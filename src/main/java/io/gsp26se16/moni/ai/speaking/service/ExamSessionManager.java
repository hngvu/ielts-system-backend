package io.gsp26se16.moni.ai.speaking.service;

import java.time.LocalDateTime;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketSession;

import io.gsp26se16.moni.ai.speaking.entity.SpeakingSession;
import io.gsp26se16.moni.ai.speaking.model.ActiveExamSession;
import io.gsp26se16.moni.ai.speaking.repository.SpeakingSessionRepository;
import io.gsp26se16.moni.authentication.entity.Users;
import io.gsp26se16.moni.authentication.repository.UsersRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class ExamSessionManager {

    private final ConcurrentHashMap<String, ActiveExamSession> sessions = new ConcurrentHashMap<>();
    private final SpeakingSessionRepository speakingSessionRepository;
    private final UsersRepository usersRepository;

    /**
     * Tạo ActiveExamSession in-memory VÀ persist SpeakingSession vào DB.
     */
    public ActiveExamSession create(String userId, Integer testId, WebSocketSession wsSession) {
        ActiveExamSession session = new ActiveExamSession(wsSession.getId(), userId, testId, wsSession);

        // Persist to DB
        try {
            Users user = usersRepository.findById(userId).orElse(null);
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
                log.warn("User {} not found, SpeakingSession not persisted", userId);
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
}
