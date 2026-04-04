package io.gsp26se16.moni.ai.speaking.service;

import java.time.LocalDateTime;
import java.util.Map;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.SimpleLoggerAdvisor;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.gsp26se16.moni.ai.speaking.entity.SpeakingSubmission;
import io.gsp26se16.moni.ai.speaking.repository.SpeakingSubmissionRepository;
import io.gsp26se16.moni.ai.writing.entity.AiEvaluation;
import io.gsp26se16.moni.ai.writing.repository.AiEvaluationRepository;
import io.gsp26se16.moni.ai.writing.service.Helper;
import io.gsp26se16.moni.ai.writing.service.PromptLoader;
import io.gsp26se16.moni.authentication.entity.UserCredentials;
import io.gsp26se16.moni.authentication.entity.Users;
import io.gsp26se16.moni.authentication.repository.UserCredentialsRepository;
import io.gsp26se16.moni.authentication.repository.UsersRepository;
import io.gsp26se16.moni.common.enumeration.EvaluationStatus;
import io.gsp26se16.moni.common.enumeration.Skill;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * AI orchestrator cho Speaking exam pipeline.
 *
 * Flow:
 *   SpeakingExamHandler gọi evaluateFromExam() sau khi user hoàn thành 3 parts.
 *   1. Tạo SpeakingSubmission (PROCESSING)
 *   2. Chấm điểm 4 tiêu chí: FC, LR, GRA, PR
 *   3. SpeakingRuleEngine điều chỉnh band
 *   4. Tạo feedback
 *   5. Lưu AiEvaluation + cập nhật SpeakingSubmission → COMPLETED
 *   6. Trả kết quả về WebSocket
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ConversationEngine {

    private final SpeakingRuleEngine speakingRuleEngine;
    private final AiEvaluationRepository aiEvaluationRepository;
    private final SpeakingSubmissionRepository speakingSubmissionRepository;
    private final UsersRepository usersRepository;
    private final UserCredentialsRepository userCredentialsRepository;
    private final PromptLoader promptLoader;
    private final Helper helper;
    private final ObjectMapper objectMapper;
    private final ChatClient.Builder chatClientBuilder;

    // ─────────────────────────────── Public API ───────────────────────────────

    /**
     * Đánh giá toàn bộ transcript 3 parts từ exam pipeline.
     * Gọi bởi SpeakingExamHandler sau khi user hoàn thành cả 3 parts.
     *
     * @param examSessionId  WebSocket session ID
     * @param userId         ID của user
     * @param fullTranscript toàn bộ transcript 3 parts (từ ActiveExamSession.getFullTranscript())
     */
    public Map<String, Object> evaluateFromExam(String examSessionId, String userId, String fullTranscript) {
        if (fullTranscript == null || fullTranscript.isBlank()) {
            log.warn("Transcript rỗng cho exam session {}", examSessionId);
            return defaultResult();
        }

        log.info("Bắt đầu đánh giá exam session {} — {} chars", examSessionId, fullTranscript.length());

        SpeakingSubmission submission = createSubmission(userId, fullTranscript);

        try {
            ChatClient chatClient =
                    chatClientBuilder.defaultAdvisors(new SimpleLoggerAdvisor()).build();

            // Chấm 4 tiêu chí IELTS Speaking
            Map<String, Object> fc = phase1FC(chatClient, fullTranscript, "IELTS Speaking Test");
            Map<String, Object> lr = phase2LR(chatClient, fullTranscript, "IELTS Speaking Test");
            Map<String, Object> gra = phase3GRA(chatClient, fullTranscript, "IELTS Speaking Test");
            Map<String, Object> pr = phase4PR(chatClient, fullTranscript, "IELTS Speaking Test");

            Map<String, Object> assessment = speakingRuleEngine.calculateBands(fc, lr, gra, pr);
            Map<String, Object> feedback = phase5Feedback(chatClient, fullTranscript, assessment);

            double finalBand = (double) assessment.get("final_band");
            persistEvaluation(submission, fullTranscript, assessment, feedback, finalBand);

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
                    fullTranscript);

        } catch (Exception e) {
            log.error("Đánh giá exam thất bại cho session {}: {}", examSessionId, e.getMessage(), e);
            submission.setEvaluationStatus(EvaluationStatus.FAILED);
            speakingSubmissionRepository.save(submission);
            return defaultResult();
        }
    }

    /**
     * Đánh giá practice mode: 1 câu hỏi + 1 transcript.
     * Dùng bởi REST endpoint /ai/speaking/score.
     * Trả format FE expects: { overallScore, fluency, pronunciation, vocabulary, grammar, comments }
     */
    public Map<String, Object> evaluatePractice(String userId, String question, String transcript) {
        if (transcript == null || transcript.isBlank()) {
            log.warn("Empty transcript for practice scoring, userId={}", userId);
            return Map.of(
                    "overallScore",
                    0.0,
                    "fluency",
                    0.0,
                    "pronunciation",
                    0.0,
                    "vocabulary",
                    0.0,
                    "grammar",
                    0.0,
                    "comments",
                    "Không phát hiện giọng nói.");
        }

        log.info(
                "Practice speaking scoring for userId={}, question='{}', transcript={} chars",
                userId,
                question.substring(0, Math.min(50, question.length())),
                transcript.length());

        String formattedTranscript = "Question: " + question + "\nAnswer: " + transcript;
        SpeakingSubmission submission = createSubmission(userId, formattedTranscript);

        try {
            ChatClient chatClient =
                    chatClientBuilder.defaultAdvisors(new SimpleLoggerAdvisor()).build();

            Map<String, Object> fc = phase1FC(chatClient, formattedTranscript, question);
            Map<String, Object> lr = phase2LR(chatClient, formattedTranscript, question);
            Map<String, Object> gra = phase3GRA(chatClient, formattedTranscript, question);
            Map<String, Object> pr = phase4PR(chatClient, formattedTranscript, question);

            Map<String, Object> assessment = speakingRuleEngine.calculateBands(fc, lr, gra, pr);
            Map<String, Object> feedback = phase5Feedback(chatClient, formattedTranscript, assessment);

            double finalBand = (double) assessment.get("final_band");
            persistEvaluation(submission, formattedTranscript, assessment, feedback, finalBand);

            Map<String, Object> criteriaMap = (Map<String, Object>) assessment.get("criteria");

            // Extract comments from feedback
            String comments = extractComments(feedback);

            return Map.of(
                    "overallScore", finalBand,
                    "fluency", getBandFromCriterion(criteriaMap, "FC"),
                    "pronunciation", getBandFromCriterion(criteriaMap, "PR"),
                    "vocabulary", getBandFromCriterion(criteriaMap, "LR"),
                    "grammar", getBandFromCriterion(criteriaMap, "GRA"),
                    "comments", comments);

        } catch (Exception e) {
            log.error("Practice scoring failed for userId={}: {}", userId, e.getMessage(), e);
            submission.setEvaluationStatus(EvaluationStatus.FAILED);
            speakingSubmissionRepository.save(submission);
            throw new RuntimeException("Speaking evaluation failed: " + e.getMessage(), e);
        }
    }

    private String extractComments(Map<String, Object> feedback) {
        if (feedback == null) return "";
        Object summary = feedback.get("summary");
        if (summary != null) return summary.toString();
        Object strategy = feedback.get("overall_strategy");
        if (strategy != null) return strategy.toString();
        // Try improvements array
        Object improvements = feedback.get("improvements");
        if (improvements instanceof java.util.List<?> list && !list.isEmpty()) {
            StringBuilder sb = new StringBuilder();
            for (Object item : list) {
                if (item instanceof Map<?, ?> map) {
                    sb.append("[")
                            .append(map.get("criterion"))
                            .append("] ")
                            .append(map.get("reason"))
                            .append("\n");
                }
            }
            return sb.toString().trim();
        }
        return feedback.toString();
    }

    // ─────────────────────────────── Private ─────────────────────────────────

    private SpeakingSubmission createSubmission(String credentialId, String transcript) {
        // userId from JWT is credential ID, need to resolve to Users entity
        Users user = null;
        if (credentialId != null) {
            UserCredentials cred =
                    userCredentialsRepository.findById(credentialId).orElse(null);
            if (cred != null) user = cred.getUser();
        }
        if (user == null) {
            // Fallback: try direct lookup
            user = usersRepository.findById(credentialId).orElse(null);
        }

        SpeakingSubmission submission = SpeakingSubmission.builder()
                .user(user)
                .audioTranscript(transcript)
                .evaluationStatus(EvaluationStatus.PROCESSING)
                .build();

        return speakingSubmissionRepository.save(submission);
    }

    private Map<String, Object> phase1FC(ChatClient chatClient, String transcript, String question) {
        String prompt = promptLoader.loadPrompt(
                "speaking/phase1_fc.txt", Map.of("question", question, "transcript", transcript));
        Map<String, Object> result = callEvaluation(chatClient, prompt);
        return helper.withCriterion(result, "FC");
    }

    private Map<String, Object> phase2LR(ChatClient chatClient, String transcript, String question) {
        String prompt = promptLoader.loadPrompt(
                "speaking/phase2_lr.txt", Map.of("question", question, "transcript", transcript));
        Map<String, Object> result = callEvaluation(chatClient, prompt);
        return helper.withCriterion(result, "LR");
    }

    private Map<String, Object> phase3GRA(ChatClient chatClient, String transcript, String question) {
        String prompt = promptLoader.loadPrompt(
                "speaking/phase3_gra.txt", Map.of("question", question, "transcript", transcript));
        Map<String, Object> result = callEvaluation(chatClient, prompt);
        return helper.withCriterion(result, "GRA");
    }

    private Map<String, Object> phase4PR(ChatClient chatClient, String transcript, String question) {
        String prompt = promptLoader.loadPrompt(
                "speaking/phase4_pr.txt", Map.of("question", question, "transcript", transcript));
        Map<String, Object> result = callEvaluation(chatClient, prompt);
        return helper.withCriterion(result, "PR");
    }

    private Map<String, Object> phase5Feedback(
            ChatClient chatClient, String transcript, Map<String, Object> assessment) {
        try {
            String prompt = promptLoader.loadPrompt(
                    "speaking/phase5_feedback.txt",
                    Map.of("transcript", transcript, "assessment", objectMapper.writeValueAsString(assessment)));

            String response = chatClient
                    .prompt()
                    .system(prompt)
                    .user("Return ONLY raw JSON.")
                    .call()
                    .content();

            return helper.parseJson(response);
        } catch (Exception e) {
            log.error("Sinh feedback thất bại: {}", e.getMessage());
            return Map.of("summary", "Feedback unavailable.");
        }
    }

    private Map<String, Object> callEvaluation(ChatClient chatClient, String systemPrompt) {
        String response = chatClient
                .prompt()
                .system(systemPrompt)
                .user("Return ONLY raw JSON.")
                .call()
                .content();
        return helper.parseJson(response);
    }

    private void persistEvaluation(
            SpeakingSubmission submission,
            String transcript,
            Map<String, Object> assessment,
            Map<String, Object> feedback,
            double finalBand) {

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

        submission.setEvaluationStatus(EvaluationStatus.COMPLETED);
        speakingSubmissionRepository.save(submission);

        log.info("AiEvaluation saved: submissionId={}, band={}", submission.getId(), finalBand);
    }

    private double getBandFromCriterion(Map<String, Object> criteriaMap, String key) {
        if (criteriaMap == null) return 5.0;
        Object obj = criteriaMap.get(key);
        if (obj instanceof Map<?, ?> map) {
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
