package io.gsp26se16.moni.ai.speaking.service;

import java.time.LocalDateTime;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketSession;

import io.gsp26se16.moni.ai.speaking.entity.SpeakingSession;
import io.gsp26se16.moni.ai.speaking.model.ActiveExamSession;
import io.gsp26se16.moni.ai.speaking.repository.SpeakingSessionRepository;
import io.gsp26se16.moni.authentication.entity.UserCredentials;
import io.gsp26se16.moni.authentication.entity.Users;
import io.gsp26se16.moni.authentication.repository.UserCredentialsRepository;
import io.gsp26se16.moni.authentication.repository.UsersRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class ExamSessionManager {

    private final ConcurrentHashMap<String, ActiveExamSession> sessions = new ConcurrentHashMap<>();
    private final SpeakingSessionRepository speakingSessionRepository;
    private final UserCredentialsRepository userCredentialsRepository;
    private final UsersRepository usersRepository;

    /**
     * Tạo ActiveExamSession in-memory VÀ persist SpeakingSession vào DB.
     */
    public ActiveExamSession create(String userId, Integer testId, WebSocketSession wsSession) {
        ActiveExamSession session = new ActiveExamSession(wsSession.getId(), userId, testId, wsSession);

        // Persist to DB
        try {
            // userId from JWT is credential ID, resolve to Users entity
            Users user = null;
            UserCredentials cred = userCredentialsRepository.findById(userId).orElse(null);
            if (cred != null) {
                user = cred.getUser();
            }
            if (user == null) {
                user = usersRepository.findById(userId).orElse(null);
            }

            if (user != null) {
                SpeakingSession dbSession = SpeakingSession.builder()
                        .user(user)
                        .question("Speaking Test #" + testId)
                        .status("ACTIVE")
                        .startTime(LocalDateTime.now())
                        .build();
                SpeakingSession saved = speakingSessionRepository.save(dbSession);
                session.setSpeakingSessionId(saved.getId());
                log.info("Persisted SpeakingSession id={} for user={}", saved.getId(), userId);
            } else {
                log.warn("User {} not found via credentials or direct lookup, SpeakingSession not persisted", userId);
            }
        } catch (Exception e) {
            log.error("Failed to persist SpeakingSession: {}", e.getMessage());
        }

        sessions.put(wsSession.getId(), session);
        return session;
    }

    public ActiveExamSession get(String sessionId) {
        return sessions.get(sessionId);
    }

    public void remove(String sessionId) {
        sessions.remove(sessionId);
    }

    public Iterable<ActiveExamSession> getAllSessions() {
        return sessions.values();
    }

    /**
     * Gửi heartbeat (Mutual keepalive) đến tất cả các session đang mở mỗi 15 giây.
     * Giáp FE keep Load Balancer không bị timeout.
     */
    @org.springframework.scheduling.annotation.Scheduled(fixedDelay = 15000)
    public void broadcastHeartbeat() {
        if (sessions.isEmpty()) return;

        String heartbeatMessage = "{\"type\":\"heartbeat\"}";
        org.springframework.web.socket.TextMessage tm =
                new org.springframework.web.socket.TextMessage(heartbeatMessage);

        for (ActiveExamSession session : sessions.values()) {
            if (session.isOpen()) {
                try {
                    session.getWsSession().sendMessage(tm);
                } catch (java.io.IOException e) {
                    log.error("Failed to send heartbeat to session {}: {}", session.getSessionId(), e.getMessage());
                }
            }
        }
    }
}
