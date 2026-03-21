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
import io.gsp26se16.moni.authentication.entity.Users;
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
            Map<String, Object> fc = evaluateCriterion(chatClient, "FC", fullTranscript, "IELTS Speaking Test");
            Map<String, Object> lr = evaluateCriterion(chatClient, "LR", fullTranscript, "IELTS Speaking Test");
            Map<String, Object> gra = evaluateCriterion(chatClient, "GRA", fullTranscript, "IELTS Speaking Test");
            Map<String, Object> pr = evaluateCriterion(chatClient, "PR", fullTranscript, "IELTS Speaking Test");

            Map<String, Object> assessment = speakingRuleEngine.calculateBands(fc, lr, gra, pr);
            Map<String, Object> feedback = generateFeedback(chatClient, fullTranscript, assessment);

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

    // ─────────────────────────────── Private ─────────────────────────────────

    private SpeakingSubmission createSubmission(String userId, String transcript) {
        Users user = usersRepository.findById(userId).orElse(null);

        SpeakingSubmission submission = SpeakingSubmission.builder()
                .user(user)
                .audioTranscript(transcript)
                .evaluationStatus(EvaluationStatus.PROCESSING)
                .build();

        return speakingSubmissionRepository.save(submission);
    }

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
