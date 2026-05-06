package io.gsp26se16.moni.ai.monitoring.scheduler;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import io.gsp26se16.moni.ai.speaking.repository.SpeakingSubmissionRepository;
import io.gsp26se16.moni.ai.writing.repository.WritingSubmissionRepository;
import io.gsp26se16.moni.authentication.entity.UserCredentials;
import io.gsp26se16.moni.authentication.repository.UserCredentialsRepository;
import io.gsp26se16.moni.common.enumeration.EvaluationStatus;
import io.gsp26se16.moni.notification.enumeration.NotificationType;
import io.gsp26se16.moni.notification.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Component
@RequiredArgsConstructor
@Slf4j
public class AiHealthAlertScheduler {

    private static final ZoneId ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

    private final WritingSubmissionRepository writingSubmissionRepository;
    private final SpeakingSubmissionRepository speakingSubmissionRepository;
    private final NotificationService notificationService;
    private final UserCredentialsRepository userCredentialsRepository;

    @Value("${app.ai-health.failed-threshold:5}")
    private int failedThreshold;

    private volatile LocalDate lastAlertDate = null;

    @Scheduled(fixedRate = 300_000) // 5 phút
    public void checkFailedThreshold() {
        LocalDate today = LocalDate.now(ZONE);
        LocalDateTime todayStart = today.atStartOfDay();
        LocalDateTime todayEnd = today.plusDays(1).atStartOfDay();

        long failedWriting = writingSubmissionRepository.countByEvaluationStatusAndSubmittedAtBetween(
                EvaluationStatus.FAILED, todayStart, todayEnd);
        long failedSpeaking = speakingSubmissionRepository.countByEvaluationStatusAndSubmittedAtBetween(
                EvaluationStatus.FAILED, todayStart, todayEnd);
        long totalFailed = failedWriting + failedSpeaking;

        if (totalFailed >= failedThreshold && !today.equals(lastAlertDate)) {
            lastAlertDate = today;
            sendAlertToAdmins(totalFailed);
        }
    }

    private void sendAlertToAdmins(long failedCount) {
        List<UserCredentials> admins = userCredentialsRepository.findByRole(UserCredentials.Role.ADMIN);

        for (UserCredentials admin : admins) {
            String userId = admin.getUser().getId();
            notificationService.create(
                    userId,
                    NotificationType.AI_HEALTH_ALERT,
                    "AI Health: " + failedCount + " bài FAILED hôm nay",
                    "Số bài AI đánh giá thất bại hôm nay đã vượt ngưỡng " + failedThreshold
                            + ". Vui lòng kiểm tra hệ thống.",
                    "/admin",
                    null);
        }
        log.warn("AI Health Alert: {} FAILED evaluations today, threshold={}", failedCount, failedThreshold);
    }
}
