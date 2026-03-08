package io.gsp26se16.moni.ai.speaking.ws;

import java.io.IOException;
import java.util.Base64;
import java.util.Map;

import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.gsp26se16.moni.ai.speaking.model.ActiveSpeakingSession;
import io.gsp26se16.moni.ai.speaking.service.AudioStreamService;
import io.gsp26se16.moni.ai.speaking.session.SessionManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * WebSocket handler for the IELTS Speaking pipeline.
 *
 * <p>Responsibilities (strictly):
 * <ul>
 *   <li>Handle WebSocket connection / disconnection lifecycle.</li>
 *   <li>Receive audio chunks and forward them to {@link AudioStreamService}.</li>
 *   <li>Send transcript and evaluation results back to the client.</li>
 * </ul>
 *
 * <p><strong>Rule:</strong> This class contains NO business logic.
 * It only does: receive → forward → respond.
 *
 * <p>Expected incoming message format:
 * <pre>
 * { "type": "start",  "question": "Describe a place you visited." }
 * { "type": "audio",  "sessionId": "...", "chunk": "<base64_pcm>" }
 * { "type": "stop",   "sessionId": "..." }
 * </pre>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SpeakingSocketHandler extends TextWebSocketHandler {

    private final SessionManager sessionManager;
    private final AudioStreamService audioStreamService;
    private final ObjectMapper objectMapper;

    // ─────────────────────────── Connection lifecycle ─────────────────────────

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        String userId = (String) session.getAttributes().get("userId");
        log.info("WebSocket connected — sessionId: {}, userId: {}", session.getId(), userId);
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        // Best-effort cleanup: close any active speaking session tied to this WS session
        String speakingSessionId = (String) session.getAttributes().get("speakingSessionId");
        if (speakingSessionId != null && sessionManager.sessionExists(speakingSessionId)) {
            sessionManager.closeSession(speakingSessionId);
        }
        log.info("WebSocket disconnected — sessionId: {}, status: {}", session.getId(), status);
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) {
        log.error("WebSocket transport error for session {}: {}", session.getId(), exception.getMessage());
    }

    // ─────────────────────────── Message handling ─────────────────────────────

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        try {
            Map<?, ?> payload = objectMapper.readValue(message.getPayload(), Map.class);
            String type = (String) payload.get("type");

            switch (type) {
                case "start" -> handleStart(session, payload);
                case "audio" -> handleAudio(session, payload);
                case "stop"  -> handleStop(session, payload);
                default      -> sendError(session, "Unknown message type: " + type);
            }
        } catch (Exception e) {
            log.error("Error handling WebSocket message: {}", e.getMessage(), e);
            sendError(session, "Failed to process message: " + e.getMessage());
        }
    }

    // ─────────────────────────── Message types ────────────────────────────────

    /**
     * "start" — create a new speaking session and bind it to this WS connection.
     */
    private void handleStart(WebSocketSession wsSession, Map<?, ?> payload) throws IOException {
        String userId = (String) wsSession.getAttributes().get("userId");
        String question = (String) payload.get("question");

        if (question == null || question.isBlank()) {
            sendError(wsSession, "Missing required field: question");
            return;
        }

        ActiveSpeakingSession speakingSession = sessionManager.createSession(userId, question);
        wsSession.getAttributes().put("speakingSessionId", speakingSession.getSessionId());

        sendMessage(wsSession, Map.of(
                "type", "started",
                "sessionId", speakingSession.getSessionId()));
    }

    /**
     * "audio" — receive a base64-encoded PCM chunk and forward to AudioStreamService.
     * Any partial transcript returned is forwarded immediately to the client.
     */
    private void handleAudio(WebSocketSession wsSession, Map<?, ?> payload) throws IOException {
        String sessionId = (String) payload.get("sessionId");
        String chunkBase64 = (String) payload.get("chunk");

        if (sessionId == null || chunkBase64 == null) {
            sendError(wsSession, "Missing sessionId or chunk");
            return;
        }

        byte[] audioChunk = Base64.getDecoder().decode(chunkBase64);

        // Forward to AudioStreamService — if a transcript is produced, send it back
        String transcript = audioStreamService.onAudioChunk(sessionId, audioChunk);
        if (transcript != null) {
            sendMessage(wsSession, Map.of(
                    "type", "transcript",
                    "text", transcript));
        }
    }

    /**
     * "stop" — signal end of speaking; close the session and send final evaluation.
     */
    private void handleStop(WebSocketSession wsSession, Map<?, ?> payload) throws IOException {
        String sessionId = (String) payload.get("sessionId");

        if (sessionId == null) {
            sendError(wsSession, "Missing sessionId");
            return;
        }

        // Drain any remaining audio and get final evaluation
        Map<String, Object> evaluation = audioStreamService.finalise(sessionId);
        sendMessage(wsSession, evaluation);

        sessionManager.closeSession(sessionId);
    }

    // ─────────────────────────── Response helpers ─────────────────────────────

    public void sendMessage(WebSocketSession session, Map<String, Object> payload) {
        if (!session.isOpen()) return;
        try {
            session.sendMessage(new TextMessage(objectMapper.writeValueAsString(payload)));
        } catch (IOException e) {
            log.error("Failed to send WebSocket message: {}", e.getMessage());
        }
    }

    private void sendError(WebSocketSession session, String message) {
        sendMessage(session, Map.of("type", "error", "message", message));
    }
}
