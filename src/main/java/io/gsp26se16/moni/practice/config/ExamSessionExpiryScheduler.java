package io.gsp26se16.moni.practice.config;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import io.gsp26se16.moni.common.enumeration.TestSessionStatus;
import io.gsp26se16.moni.practice.entity.TestSession;
import io.gsp26se16.moni.practice.repository.TestSessionRepository;
import io.gsp26se16.moni.practice.service.ExamSessionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class ExamSessionExpiryScheduler {

    private final TestSessionRepository testSessionRepository;
    private final ExamSessionService examSessionService;

    @Scheduled(fixedRate = 60_000) // every 60 seconds
    @Transactional
    public void expireOverdueSessions() {
        List<TestSession> activeSessions = testSessionRepository.findAllByStatus(TestSessionStatus.IN_PROGRESS);
        LocalDateTime now = LocalDateTime.now();

        for (TestSession session : activeSessions) {
            if (session.getDurationSeconds() == null) continue;

            long elapsed = ChronoUnit.SECONDS.between(session.getStartedAt(), now);
            if (elapsed >= session.getDurationSeconds()) {
                try {
                    examSessionService.expireSession(session);
                } catch (Exception e) {
                    log.error("Failed to expire session {}", session.getId(), e);
                }
            }
        }
    }
}
