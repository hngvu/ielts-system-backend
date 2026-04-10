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
import io.gsp26se16.moni.roadmap.entity.LearnerMetric;
import io.gsp26se16.moni.roadmap.repository.LearnerMetricRepository;
import io.gsp26se16.moni.tag.repository.TagRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class WritingTask2Service {

    private final ChatClient.Builder chatClientBuilder;
    private final ObjectMapper objectMapper;
    private final PromptLoader promptLoader;
    private final Helper helper;
    private final WritingSubmissionRepository writingSubmissionRepository;
    private final AiEvaluationRepository aiEvaluationRepository;
    private final UsersRepository usersRepository;
    private final LearnerMetricRepository learnerMetricRepository;
    private final TagRepository tagRepository;

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
                    .orElseGet(() -> createSubmission(request, WritingTaskType.TASK_2));
        } else {
            submission = createSubmission(request, WritingTaskType.TASK_2);
        }
        submission.setEvaluationStatus(EvaluationStatus.PROCESSING);
        writingSubmissionRepository.save(submission);

        try {
            ChatClient chatClient = chatClientBuilder.build();

            String question = request.getQuestion();
            String essay = request.getAnswer();

            // ── Phase 1: Structural parse ─────────────────────────────────────
            Map<String, Object> parsedEssay = phase1Parse(chatClient, question, essay);

            // ── Phase 2–5: Criterion scoring in parallel ──────────────────────
            CompletableFuture<Map<String, Object>> trFuture = CompletableFuture.supplyAsync(
                    () -> {
                        try {
                            return phase2TaskResponse(chatClient, question, essay, parsedEssay);
                        } catch (JsonProcessingException e) {
                            throw new RuntimeException("Error in JSON processing for TR", e);
                        }
                    },
                    aiExecutor);

            CompletableFuture<Map<String, Object>> ccFuture = CompletableFuture.supplyAsync(
                    () -> {
                        try {
                            return phase3Coherence(chatClient, essay, parsedEssay);
                        } catch (JsonProcessingException e) {
                            throw new RuntimeException("Error in JSON processing for CC", e);
                        }
                    },
                    aiExecutor);

            CompletableFuture<Map<String, Object>> lrFuture =
                    CompletableFuture.supplyAsync(() -> phase4Lexical(chatClient, essay), aiExecutor);

            CompletableFuture<Map<String, Object>> graFuture =
                    CompletableFuture.supplyAsync(() -> phase5Grammar(chatClient, essay), aiExecutor);

            CompletableFuture.allOf(trFuture, ccFuture, lrFuture, graFuture).join();

            Map<String, Object> tr = trFuture.join();
            Map<String, Object> cc = ccFuture.join();
            Map<String, Object> lr = lrFuture.join();
            Map<String, Object> gra = graFuture.join();

            // ── Phase 6: Rule Engine + band calculation ───────────────────────
            Map<String, Object> finalResult = helper.calculateBands(tr, cc, lr, gra);

            // ── Phase 7: Feedback ─────────────────────────────────────────────
            Map<String, Object> feedback = phase7Feedback(chatClient, essay, finalResult);

            // ── Lưu AiEvaluation + cập nhật submission COMPLETED ─────────────
            double finalBand = (double) finalResult.get("final_band");
            persistEvaluation(submission, finalBand, finalResult, feedback);

            Map<String, Object> response = new HashMap<>();
            response.put("assessment", finalResult);
            response.put("feedback", feedback);
            response.put("parsed_structure", parsedEssay);
            return response;

        } catch (Exception e) {
            log.error("WritingTask2 scoring thất bại cho submission {}: {}", submission.getId(), e.getMessage(), e);
            submission.setEvaluationStatus(EvaluationStatus.FAILED);
            writingSubmissionRepository.save(submission);
            throw e;
        }
    }

    // =========================================================================
    // PHASES
    // =========================================================================

    private Map<String, Object> phase1Parse(ChatClient chatClient, String question, String essay) {
        String prompt = promptLoader.loadPrompt("phase1_parse_task2.txt", Map.of("question", question, "essay", essay));
        return callEvaluation(chatClient, prompt);
    }

    private Map<String, Object> phase2TaskResponse(
            ChatClient chatClient, String question, String essay, Map<String, Object> parsedEssay)
            throws JsonProcessingException {
        String prompt = promptLoader.loadPrompt(
                "phase2_tr.txt",
                Map.of(
                        "question", question,
                        "essay", essay,
                        "phase2_json", objectMapper.writeValueAsString(parsedEssay)));
        return callEvaluation(chatClient, prompt);
    }

    private Map<String, Object> phase3Coherence(ChatClient chatClient, String essay, Map<String, Object> parsedEssay)
            throws JsonProcessingException {
        String prompt = promptLoader.loadPrompt(
                "phase3_cc.txt", Map.of("essay", essay, "phase1_json", objectMapper.writeValueAsString(parsedEssay)));
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

    private Map<String, Object> phase7Feedback(ChatClient chatClient, String essay, Map<String, Object> finalResult)
            throws JsonProcessingException {
        String prompt = promptLoader.loadPrompt(
                "phase7_feedback_task2.txt",
                Map.of("all_phase_results", objectMapper.writeValueAsString(finalResult), "essay", essay));
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

        WritingSubmission submission = WritingSubmission.builder()
                .user(user)
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
                updateMetricsFromWritingEval(user, analysisResult);
                log.info("Writing metrics updated for user={}, finalBand={}", user.getId(), finalBand);
            }
        } catch (Exception e) {
            log.error("Failed to update writing metrics: {}", e.getMessage(), e);
            // Don't fail the whole evaluation if metric update fails
        }
    }

    /**
     * Update LearnerMetric based on Writing evaluation scores.
     * Called after RuleEngine.calculateBands() finishes.
     *
     * Maps writing criteria to tags:
     * - TR (Task Response) → TR tag
     * - CC (Coherence & Cohesion) → CC tag
     * - LR (Lexical Resource) → LR tag
     * - GRA (Grammar) → GRA tag
     *
     * Uses BKT (Bayesian Knowledge Tracing) algorithm to update mastery.
     */
    private void updateMetricsFromWritingEval(Users user, Map<String, Object> analysisResult) {
        if (analysisResult == null) return;

        Map<String, Object> criteriaMap = (Map<String, Object>) analysisResult.get("criteria");
        if (criteriaMap == null) return;

        // Extract final band for each criterion
        Map<String, Double> criterionBands = new java.util.LinkedHashMap<>();
        for (String criterion : new String[] {"TR", "CC", "LR", "GRA"}) {
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

        // Update metric for each criterion
        for (Map.Entry<String, Double> entry : criterionBands.entrySet()) {
            String criterion = entry.getKey();
            Double band = entry.getValue();

            // Find or create tag for this criterion
            io.gsp26se16.moni.tag.entity.Tag tag =
                    tagRepository.findByCode(criterion).orElse(null);
            if (tag == null) {
                log.debug("Tag not found for criterion={}, skipping metric update", criterion);
                continue;
            }

            // Convert band (0-9) to observation for BKT (0-1)
            double S = band / 9.0;
            boolean isCorrect = S >= 0.6; // Band >= 5.4 counts as "correct"

            // Update with BKT algorithm
            updateMetricBKT(user, tag, isCorrect, S);
        }
    }

    /**
     * Update single metric using BKT formula.
     * Integrated from MasteryService for inline use.
     */
    private void updateMetricBKT(
            Users user, io.gsp26se16.moni.tag.entity.Tag tag, boolean isCorrect, double scoreNormalized) {

        LearnerMetric metric = learnerMetricRepository
                .findByUserAndTag(user, tag)
                .orElseGet(() -> {
                    LearnerMetric m = new LearnerMetric();
                    m.setUser(user);
                    m.setTag(tag);
                    m.setMasteryLevel(0.3); // BKT prior
                    m.setConfidenceScore(0.0);
                    m.setAttemptCount(0); // [MỚI] Khởi tạo số lần làm bài

                    // [MỚI] Thiết lập thông số cá nhân hóa cho SPEAKING
                    m.setPGuess(0.05); // Speaking: Rất khó "đoán lụi" trúng phát âm/ngữ pháp
                    m.setPSlip(0.15); // Speaking: Dễ lỡ miệng, nói vấp do áp lực thời gian
                    m.setPTransit(0.1);
                    return m;
                });

        double pL = metric.getMasteryLevel();
        double pGuess = metric.getPGuess();
        double pSlip = metric.getPSlip();
        double pTransit = metric.getPTransit();

        // Bayesian update
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

        // Apply transition
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
}
