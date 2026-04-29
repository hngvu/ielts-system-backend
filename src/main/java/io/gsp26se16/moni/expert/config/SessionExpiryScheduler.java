package io.gsp26se16.moni.expert.config;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import io.gsp26se16.moni.ai.writing.repository.WritingSubmissionRepository;
import io.gsp26se16.moni.authentication.entity.UserCredentials;
import io.gsp26se16.moni.authentication.repository.UserCredentialsRepository;
import io.gsp26se16.moni.expert.entity.ScoringSession;
import io.gsp26se16.moni.expert.enumeration.SessionStatus;
import io.gsp26se16.moni.expert.repository.ScoringSessionRepository;
import io.gsp26se16.moni.payment.service.CreditService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Tự cancel phiên expert bị "stuck" + hoàn credit cho user.
 *
 * Quy tắc (khác nhau giữa Writing và Speaking):
 *  - SPEAKING QUEUED > 60 phút: expert không kịp nhận → huỷ + refund.
 *  - SPEAKING IN_PROGRESS > 35 phút: video room Daily.co đã expire → huỷ + refund.
 *  - WRITING QUEUED > 7 ngày: expert chưa nhận quá lâu → huỷ + refund.
 *  - WRITING IN_PROGRESS: KHÔNG tự cancel — expert có thể đọc và chấm kỹ trong nhiều giờ.
 *
 * Mọi đường cancel tự động đều refund credit và log REFUND transaction để
 * user nhìn thấy trong /transactions.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SessionExpiryScheduler {

    private final ScoringSessionRepository sessionRepository;
    private final CreditService creditService;
    private final UserCredentialsRepository userCredentialsRepository;
    private final WritingSubmissionRepository writingSubmissionRepository;

    @Scheduled(fixedRate = 300_000) // every 5 minutes
    @Transactional
    public void expireStuckSessions() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime speakingQueuedCutoff = now.minusMinutes(60);
        LocalDateTime speakingInProgressCutoff = now.minusMinutes(35);
        LocalDateTime writingQueuedCutoff = now.minusDays(7);

        List<ScoringSession> stuckQueued =
                sessionRepository.findByStatusAndCreatedAtBefore(SessionStatus.QUEUED, Timestamp.valueOf(now));
        List<ScoringSession> stuckInProgress =
                sessionRepository.findByStatusAndCreatedAtBefore(SessionStatus.IN_PROGRESS, Timestamp.valueOf(now));

        int cancelled = 0;

        for (ScoringSession s : stuckQueued) {
            boolean isSpeaking = "SPEAKING".equalsIgnoreCase(s.getSkill());
            LocalDateTime cutoff = isSpeaking ? speakingQueuedCutoff : writingQueuedCutoff;

            if (s.getCreatedAt() == null || s.getCreatedAt().toLocalDateTime().isAfter(cutoff)) continue;

            cancelAndRefund(s, now);
            cancelled++;
        }

        for (ScoringSession s : stuckInProgress) {
            boolean isSpeaking = "SPEAKING".equalsIgnoreCase(s.getSkill());
            // Writing IN_PROGRESS: bỏ qua — để expert chấm xong
            if (!isSpeaking) continue;

            // BUG fix: dùng startedAt (lúc expert accept) thay vì createdAt (lúc book).
            // Trước đây session ngồi trong queue lâu rồi expert mới accept thì
            // ngay sau accept đã bị scheduler huỷ vì createdAt > 35 phút,
            // dù call mới bắt đầu — gây refund kèm complete-after-cancel.
            LocalDateTime referenceTime = s.getStartedAt() != null
                    ? s.getStartedAt()
                    : (s.getCreatedAt() != null ? s.getCreatedAt().toLocalDateTime() : null);
            if (referenceTime == null || referenceTime.isAfter(speakingInProgressCutoff)) continue;

            cancelAndRefund(s, now);
            cancelled++;
        }

        if (cancelled > 0) {
            log.info("Session expiry check: expired and refunded {} session(s)", cancelled);
        }
    }

    /** Cancel session + refund credit cho user đã book. */
    private void cancelAndRefund(ScoringSession session, LocalDateTime now) {
        session.setStatus(SessionStatus.CANCELLED);
        session.setEndedAt(now);
        sessionRepository.save(session);

        // Reset linked WritingSubmission về PENDING để learner không thấy "Đang chấm..."
        // sau khi session đã auto-cancel. Dùng FQ name để tránh spotless xóa import.
        if (session.getWritingSubmissionId() != null) {
            writingSubmissionRepository
                    .findById(session.getWritingSubmissionId())
                    .ifPresent(sub -> {
                        sub.setEvaluationStatus(io.gsp26se16.moni.common.enumeration.EvaluationStatus.PENDING);
                        writingSubmissionRepository.save(sub);
                    });
        }

        if (session.getUser() == null) {
            log.warn("Session #{} has no user, skip refund", session.getId());
            return;
        }

        // Tìm credential từ userId để gọi refund (CreditService yêu cầu credentialId)
        UserCredentials cred = userCredentialsRepository
                .findByUser_Id(session.getUser().getId())
                .orElse(null);
        if (cred == null) {
            log.warn(
                    "Cannot find credential for user {} — skip refund for session #{}",
                    session.getUser().getId(),
                    session.getId());
            return;
        }

        String serviceCode =
                "SPEAKING".equalsIgnoreCase(session.getSkill()) ? "EXPERT_SPEAKING_SCORE" : "EXPERT_WRITING_SCORE";
        try {
            creditService.refund(cred.getId(), serviceCode);
            log.info(
                    "Auto-cancelled session #{} ({}) and refunded {}",
                    session.getId(),
                    session.getSkill(),
                    serviceCode);
        } catch (Exception e) {
            log.error("Refund failed for session #{}: {}", session.getId(), e.getMessage());
        }
    }
}
