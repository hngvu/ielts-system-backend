package io.gsp26se16.moni.ai.writing.service;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;

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
import io.gsp26se16.moni.roadmap.entity.LearnerMetric;
import io.gsp26se16.moni.roadmap.repository.LearnerMetricRepository;
import io.gsp26se16.moni.roadmap.service.WeeklyPlanService;
import io.gsp26se16.moni.tag.repository.TagRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class WritingTask1ServiceImpl implements WritingTask1Service {

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
    private final LearnerMetricRepository learnerMetricRepository;
    private final TagRepository tagRepository;
    private final WeeklyPlanService weeklyPlanService;

    @Qualifier("aiExecutor")
    private final Executor aiExecutor;

    // =========================================================================
    // PUBLIC ENTRY POINT
    // =========================================================================

    public Map<String, Object> score(WritingRequest request) throws JsonProcessingException {

        WritingSubmission submission;
        if (request.getSubmissionId() != null) {
            submission = writingSubmissionRepository
                    .findById(request.getSubmissionId())
                    .orElseGet(() -> createSubmission(request, WritingTaskType.TASK_1));
        } else {
            submission = createSubmission(request, WritingTaskType.TASK_1);
        }
        submission.setEvaluationStatus(EvaluationStatus.PROCESSING);
        writingSubmissionRepository.save(submission);

        try {
            ChatClient chatClient = chatClientBuilder.build();

            // ── Vision analysis: read pre-computed data from DB ────────────────
            Map<String, Object> tempChartData = null;
            if (request.getStimulusId() != null) {
                tempChartData = getPreComputedVisionAnalysis(request.getStimulusId());
            }
            final Map<String, Object> chartData = tempChartData;

            // ── Phase 1 ───────────────────────────────────────────────────────
            Map<String, Object> parsedEssay = phase1Parse(chatClient, request.getAnswer());

            // ── Phase 2–5 in parallel ─────────────────────────────────────────
            CompletableFuture<Map<String, Object>> taFuture = CompletableFuture.supplyAsync(
                    () -> {
                        try {
                            return phase2TaskAchievement(chatClient, request.getAnswer(), parsedEssay, chartData);
                        } catch (JsonProcessingException e) {
                            throw new RuntimeException("Error in JSON processing for TA", e);
                        }
                    },
                    aiExecutor);

            CompletableFuture<Map<String, Object>> ccFuture = CompletableFuture.supplyAsync(
                    () -> {
                        try {
                            return phase3Coherence(chatClient, request.getAnswer(), parsedEssay);
                        } catch (JsonProcessingException e) {
                            throw new RuntimeException("Error in JSON processing for CC", e);
                        }
                    },
                    aiExecutor);

            CompletableFuture<Map<String, Object>> lrFuture =
                    CompletableFuture.supplyAsync(() -> phase4Lexical(chatClient, request.getAnswer()), aiExecutor);

            CompletableFuture<Map<String, Object>> graFuture =
                    CompletableFuture.supplyAsync(() -> phase5Grammar(chatClient, request.getAnswer()), aiExecutor);

            CompletableFuture.allOf(taFuture, ccFuture, lrFuture, graFuture).join();

            Map<String, Object> ta = taFuture.join();
            Map<String, Object> cc = ccFuture.join();
            Map<String, Object> lr = lrFuture.join();
            Map<String, Object> gra = graFuture.join();

            // ── Phase 6: Rule Engine ──────────────────────────────────────────
            Map<String, Object> finalResult = phase6Calculate(ta, cc, lr, gra);

            // ── Phase 7: Feedback ─────────────────────────────────────────────
            Map<String, Object> feedback = phase7Feedback(chatClient, chartData, request.getAnswer(), finalResult);

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
        String prompt = promptLoader.loadPrompt("writing/phase1_parse.txt", Map.of("essay", essay));
        return callEvaluation(chatClient, prompt);
    }

    private Map<String, Object> phase2TaskAchievement(
            ChatClient chatClient, String essay, Map<String, Object> parsed, Map<String, Object> chartData)
            throws JsonProcessingException {
        String prompt = promptLoader.loadPrompt(
                "writing/phase2_ta.txt",
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
                "writing/phase3_cc.txt",
                Map.of("essay", essay, "phase1_json", objectMapper.writeValueAsString(parsed)));
        return callEvaluation(chatClient, prompt);
    }

    private Map<String, Object> phase4Lexical(ChatClient chatClient, String essay) {
        String prompt = promptLoader.loadPrompt("writing/phase4_lr.txt", Map.of("essay", essay));
        return callEvaluation(chatClient, prompt);
    }

    private Map<String, Object> phase5Grammar(ChatClient chatClient, String essay) {
        String prompt = promptLoader.loadPrompt("writing/phase5_gra.txt", Map.of("essay", essay));
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
            ChatClient chatClient, Map<String, Object> chartData, String essay, Map<String, Object> finalResult)
            throws JsonProcessingException {
        String prompt = promptLoader.loadPrompt(
                "writing/phase7_feedback.txt",
                Map.of(
                        "chart_entities",
                        chartData != null ? objectMapper.writeValueAsString(chartData) : "[]",
                        "essay",
                        essay,
                        "all_phase_results",
                        objectMapper.writeValueAsString(finalResult)));
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

        // ============================================================
        // [NEW] Update LearnerMetric with Writing evaluation scores
        // ============================================================
        try {
            Users user = submission.getUser();
            if (user != null) {
                updateMetricsFromWritingEval(submission, finalBand, analysisResult);
                log.info("Writing metrics updated for user={}, finalBand={}", user.getId(), finalBand);

                // [NEW] Auto-complete weekly plan test slot
                if (submission.getTestId() != null) {
                    // Store band*10 in score to preserve decimal (e.g. 7.5 → 75, totalQuestions=90)
                    weeklyPlanService.autoCompleteTestSlot(
                            user, submission.getTestId(), (int) Math.round(finalBand * 10), 90);
                }
            }
        } catch (Exception e) {
            log.error("Failed to update writing metrics or weekly plan: {}", e.getMessage(), e);
            // Don't fail the whole evaluation if metric update fails
        }
    }

    /**
     * Update LearnerMetric based on Writing evaluation scores.
     * Called after RuleEngine.applyAllRules() finishes.
     *
     * Maps writing criteria to tags:
     * - TA (Task Achievement) → TA tag
     * - CC (Coherence & Cohesion) → CC tag
     * - LR (Lexical Resource) → LR tag
     * - GRA (Grammar) → GRA tag
     *
     * Uses BKT (Bayesian Knowledge Tracing) algorithm to update mastery.
     */
    private void updateMetricsFromWritingEval(
            WritingSubmission submission, double finalBand, Map<String, Object> analysisResult) {
        if (analysisResult == null) return;

        Users user = submission.getUser();
        if (user == null) return;

        Map<String, Object> criteriaMap = (Map<String, Object>) analysisResult.get("criteria");
        if (criteriaMap == null) return;

        // Extract final band for each criterion
        Map<String, Double> criterionBands = new java.util.LinkedHashMap<>();
        for (String criterion : new String[] {"TA", "CC", "LR", "GRA"}) {
            Object obj = criteriaMap.get(criterion);
            if (obj instanceof Map<?, ?> map) {
                Object adjusted = map.get("adjusted_band");
                if (adjusted instanceof Number n) {
                    criterionBands.put(criterion, n.doubleValue());
                } else {
                    Object band = map.get("band");
                    if (band instanceof Number n) {
                        criterionBands.put(criterion, n.doubleValue());
                    }
                }
            }
        }

        // Map AI criterion keys to writing tag codes (W_ prefix to avoid collision with speaking tags)
        Map<String, String> criterionToTagCode =
                Map.of("TA", "W_TA", "TR", "W_TA", "CC", "W_CC", "LR", "W_LR", "GRA", "W_GRA");

        // Update metric for each criterion
        for (Map.Entry<String, Double> entry : criterionBands.entrySet()) {
            String criterion = entry.getKey();
            Double band = entry.getValue();

            // Find or create tag for this criterion
            String tagCode = criterionToTagCode.getOrDefault(criterion, criterion);
            io.gsp26se16.moni.tag.entity.Tag tag =
                    tagRepository.findByCode(tagCode).orElse(null);
            if (tag == null) {
                log.debug("Tag not found for criterion={} (tagCode={}), skipping metric update", criterion, tagCode);
                continue;
            }

            // For writing criteria, use band directly as mastery (not binary BKT)
            double mastery = band / 9.0;
            updateCriterionMetric(user, tag, mastery);
        }

        // Update metric for tags specifically attached to the Stimulus (like WRITING_TYPE or TOPIC)
        if (submission.getStimulus() != null && submission.getStimulus().getTags() != null) {
            double finalS = finalBand / 9.0;
            boolean finalIsCorrect = finalS >= 0.6;

            for (io.gsp26se16.moni.tag.entity.Tag tag : submission.getStimulus().getTags()) {
                if (tag.getType() == io.gsp26se16.moni.tag.entity.TagType.WRITING_TYPE
                        || tag.getType() == io.gsp26se16.moni.tag.entity.TagType.TOPIC) {
                    updateMetricBKT(user, tag, finalIsCorrect, finalS);
                }
            }
        }
    }

    /**
     * Update criterion metric using band score directly as mastery level.
     */
    private void updateCriterionMetric(Users user, io.gsp26se16.moni.tag.entity.Tag tag, double mastery) {
        LearnerMetric metric = learnerMetricRepository
                .findByUserAndTagAndSkill(user, tag, Skill.WRITING)
                .orElseGet(() -> {
                    LearnerMetric m = new LearnerMetric();
                    m.setUser(user);
                    m.setTag(tag);
                    m.setSkill(Skill.WRITING);
                    m.setMasteryLevel(0.0);
                    m.setConfidenceScore(0.0);
                    m.setAttemptCount(0);
                    m.setPGuess(0.05);
                    m.setPSlip(0.15);
                    m.setPTransit(0.1);
                    return m;
                });

        metric.setMasteryLevel(Math.max(0.0, Math.min(1.0, mastery)));
        metric.setAttemptCount(metric.getAttemptCount() == null ? 1 : metric.getAttemptCount() + 1);
        double calculatedConfidence = 1.0 - (1.0 / (metric.getAttemptCount() + 1.0));
        metric.setConfidenceScore(calculatedConfidence);
        metric.setUpdatedAt(LocalDateTime.now());

        learnerMetricRepository.save(metric);
        log.debug("[Writing-Criterion] tag={}, mastery={}, conf={}", tag.getName(), mastery, calculatedConfidence);
    }

    /**
     * Update single metric using BKT formula (for non-criteria tags).
     */
    private void updateMetricBKT(
            Users user, io.gsp26se16.moni.tag.entity.Tag tag, boolean isCorrect, double scoreNormalized) {

        LearnerMetric metric = learnerMetricRepository
                .findByUserAndTagAndSkill(user, tag, Skill.WRITING)
                .orElseGet(() -> {
                    LearnerMetric m = new LearnerMetric();
                    m.setUser(user);
                    m.setTag(tag);
                    m.setSkill(Skill.WRITING);
                    m.setMasteryLevel(0.3);
                    m.setConfidenceScore(0.0);
                    m.setAttemptCount(0);
                    m.setPGuess(0.05);
                    m.setPSlip(0.15);
                    m.setPTransit(0.1);
                    return m;
                });

        double pL = metric.getMasteryLevel();
        double pGuess = metric.getPGuess();
        double pSlip = metric.getPSlip();
        double pTransit = metric.getPTransit();

        double pLnew;
        if (isCorrect) {
            double pCorrectGivenL = 1.0 - pSlip;
            double pCorrectGivenNotL = pGuess;
            double pCorrect = (pL * pCorrectGivenL) + ((1.0 - pL) * pCorrectGivenNotL);
            pLnew = (pL * pCorrectGivenL) / pCorrect;
        } else {
            double pIncorrectGivenL = pSlip;
            double pIncorrectGivenNotL = 1.0 - pGuess;
            double pIncorrect = (pL * pIncorrectGivenL) + ((1.0 - pL) * pIncorrectGivenNotL);
            pLnew = (pL * pIncorrectGivenL) / pIncorrect;
        }

        double pLfinal = pLnew + ((1.0 - pLnew) * pTransit);
        pLfinal = Math.max(0.0, Math.min(1.0, pLfinal));

        // ============================================================
        // [MỚI] Cập nhật Metric với attemptCount và tính toán Confidence
        // ============================================================
        metric.setMasteryLevel(pLfinal);

        // Tăng số lần luyện tập Speaking lên 1
        metric.setAttemptCount(metric.getAttemptCount() == null ? 1 : metric.getAttemptCount() + 1);

        // Tính độ tự tin theo đường cong (1 lần -> 50%, 4 lần -> 80%, ...)
        double calculatedConfidence = 1.0 - (1.0 / (metric.getAttemptCount() + 1.0));
        metric.setConfidenceScore(calculatedConfidence);

        metric.setUpdatedAt(LocalDateTime.now());

        learnerMetricRepository.save(metric);
        log.debug(
                "[Speaking-BKT] tag={}, Attempt={}, pL(final)={}, Conf={}",
                tag.getName(),
                metric.getAttemptCount(),
                pLfinal,
                calculatedConfidence);
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

    /**
     * Retrieves the pre-computed Vision analysis result for a stimulus.
     * This data is populated by Admin at test creation time via
     * POST /api/v1/admin/stimuli/{id}/analyze-chart.
     *
     * Returns empty map if no analysis has been performed yet.
     */
    private Map<String, Object> getPreComputedVisionAnalysis(Integer stimulusId) {
        Stimulus stimulus = stimulusRepository
                .findById(stimulusId)
                .orElseThrow(() -> new AppException(ErrorCode.QUESTION_GROUP_NOT_FOUND));

        if (stimulus.getVisonAnalysisResult() != null
                && !stimulus.getVisonAnalysisResult().isEmpty()) {
            log.info("Using pre-computed vision analysis for Stimulus: {}", stimulusId);
            return stimulus.getVisonAnalysisResult();
        }

        log.warn(
                "No pre-computed vision analysis for Stimulus {}. "
                        + "Please use /api/v1/admin/stimuli/{id}/analyze-chart to pre-compute.",
                stimulusId);
        return Map.of();
    }
}
