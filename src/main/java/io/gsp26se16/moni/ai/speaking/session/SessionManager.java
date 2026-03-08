package io.gsp26se16.moni.ai.speaking.session;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Component;

import io.gsp26se16.moni.authentication.entity.Users;
import io.gsp26se16.moni.authentication.repository.UsersRepository;
import io.gsp26se16.moni.ai.speaking.entity.SpeakingSession;
import io.gsp26se16.moni.ai.speaking.model.ActiveSpeakingSession;
import io.gsp26se16.moni.ai.speaking.repository.SpeakingSessionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Manages the lifecycle of active speaking sessions.
 *
 * <p>Responsibilities:
 * <ul>
 *   <li>Create and persist a {@link SpeakingSession} to the database.</li>
 *   <li>Hold the live {@link ActiveSpeakingSession} in memory (audio buffer, transcripts, state).</li>
 *   <li>Provide access to the in-memory session for downstream services.</li>
 *   <li>Close and clean up sessions when they end.</li>
 * </ul>
 *
 * <p>All session state lives here. Services must remain stateless.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SessionManager {

    private final SpeakingSessionRepository sessionRepository;
    private final UsersRepository usersRepository;

    /** In-memory map: sessionId → active session state */
    private final Map<String, ActiveSpeakingSession> activeSessions = new ConcurrentHashMap<>();

    // ─────────────────────────────── Create ──────────────────────────────────

    /**
     * Creates a new speaking session for the given user and question.
     * Persists the session entity and registers it in memory.
     *
     * @param userId   the authenticated user's ID
     * @param question the IELTS speaking question
     * @return the newly created {@link ActiveSpeakingSession}
     */
    public ActiveSpeakingSession createSession(String userId, String question) {
        Users user = usersRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + userId));

        SpeakingSession entity = SpeakingSession.builder()
                .user(user)
                .question(question)
                .status("ACTIVE")
                .startTime(LocalDateTime.now())
                .build();

        SpeakingSession saved = sessionRepository.save(entity);

        ActiveSpeakingSession session = ActiveSpeakingSession.builder()
                .sessionId(saved.getId())
                .userId(userId)
                .currentQuestion(question)
                .build();

        activeSessions.put(saved.getId(), session);
        log.info("Speaking session created: {} for user: {}", saved.getId(), userId);
        return session;
    }

    // ─────────────────────────────── Access ──────────────────────────────────

    /**
     * Retrieves the active in-memory session by ID.
     *
     * @throws IllegalStateException if the session is not found or already closed
     */
    public ActiveSpeakingSession getSession(String sessionId) {
        ActiveSpeakingSession session = activeSessions.get(sessionId);
        if (session == null) {
            throw new IllegalStateException("No active session found: " + sessionId);
        }
        return session;
    }

    public boolean sessionExists(String sessionId) {
        return activeSessions.containsKey(sessionId);
    }

    // ─────────────────────────────── Close ───────────────────────────────────

    /**
     * Closes a speaking session: updates the DB record and removes from memory.
     *
     * @param sessionId the session to close
     */
    public void closeSession(String sessionId) {
        activeSessions.remove(sessionId);

        sessionRepository.findById(sessionId).ifPresent(entity -> {
            entity.setStatus("COMPLETED");
            entity.setEndTime(LocalDateTime.now());
            sessionRepository.save(entity);
        });

        log.info("Speaking session closed: {}", sessionId);
    }
}
