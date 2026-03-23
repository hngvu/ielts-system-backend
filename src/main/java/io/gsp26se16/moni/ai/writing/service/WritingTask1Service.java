package io.gsp26se16.moni.ai.writing.service;

import java.time.LocalDateTime;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.SimpleLoggerAdvisor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.gsp26se16.moni.ai.writing.entity.AiEvaluation;
import io.gsp26se16.moni.ai.writing.entity.WritingSubmission;
import io.gsp26se16.moni.ai.writing.repository.AiEvaluationRepository;
import io.gsp26se16.moni.ai.writing.repository.WritingSubmissionRepository;
import io.gsp26se16.moni.ai.writing.request.WritingRequest;
import io.gsp26se16.moni.authentication.entity.Users;
import io.gsp26se16.moni.authentication.repository.UsersRepository;
import io.gsp26se16.moni.common.enumeration.EvaluationStatus;
import io.gsp26se16.moni.common.enumeration.Skill;
import io.gsp26se16.moni.common.enumeration.WritingTaskType;
import io.gsp26se16.moni.common.exception.AppException;
import io.gsp26se16.moni.common.exception.ErrorCode;
import io.gsp26se16.moni.content.entity.Stimulus;
import io.gsp26se16.moni.content.repository.StimulusRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class WritingTask1Service {

    private final ChatClient.Builder chatClientBuilder;
    private final ObjectMapper objectMapper;
    private final GeminiVisionClient visionClient;
    private final StimulusRepository stimulusRepository;
    private final PromptLoader promptLoader;
    private final RuleEngine ruleEngine;
    private final Helper helper;
    private final WritingSubmissionRepository writingSubmissionRepository;
    private final AiEvaluationRepository aiEvaluationRepository;
    private final UsersRepository usersRepository;

    // =========================================================================
    // PUBLIC ENTRY POINT
    // =========================================================================

    public Map<String, Object> score(WritingRequest request) throws JsonProcessingException {

        // ── Tạo WritingSubmission (PROCESSING) ────────────────────────────────
        WritingSubmission submission = createSubmission(request, WritingTaskType.TASK_1);

        try {
            ChatClient chatClient =
                    chatClientBuilder.defaultAdvisors(new SimpleLoggerAdvisor()).build();

            // ── Vision analysis (cache nếu đã có) ─────────────────────────────
            Map<String, Object> chartData = null;
            if (request.getStimulusId() != null) {
                chartData = getOrCacheVisionAnalysis(request.getStimulusId(), request.getChartImage());
            }

            // ── Phase 1 ───────────────────────────────────────────────────────
            Map<String, Object> parsedEssay = phase1Parse(chatClient, request.getAnswer());

            // ── Phase 2–5 ─────────────────────────────────────────────────────
            Map<String, Object> ta = phase2TaskAchievement(chatClient, request.getAnswer(), parsedEssay, chartData);
            Map<String, Object> cc = phase3Coherence(chatClient, request.getAnswer(), parsedEssay);
            Map<String, Object> lr = phase4Lexical(chatClient, request.getAnswer());
            Map<String, Object> gra = phase5Grammar(chatClient, request.getAnswer());

            // ── Phase 6: Rule Engine ──────────────────────────────────────────
            Map<String, Object> finalResult = phase6Calculate(ta, cc, lr, gra);

            // ── Phase 7: Feedback ─────────────────────────────────────────────
            Map<String, Object> feedback =
                    phase7Feedback(chatClient, request.getQuestion(), request.getAnswer(), finalResult);

            // ── Lưu AiEvaluation + cập nhật submission COMPLETED ─────────────
            double finalBand = (double) finalResult.get("final_band");
            persistEvaluation(submission, finalBand, finalResult, feedback);

            Map<String, Object> response = new HashMap<>();
            response.put("assessment", finalResult);
            response.put("feedback", feedback);
            response.put("parsed_structure", parsedEssay);
            return response;

        } catch (Exception e) {
            log.error("WritingTask1 scoring thất bại cho submission {}: {}", submission.getId(), e.getMessage(), e);
            submission.setEvaluationStatus(EvaluationStatus.FAILED);
            writingSubmissionRepository.save(submission);
            throw e;
        }
    }

    // =========================================================================
    // PHASES
    // =========================================================================

    private Map<String, Object> phase1Parse(ChatClient chatClient, String essay) {
        String prompt = promptLoader.loadPrompt("phase1_parse.txt", Map.of("essay", essay));
        return callEvaluation(chatClient, prompt);
    }

    private Map<String, Object> phase2TaskAchievement(
            ChatClient chatClient, String essay, Map<String, Object> parsed, Map<String, Object> chartData)
            throws JsonProcessingException {
        String prompt = promptLoader.loadPrompt(
                "phase2_ta.txt",
                Map.of(
                        "essay",
                        essay,
                        "phase1_json",
                        objectMapper.writeValueAsString(parsed),
                        "chart_entities",
                        chartData != null ? objectMapper.writeValueAsString(chartData) : "[]"));
        return callEvaluation(chatClient, prompt);
    }

    private Map<String, Object> phase3Coherence(ChatClient chatClient, String essay, Map<String, Object> parsed)
            throws JsonProcessingException {
        String prompt = promptLoader.loadPrompt(
                "phase3_cc.txt", Map.of("essay", essay, "phase1_json", objectMapper.writeValueAsString(parsed)));
        return callEvaluation(chatClient, prompt);
    }

    private Map<String, Object> phase4Lexical(ChatClient chatClient, String essay) {
        String prompt = promptLoader.loadPrompt("phase4_lr.txt", Map.of("essay", essay));
        return callEvaluation(chatClient, prompt);
    }

    private Map<String, Object> phase5Grammar(ChatClient chatClient, String essay) {
        String prompt = promptLoader.loadPrompt("phase5_gra.txt", Map.of("essay", essay));
        return callEvaluation(chatClient, prompt);
    }

    private Map<String, Object> phase6Calculate(
            Map<String, Object> ta, Map<String, Object> cc, Map<String, Object> lr, Map<String, Object> gra) {

        Map<String, Double> rawBands = Map.of(
                "TA", getBand(ta),
                "CC", getBand(cc),
                "LR", getBand(lr),
                "GRA", getBand(gra));

        Map<String, RuleEngine.Violation> violations = helper.collectViolations(ta, cc, lr, gra);
        RuleEngine.RuleResult ruleResult = ruleEngine.applyAllRules(rawBands, violations);
        double finalBand = ruleEngine.calculateFinalBand(ruleResult.adjustedBands(), ruleResult.overallCap());

        Map<String, Object> result = new HashMap<>();
        result.put("final_band", finalBand);
        result.put("overall_cap", ruleResult.overallCap());
        result.put("applied_hard_rules", ruleResult.appliedHardRules());
        result.put(
                "criteria",
                Map.of(
                        "TA", helper.mergeCriterion(ta, ruleResult.adjustedBands()),
                        "CC", helper.mergeCriterion(cc, ruleResult.adjustedBands()),
                        "LR", helper.mergeCriterion(lr, ruleResult.adjustedBands()),
                        "GRA", helper.mergeCriterion(gra, ruleResult.adjustedBands())));
        return result;
    }

    private Map<String, Object> phase7Feedback(
            ChatClient chatClient, String question, String essay, Map<String, Object> finalResult)
            throws JsonProcessingException {
        String prompt = promptLoader.loadPrompt(
                "phase7_feedback.txt",
                Map.of(
                        "question", question,
                        "essay", essay,
                        "all_phase_results", objectMapper.writeValueAsString(finalResult)));
        return callFeedback(chatClient, prompt);
    }

    // =========================================================================
    // DB PERSISTENCE
    // =========================================================================

    /**
     * Tạo WritingSubmission với trạng thái PROCESSING.
     * userId lấy từ JWT trong SecurityContext.
     */
    private WritingSubmission createSubmission(WritingRequest request, WritingTaskType taskType) {
        Users user = resolveCurrentUser();
        int wordCount = countWords(request.getAnswer());

        // Gắn questionGroup nếu có truyền questionGroupId
        Stimulus stimulus = null;
        if (request.getStimulusId() != null) {
            stimulus = stimulusRepository.findById(request.getStimulusId()).orElse(null);
        }

        WritingSubmission submission = WritingSubmission.builder()
                .user(user)
                .stimulus(stimulus)
                .taskType(taskType)
                .essayContent(request.getAnswer())
                .wordCount(wordCount)
                .evaluationStatus(EvaluationStatus.PROCESSING)
                .build();

        WritingSubmission saved = writingSubmissionRepository.save(submission);
        log.info("WritingSubmission tạo: id={}, taskType={}, wordCount={}", saved.getId(), taskType, wordCount);
        return saved;
    }

    /**
     * Lưu AiEvaluation và cập nhật trạng thái submission → COMPLETED.
     */
    private void persistEvaluation(
            WritingSubmission submission,
            double finalBand,
            Map<String, Object> analysisResult,
            Map<String, Object> feedbackResponse) {

        AiEvaluation evaluation = AiEvaluation.builder()
                .submissionId(submission.getId())
                .skill(Skill.WRITING)
                .overallScore(finalBand)
                .analysisResult(analysisResult)
                .feedbackResponse(feedbackResponse)
                .createdAt(LocalDateTime.now())
                .build();

        aiEvaluationRepository.save(evaluation);

        submission.setEvaluationStatus(EvaluationStatus.COMPLETED);
        writingSubmissionRepository.save(submission);

        log.info("AiEvaluation lưu thành công: submissionId={}, band={}", submission.getId(), finalBand);
    }

    // =========================================================================
    // HELPERS
    // =========================================================================

    /**
     * Lấy Users hiện tại từ JWT SecurityContext.
     * Claim "userId" được inject bởi CustomJwtDecoder.
     */
    private Users resolveCurrentUser() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof Jwt jwt) {
            String userId = jwt.getClaim("userId");
            if (userId != null) {
                return usersRepository.findById(userId).orElse(null);
            }
        }
        log.warn("Không tìm được userId từ SecurityContext, submission sẽ không có user");
        return null;
    }

    private int countWords(String text) {
        if (text == null || text.isBlank()) return 0;
        return text.trim().split("\\s+").length;
    }

    private double getBand(Object obj) {
        if (obj instanceof Map<?, ?> map) {
            if (map.containsKey("error")) {
                log.warn("Criterion contains error, defaulting to band 5.0: {}", map.get("error"));
                return 5.0;
            }
            Object val = map.get("band");
            if (val instanceof Number n) return n.doubleValue();
            if (val instanceof String s) {
                try {
                    return Double.parseDouble(s);
                } catch (NumberFormatException e) {
                    log.warn("Cannot parse band: {}", val);
                }
            }
        }
        log.warn("Invalid band value, defaulting to 5.0: {}", obj);
        return 5.0;
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

    private Map<String, Object> callFeedback(ChatClient chatClient, String systemPrompt) {
        String response = chatClient
                .prompt()
                .system(systemPrompt)
                .user("Explain strictly based on evaluation results. Return ONLY raw JSON.")
                .call()
                .content();
        return helper.parseJson(response);
    }

    // =========================================================================
    // VISION CACHE
    // =========================================================================

    private Map<String, Object> getOrCacheVisionAnalysis(Integer stimulusId, MultipartFile chartImage) {
        Stimulus stimulus = stimulusRepository
                .findById(stimulusId)
                .orElseThrow(() -> new AppException(ErrorCode.QUESTION_GROUP_NOT_FOUND));

        if (stimulus.getVisonAnalysisResult() != null
                && !stimulus.getVisonAnalysisResult().isEmpty()) {
            log.info("Dùng cached vision analysis cho QuestionGroup: {}", stimulusId);
            return stimulus.getVisonAnalysisResult();
        }

        try {
            if (chartImage == null || chartImage.isEmpty() || chartImage.getSize() <= 0) {
                log.warn("Chart image rỗng cho QuestionGroup: {}", stimulusId);
                return Map.of();
            }

            byte[] imageBytes = chartImage.getBytes();
            if (imageBytes == null || imageBytes.length == 0) {
                log.warn("Chart image bytes rỗng cho QuestionGroup: {}", stimulusId);
                return Map.of();
            }

            String base64Image = Base64.getEncoder().encodeToString(imageBytes);
            Map<String, Object> analysis = visionClient.analyzeChart(base64Image);

            stimulus.setVisonAnalysisResult(analysis);
            stimulusRepository.save(stimulus);
            return analysis;

        } catch (Exception e) {
            throw new RuntimeException("Không thể xử lý chart image", e);
        }
    }
}
