package io.gsp26se16.moni.expert.config;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import io.gsp26se16.moni.expert.entity.ScoringSession;
import io.gsp26se16.moni.expert.enumeration.SessionStatus;
import io.gsp26se16.moni.expert.repository.ScoringSessionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class SessionExpiryScheduler {

    private final ScoringSessionRepository sessionRepository;

    @Scheduled(fixedRate = 300_000) // every 5 minutes
    @Transactional
    public void expireStuckSessions() {
        LocalDateTime inProgressCutoff = LocalDateTime.now().minusMinutes(35);
        LocalDateTime queuedCutoff = LocalDateTime.now().minusMinutes(60);

        // Find IN_PROGRESS sessions older than 35 min
        List<ScoringSession> stuckInProgress = sessionRepository.findByStatusAndCreatedAtBefore(
                SessionStatus.IN_PROGRESS, Timestamp.valueOf(inProgressCutoff));

        // Find QUEUED sessions older than 60 min
        List<ScoringSession> stuckQueued =
                sessionRepository.findByStatusAndCreatedAtBefore(SessionStatus.QUEUED, Timestamp.valueOf(queuedCutoff));

        for (ScoringSession s : stuckInProgress) {
            s.setStatus(SessionStatus.CANCELLED);
            s.setEndedAt(LocalDateTime.now());
            sessionRepository.save(s);
            log.info("Auto-expired IN_PROGRESS session #{}", s.getId());
        }

        for (ScoringSession s : stuckQueued) {
            s.setStatus(SessionStatus.CANCELLED);
            sessionRepository.save(s);
            log.info("Auto-expired QUEUED session #{}", s.getId());
        }

        int total = stuckInProgress.size() + stuckQueued.size();
        if (total > 0) {
            log.info("Session expiry check: expired {} sessions", total);
        }
    }
}
