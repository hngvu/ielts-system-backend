package io.gsp26se16.moni.ai.speaking.ws;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.gsp26se16.moni.ai.speaking.model.ActiveExamSession;
import io.gsp26se16.moni.ai.speaking.service.ConversationEngine;
import io.gsp26se16.moni.ai.speaking.service.ExamSessionManager;
import io.gsp26se16.moni.ai.speaking.service.ExaminerService;
import io.gsp26se16.moni.common.enumeration.ExamState;
import io.gsp26se16.moni.common.exception.AppException;
import io.gsp26se16.moni.payment.service.CreditService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * WebSocket handler cho buổi thi Speaking với examiner AI + ElevenLabs TTS.
 * Endpoint: /ws/speaking/exam?token=<JWT>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SpeakingExamHandler extends TextWebSocketHandler {

    private final ExamSessionManager sessionManager;
    private final ExaminerService examinerService;
    private final ConversationEngine conversationEngine;
    private final CreditService creditService;
    private final ObjectMapper objectMapper;

    private final ExecutorService evalExecutor = Executors.newCachedThreadPool();

    // ─────────────────────────────── Connection ──────────────────────────────

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        String userId = (String) session.getAttributes().get("userId");
        log.info("SpeakingExam WS connected: sessionId={}, userId={}", session.getId(), userId);
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        sessionManager.remove(session.getId());
        log.info("SpeakingExam WS closed: sessionId={}, status={}", session.getId(), status);
    }

    // ─────────────────────────────── Messages ────────────────────────────────

    @Override
    protected void handleTextMessage(WebSocketSession ws, TextMessage message) {
        try {
            Map<String, Object> payload = objectMapper.readValue(message.getPayload(), new TypeReference<>() {});
            String type = (String) payload.get("type");

            switch (type) {
                case "start_exam" -> handleStartExam(ws, payload);
                case "transcript" -> handleTranscript(ws, payload);
                case "start_speaking_part2" -> handleStartPart2(ws);
                case "stop_speaking_part2" -> handleStopPart2(ws, payload);
                case "end_exam" -> handleEndExam(ws);
                case "ping", "ack" -> {
                    /* ping from old logic, ack from new mutual heartbeat */
                }
                default -> log.warn("Unknown message type: {}", type);
            }
        } catch (Exception e) {
            log.error("Error handling WS message: {}", e.getMessage(), e);
            sendError(ws, "Internal error: " + e.getMessage());
        }
    }

    // ─────────────────────────────── Handlers ────────────────────────────────

    private void handleStartExam(WebSocketSession ws, Map<String, Object> payload) throws IOException {
        String userId = (String) ws.getAttributes().get("userId");
        Integer testId = (Integer) payload.get("testId");

        if (testId == null) {
            sendError(ws, "testId is required");
            return;
        }

        // Check and deduct credit before starting exam
        try {
            creditService.checkAndDeduct(userId, "AI_SPEAKING_SCORE");
        } catch (AppException e) {
            sendError(ws, "Không đủ credit để bắt đầu thi Speaking");
            return;
        }

        ActiveExamSession session = sessionManager.create(userId, testId, ws);
        examinerService.loadExam(session);
        examinerService.startPart1(session);

        log.info("Exam started: sessionId={}, testId={}, userId={}", session.getSessionId(), testId, userId);
    }

    private void handleTranscript(WebSocketSession ws, Map<String, Object> payload) throws IOException {
        ActiveExamSession session = sessionManager.get(ws.getId());
        if (session == null) {
            sendError(ws, "No active exam session");
            return;
        }

        Integer questionId = (Integer) payload.get("questionId");
        String text = (String) payload.getOrDefault("text", "[no response]");
        if (text.isBlank()) text = "[no response]";
        String audioUrl = (String) payload.getOrDefault("audioUrl", "");

        examinerService.handleTranscript(session, questionId, text, audioUrl);
    }

    private void handleStartPart2(WebSocketSession ws) {
        ActiveExamSession session = sessionManager.get(ws.getId());
        if (session == null) {
            sendError(ws, "No active exam session");
            return;
        }
        if (session.getState() != ExamState.PART2_PREPARATION) {
            sendError(ws, "Not in Part 2 preparation state");
            return;
        }
        examinerService.startPart2Speaking(session);
    }

    private void handleStopPart2(WebSocketSession ws, Map<String, Object> payload) throws IOException {
        ActiveExamSession session = sessionManager.get(ws.getId());
        if (session == null) {
            sendError(ws, "No active exam session");
            return;
        }

        String transcript = (String) payload.getOrDefault("text", "");
        String audioUrl = (String) payload.getOrDefault("audioUrl", "");
        examinerService.stopPart2Speaking(session, transcript, audioUrl);
    }

    private void handleEndExam(WebSocketSession ws) {
        ActiveExamSession session = sessionManager.get(ws.getId());
        if (session == null) {
            sendError(ws, "No active exam session");
            return;
        }

        runEvaluation(session);
    }

    // ─────────────────────────────── Evaluation ──────────────────────────────

    /**
     * Chạy ConversationEngine trong virtual thread để không block WS thread.
     * Sau khi xong gửi kết quả về client và dọn session.
     */
    private void runEvaluation(ActiveExamSession session) {
        CompletableFuture.runAsync(
                () -> {
                    try {
                        session.setState(ExamState.EVALUATING);
                        Map<String, Object> result = conversationEngine.evaluateFromExam(
                                session.getSessionId(),
                                session.getUserId(),
                                session.getFullTranscript(),
                                session.getAudioUrls());

                        if (session.isOpen()) {
                            session.getWsSession()
                                    .sendMessage(new TextMessage(objectMapper.writeValueAsString(result)));
                        }

                        session.setState(ExamState.COMPLETED);
                        sessionManager.remove(session.getSessionId());

                    } catch (Exception e) {
                        log.error("Evaluation failed for session {}: {}", session.getSessionId(), e.getMessage(), e);
                        sendError(session.getWsSession(), "Evaluation failed: " + e.getMessage());
                    }
                },
                evalExecutor);
    }

    // ─────────────────────────────── Utilities ───────────────────────────────

    private void sendError(WebSocketSession ws, String message) {
        try {
            if (ws.isOpen()) {
                ws.sendMessage(
                        new TextMessage(objectMapper.writeValueAsString(Map.of("type", "error", "message", message))));
            }
        } catch (IOException e) {
            log.error("Failed to send error: {}", e.getMessage());
        }
    }
}
