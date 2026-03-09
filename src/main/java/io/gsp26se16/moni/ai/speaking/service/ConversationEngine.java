package io.gsp26se16.moni.ai.speaking.service;

import java.time.LocalDateTime;
import java.util.Map;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.SimpleLoggerAdvisor;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.gsp26se16.moni.ai.speaking.entity.SpeakingSession;
import io.gsp26se16.moni.ai.speaking.entity.SpeakingSubmission;
import io.gsp26se16.moni.ai.speaking.model.ActiveSpeakingSession;
import io.gsp26se16.moni.ai.speaking.repository.SpeakingSessionRepository;
import io.gsp26se16.moni.ai.speaking.repository.SpeakingSubmissionRepository;
import io.gsp26se16.moni.ai.speaking.session.SessionManager;
import io.gsp26se16.moni.ai.writing.entity.AiEvaluation;
import io.gsp26se16.moni.ai.writing.repository.AiEvaluationRepository;
import io.gsp26se16.moni.ai.writing.service.Helper;
import io.gsp26se16.moni.ai.writing.service.PromptLoader;
import io.gsp26se16.moni.authentication.entity.Users;
import io.gsp26se16.moni.authentication.repository.UsersRepository;
import io.gsp26se16.moni.common.enumeration.EvaluationStatus;
import io.gsp26se16.moni.common.enumeration.Skill;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Central AI orchestrator cho Speaking pipeline.
 *
 * Flow:
 *   1. Lấy transcript từ session
 *   2. Tạo SpeakingSubmission (PROCESSING)
 *   3. Chấm điểm 4 tiêu chí: FC, LR, GRA, PR
 *   4. SpeakingRuleEngine điều chỉnh band
 *   5. Tạo feedback
 *   6. Lưu AiEvaluation + cập nhật SpeakingSubmission → COMPLETED
 *   7. Trả kết quả về WebSocket
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ConversationEngine {

    private final SessionManager sessionManager;
    private final SpeakingRuleEngine speakingRuleEngine;
    private final AiEvaluationRepository aiEvaluationRepository;
    private final SpeakingSubmissionRepository speakingSubmissionRepository;
    private final SpeakingSessionRepository speakingSessionRepository;
    private final UsersRepository usersRepository;
    private final PromptLoader promptLoader;
    private final Helper helper;
    private final ObjectMapper objectMapper;
    private final ChatClient.Builder chatClientBuilder;

    // ─────────────────────────────── Public API ───────────────────────────────

    /**
     * Đánh giá toàn bộ transcript đã tích lũy trong session.
     *
     * @param sessionId the active speaking session ID
     * @return kết quả đánh giá gửi về WebSocket
     */
    public Map<String, Object> evaluate(String sessionId) {
        ActiveSpeakingSession active = sessionManager.getSession(sessionId);
        String transcript = active.getFullTranscript();
        String question = active.getCurrentQuestion();

        if (transcript.isBlank()) {
            log.warn("Transcript rỗng cho session {}, trả về kết quả mặc định", sessionId);
            return defaultResult();
        }

        log.info("Bắt đầu đánh giá session {} — độ dài transcript: {}", sessionId, transcript.length());

        // ── Tạo SpeakingSubmission (trạng thái PROCESSING) ───────────────────
        SpeakingSubmission submission = createSubmission(active, transcript);

        try {
            ChatClient chatClient =
                    chatClientBuilder.defaultAdvisors(new SimpleLoggerAdvisor()).build();

            // ── Chấm điểm 4 tiêu chí ─────────────────────────────────────────
            Map<String, Object> fc = evaluateCriterion(chatClient, "FC", transcript, question);
            Map<String, Object> lr = evaluateCriterion(chatClient, "LR", transcript, question);
            Map<String, Object> gra = evaluateCriterion(chatClient, "GRA", transcript, question);
            Map<String, Object> pr = evaluateCriterion(chatClient, "PR", transcript, question);

            // ── Rule Engine ───────────────────────────────────────────────────
            Map<String, Object> assessment = speakingRuleEngine.calculateBands(fc, lr, gra, pr);

            // ── Feedback ──────────────────────────────────────────────────────
            Map<String, Object> feedback = generateFeedback(chatClient, transcript, assessment);

            // ── Lưu AiEvaluation + cập nhật submission → COMPLETED ────────────
            double finalBand = (double) assessment.get("final_band");
            persistEvaluation(submission, transcript, assessment, feedback, finalBand);

            // ── Build response ─────────────────────────────────────────────────
            Map<String, Object> criteriaMap = (Map<String, Object>) assessment.get("criteria");
            return Map.of(
                    "type",
                    "evaluation",
                    "final_band",
                    finalBand,
                    "fluency",
                    getBandFromCriterion(criteriaMap, "FC"),
                    "vocabulary",
                    getBandFromCriterion(criteriaMap, "LR"),
                    "grammar",
                    getBandFromCriterion(criteriaMap, "GRA"),
                    "pronunciation",
                    getBandFromCriterion(criteriaMap, "PR"),
                    "feedback",
                    feedback,
                    "transcript",
                    transcript);

        } catch (Exception e) {
            log.error("Đánh giá thất bại cho session {}: {}", sessionId, e.getMessage(), e);
            submission.setEvaluationStatus(EvaluationStatus.FAILED);
            speakingSubmissionRepository.save(submission);
            return defaultResult();
        }
    }

    // ─────────────────────────────── Tạo submission ──────────────────────────

    private SpeakingSubmission createSubmission(ActiveSpeakingSession active, String transcript) {
        Users user = usersRepository.findById(active.getUserId()).orElse(null);

        SpeakingSession speakingSession =
                speakingSessionRepository.findById(active.getSessionId()).orElse(null);

        SpeakingSubmission submission = SpeakingSubmission.builder()
                .user(user)
                .speakingSession(speakingSession)
                .audioTranscript(transcript)
                .evaluationStatus(EvaluationStatus.PROCESSING)
                .build();

        return speakingSubmissionRepository.save(submission);
    }

    // ─────────────────────────────── Phases ──────────────────────────────────

    private Map<String, Object> evaluateCriterion(
            ChatClient chatClient, String criterion, String transcript, String question) {

        String prompt = promptLoader.loadPromptWithSpeakingRubric(
                "speaking_eval.txt",
                criterion,
                Map.of(
                        "criterion", criterion,
                        "question", question,
                        "transcript", transcript));

        String response = chatClient
                .prompt()
                .system(prompt)
                .user("Evaluate strictly using the rubric. Return ONLY raw JSON.")
                .call()
                .content();

        Map<String, Object> result = helper.parseJson(response);
        return helper.withCriterion(result, criterion);
    }

    private Map<String, Object> generateFeedback(
            ChatClient chatClient, String transcript, Map<String, Object> assessment) {
        try {
            String prompt = promptLoader.loadPrompt(
                    "speaking_feedback.txt",
                    Map.of("transcript", transcript, "assessment", objectMapper.writeValueAsString(assessment)));

            String response = chatClient
                    .prompt()
                    .system(prompt)
                    .user("Provide feedback strictly from the assessment. Return ONLY raw JSON.")
                    .call()
                    .content();

            return helper.parseJson(response);
        } catch (Exception e) {
            log.error("Sinh feedback thất bại: {}", e.getMessage());
            return Map.of("summary", "Feedback unavailable.");
        }
    }

    // ─────────────────────────────── Persistence ─────────────────────────────

    private void persistEvaluation(
            SpeakingSubmission submission,
            String transcript,
            Map<String, Object> assessment,
            Map<String, Object> feedback,
            double finalBand) {

        // Lưu AiEvaluation
        AiEvaluation evaluation = AiEvaluation.builder()
                .submissionId(submission.getId())
                .skill(Skill.SPEAKING)
                .overallScore(finalBand)
                .overallComment(transcript)
                .analysisResult(assessment)
                .feedbackResponse(feedback)
                .createdAt(LocalDateTime.now())
                .build();

        aiEvaluationRepository.save(evaluation);

        // Cập nhật trạng thái submission → COMPLETED
        submission.setEvaluationStatus(EvaluationStatus.COMPLETED);
        speakingSubmissionRepository.save(submission);

        log.info("Đã lưu AiEvaluation cho submission {} — band: {}", submission.getId(), finalBand);
    }

    // ─────────────────────────────── Helpers ─────────────────────────────────

    private double getBandFromCriterion(Map<String, Object> criteriaMap, String key) {
        if (criteriaMap == null) return 5.0;
        Object criterionObj = criteriaMap.get(key);
        if (criterionObj instanceof Map<?, ?> map) {
            Object adjusted = map.get("adjusted_band");
            if (adjusted instanceof Number n) return n.doubleValue();
            Object band = map.get("band");
            if (band instanceof Number n) return n.doubleValue();
        }
        return 5.0;
    }

    private Map<String, Object> defaultResult() {
        return Map.of(
                "type", "evaluation",
                "final_band", 0.0,
                "fluency", 0.0,
                "vocabulary", 0.0,
                "grammar", 0.0,
                "pronunciation", 0.0,
                "feedback", Map.of("summary", "No speech detected."),
                "transcript", "");
    }
}
