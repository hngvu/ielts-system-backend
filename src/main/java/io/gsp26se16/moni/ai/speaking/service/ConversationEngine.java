package io.gsp26se16.moni.ai.speaking.service;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.Function;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.SimpleLoggerAdvisor;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.gsp26se16.moni.ai.speaking.entity.SpeakingSession;
import io.gsp26se16.moni.ai.speaking.entity.SpeakingSubmission;
import io.gsp26se16.moni.ai.speaking.model.ActiveExamSession;
import io.gsp26se16.moni.ai.speaking.repository.SpeakingSessionRepository;
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
import io.gsp26se16.moni.content.entity.TestStructure;
import io.gsp26se16.moni.content.repository.TestStructureRepository;
import io.gsp26se16.moni.roadmap.entity.LearnerMetric;
import io.gsp26se16.moni.roadmap.repository.LearnerMetricRepository;
import io.gsp26se16.moni.tag.entity.Tag;
import io.gsp26se16.moni.tag.repository.TagRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * AI orchestrator cho Speaking exam pipeline.
 *
 * Flow:
 * SpeakingExamHandler gọi evaluateFromExam() sau khi user hoàn thành 3 parts.
 * 1. Tạo SpeakingSubmission (PROCESSING)
 * 2. Chấm điểm 4 tiêu chí: FC, LR, GRA, PR
 * 3. SpeakingRuleEngine điều chỉnh band
 * 4. Tạo feedback
 * 5. Lưu AiEvaluation + cập nhật SpeakingSubmission → COMPLETED
 * 6. Trả kết quả về WebSocket
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ConversationEngine {

    private final SpeakingRuleEngine speakingRuleEngine;
    private final AiEvaluationRepository aiEvaluationRepository;
    private final SpeakingSubmissionRepository speakingSubmissionRepository;
    private final SpeakingSessionRepository speakingSessionRepository;
    private final UsersRepository usersRepository;
    private final UserCredentialsRepository userCredentialsRepository;
    private final LearnerMetricRepository learnerMetricRepository;
    private final TagRepository tagRepository;
    private final PromptLoader promptLoader;
    private final Helper helper;
    private final ObjectMapper objectMapper;
    private final ChatClient.Builder chatClientBuilder;
    private final Executor aiExecutor;
    private final io.gsp26se16.moni.content.repository.TestRepository testRepository;
    private final TestStructureRepository testStructureRepository;

    // ─────────────────────────────── Public API ───────────────────────────────

    /**
     * Đánh giá toàn bộ transcript 3 parts từ exam pipeline.
     * Gọi bởi SpeakingExamHandler sau khi user hoàn thành cả 3 parts.
     *
     * @param session ActiveExamSession chứa toàn bộ thông tin buổi thi
     */
    public Map<String, Object> evaluateFromExam(ActiveExamSession session) {
        String fullTranscript = session.getFullTranscriptWithQuestions();
        List<String> audioUrls = session.getAudioUrls();
        String examSessionId = session.getSessionId();
        String userId = session.getUserId();

        if (fullTranscript == null || fullTranscript.isBlank() || isNoResponseTranscript(fullTranscript)) {
            log.warn("Transcript rỗng cho exam session {}", examSessionId);
            return defaultResult();
        }

        log.info("Bắt đầu đánh giá exam session {} — {} chars", examSessionId, fullTranscript.length());

        SpeakingSubmission submission = createExamSubmission(session, fullTranscript, audioUrls);

        try {
            ChatClient chatClient = chatClientBuilder.build();
            Function<String, Map<String, Object>> fallback = (criterion) -> {
                log.warn("{} fallback → band 0.0", criterion);
                return Map.of(
                        "band", 0.0,
                        "strengths", List.of(),
                        "weaknesses", List.of(),
                        "violations", Map.of(),
                        "justification", "Fallback due to evaluation error");
            };

            // The transcript already contains questions inline, so pass it as the
            // "question" context
            // AI prompts use {question} placeholder — now it receives the full Q&A context
            CompletableFuture<Map<String, Object>> fcFuture = CompletableFuture.supplyAsync(
                            () -> phase1FC(chatClient, fullTranscript, fullTranscript), aiExecutor)
                    .exceptionally(ex -> {
                        log.error("FC failed", ex);
                        return fallback.apply("FC");
                    });

            CompletableFuture<Map<String, Object>> lrFuture = CompletableFuture.supplyAsync(
                            () -> phase2LR(chatClient, fullTranscript, fullTranscript), aiExecutor)
                    .exceptionally(ex -> {
                        log.error("LR failed", ex);
                        return fallback.apply("LR");
                    });

            CompletableFuture<Map<String, Object>> graFuture = CompletableFuture.supplyAsync(
                            () -> phase3GRA(chatClient, fullTranscript, fullTranscript), aiExecutor)
                    .exceptionally(ex -> {
                        log.error("GRA failed", ex);
                        return fallback.apply("GRA");
                    });

            CompletableFuture<Map<String, Object>> prFuture = CompletableFuture.supplyAsync(
                            () -> phase4PR(chatClient, fullTranscript, fullTranscript), aiExecutor)
                    .exceptionally(ex -> {
                        log.error("PR failed", ex);
                        return fallback.apply("PR");
                    });

            CompletableFuture.allOf(fcFuture, lrFuture, graFuture, prFuture).join();

            Map<String, Object> fc = fcFuture.join();
            Map<String, Object> lr = lrFuture.join();
            Map<String, Object> gra = graFuture.join();
            Map<String, Object> pr = prFuture.join();

            Map<String, Object> assessment = speakingRuleEngine.calculateBands(fc, lr, gra, pr);

            Map<String, Object> feedback;
            try {
                feedback = phase5Feedback(chatClient, fullTranscript, assessment);
            } catch (Exception ex) {
                log.error("Feedback failed", ex);
                feedback = Map.of("summary", "Feedback unavailable due to error.");
            }

            double finalBand = (double) assessment.get("final_band");

            persistEvaluation(submission, fullTranscript, assessment, feedback, finalBand);

            Map<String, Object> criteriaMap = (Map<String, Object>) assessment.get("criteria");

            Map<String, Object> response = new LinkedHashMap<>();
            response.put("type", "evaluation");
            response.put("final_band", finalBand);
            response.put("fluency", getBandFromCriterion(criteriaMap, "FC"));
            response.put("vocabulary", getBandFromCriterion(criteriaMap, "LR"));
            response.put("grammar", getBandFromCriterion(criteriaMap, "GRA"));
            response.put("pronunciation", getBandFromCriterion(criteriaMap, "PR"));
            response.put("criteria", criteriaMap);
            response.put("feedback", feedback);
            response.put("transcript", fullTranscript);
            return response;

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
     * Trả format FE expects: { overallScore, fluency, pronunciation, vocabulary,
     * grammar, comments }
     */
    public Map<String, Object> evaluatePractice(String userId, String question, String transcript) {
        if (transcript == null || transcript.isBlank()) {
            log.warn("Empty transcript for practice scoring, userId={}", userId);
            return Map.of(
                    "overallScore", 0.0,
                    "fluency", 0.0,
                    "pronunciation", 0.0,
                    "vocabulary", 0.0,
                    "grammar", 0.0,
                    "comments", "Không phát hiện giọng nói.");
        }

        log.info(
                "Practice speaking scoring for userId={}, question='{}', transcript={} chars",
                userId,
                question.substring(0, Math.min(50, question.length())),
                transcript.length());

        String formattedTranscript = "Question: " + question + "\nAnswer: " + transcript;
        SpeakingSubmission submission = createPracticeSubmission(userId, formattedTranscript);

        try {
            ChatClient chatClient =
                    chatClientBuilder.defaultAdvisors(new SimpleLoggerAdvisor()).build();

            // 🔥 chạy song song + fallback từng phase
            CompletableFuture<Map<String, Object>> fcFuture = CompletableFuture.supplyAsync(
                            () -> phase1FC(chatClient, formattedTranscript, question), aiExecutor)
                    .exceptionally(ex -> {
                        log.error("FC failed", ex);
                        return helper.defaultCriterion("FC");
                    });

            CompletableFuture<Map<String, Object>> lrFuture = CompletableFuture.supplyAsync(
                            () -> phase2LR(chatClient, formattedTranscript, question), aiExecutor)
                    .exceptionally(ex -> {
                        log.error("LR failed", ex);
                        return helper.defaultCriterion("LR");
                    });

            CompletableFuture<Map<String, Object>> graFuture = CompletableFuture.supplyAsync(
                            () -> phase3GRA(chatClient, formattedTranscript, question), aiExecutor)
                    .exceptionally(ex -> {
                        log.error("GRA failed", ex);
                        return helper.defaultCriterion("GRA");
                    });

            CompletableFuture<Map<String, Object>> prFuture = CompletableFuture.supplyAsync(
                            () -> phase4PR(chatClient, formattedTranscript, question), aiExecutor)
                    .exceptionally(ex -> {
                        log.error("PR failed", ex);
                        return helper.defaultCriterion("PR");
                    });

            CompletableFuture.allOf(fcFuture, lrFuture, graFuture, prFuture).join();

            Map<String, Object> fc = fcFuture.join();
            Map<String, Object> lr = lrFuture.join();
            Map<String, Object> gra = graFuture.join();
            Map<String, Object> pr = prFuture.join();

            Map<String, Object> assessment = speakingRuleEngine.calculateBands(fc, lr, gra, pr);

            // feedback fallback
            Map<String, Object> feedback;
            try {
                feedback = phase5Feedback(chatClient, formattedTranscript, assessment);
            } catch (Exception e) {
                log.error("Feedback failed", e);
                feedback = Map.of("summary", "Feedback unavailable.");
            }

            double finalBand = (double) assessment.get("final_band");
            persistEvaluation(submission, formattedTranscript, assessment, feedback, finalBand);

            Map<String, Object> criteriaMap = (Map<String, Object>) assessment.get("criteria");

            String comments = extractComments(feedback);

            Map<String, Object> response = new LinkedHashMap<>();
            response.put("overallScore", finalBand);
            response.put("fluency", getBandFromCriterion(criteriaMap, "FC"));
            response.put("pronunciation", getBandFromCriterion(criteriaMap, "PR"));
            response.put("vocabulary", getBandFromCriterion(criteriaMap, "LR"));
            response.put("grammar", getBandFromCriterion(criteriaMap, "GRA"));
            response.put("criteria", criteriaMap);
            response.put("comments", comments);
            return response;

        } catch (Exception e) {
            log.error("Practice scoring failed for userId={}: {}", userId, e.getMessage(), e);
            submission.setEvaluationStatus(EvaluationStatus.FAILED);
            speakingSubmissionRepository.save(submission);

            return Map.of(
                    "overallScore", 0.0,
                    "fluency", 0.0,
                    "pronunciation", 0.0,
                    "vocabulary", 0.0,
                    "grammar", 0.0,
                    "comments", "Evaluation failed.");
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

    private SpeakingSubmission createExamSubmission(
            ActiveExamSession session, String transcript, List<String> audioUrls) {
        String credentialId = session.getUserId();
        // userId from JWT is credential ID, need to resolve to Users entity
        Users user = null;
        if (credentialId != null) {
            UserCredentials cred =
                    userCredentialsRepository.findById(credentialId).orElse(null);
            if (cred != null) user = cred.getUser();
        }
        if (user == null) {
            user = usersRepository.findById(credentialId).orElse(null);
        }

        String audioUrlsJson = null;
        try {
            if (audioUrls != null && !audioUrls.isEmpty()) {
                audioUrlsJson = objectMapper.writeValueAsString(audioUrls);
            }
        } catch (Exception e) {
            log.warn("Failed to serialize audioUrls", e);
        }

        // Resolve Test entity
        io.gsp26se16.moni.content.entity.Test test = null;
        if (session.getTestId() != null) {
            test = testRepository.findById(session.getTestId()).orElse(null);
        }

        // Resolve SpeakingSession entity
        SpeakingSession speakingSession = null;
        if (session.getSpeakingSessionId() != null) {
            speakingSession = speakingSessionRepository
                    .findById(session.getSpeakingSessionId())
                    .orElse(null);
        }

        SpeakingSubmission submission = SpeakingSubmission.builder()
                .user(user)
                .test(test)
                .speakingSession(speakingSession)
                .audioTranscript(transcript)
                .audioUrl(audioUrlsJson)
                .evaluationStatus(EvaluationStatus.PROCESSING)
                .build();

        return speakingSubmissionRepository.save(submission);
    }

    /** createSubmission for practice mode (no ActiveExamSession) */
    private SpeakingSubmission createPracticeSubmission(String credentialId, String transcript) {
        Users user = null;
        if (credentialId != null) {
            UserCredentials cred =
                    userCredentialsRepository.findById(credentialId).orElse(null);
            if (cred != null) user = cred.getUser();
        }
        if (user == null) {
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

        // ============================================================
        // [NEW] Update LearnerMetric with Speaking evaluation scores
        // ============================================================
        try {
            String userIdStr = submission.getUser().getId();
            if (userIdStr != null) {
                UserCredentials credentials =
                        userCredentialsRepository.findById(userIdStr).orElse(null);
                if (credentials != null && credentials.getUser() != null) {
                    updateMetricsFromSpeakingEval(submission, finalBand, assessment);
                    log.info("Speaking metrics updated for user={}, finalBand={}", userIdStr, finalBand);
                }
            }
        } catch (Exception e) {
            log.error("Failed to update speaking metrics: {}", e.getMessage(), e);
            // Don't fail the whole evaluation if metric update fails
        }
    }

    /**
     * Update LearnerMetric based on Speaking evaluation scores.
     * Called after SpeakingRuleEngine.calculateBands() finishes.
     *
     * Maps speaking criteria to tags:
     * - FC (Fluency & Coherence) → FC tag
     * - LR (Lexical Resource) → LR tag
     * - GRA (Grammar) → GRA tag
     * - PR (Pronunciation) → PR tag
     *
     * Uses BKT (Bayesian Knowledge Tracing) algorithm to update mastery.
     */
    private void updateMetricsFromSpeakingEval(
            SpeakingSubmission submission, double finalBand, Map<String, Object> assessment) {
        if (assessment == null) return;

        Users user = submission.getUser();
        if (user == null) {
            String userIdStr = submission.getUser().getId();
            if (userIdStr != null) {
                UserCredentials credentials =
                        userCredentialsRepository.findById(userIdStr).orElse(null);
                if (credentials != null) user = credentials.getUser();
            }
        }
        if (user == null) return;

        Map<String, Object> criteriaMap = (Map<String, Object>) assessment.get("criteria");
        if (criteriaMap == null) return;

        // Extract final band for each criterion
        Map<String, Double> criterionBands = new LinkedHashMap<>();
        for (String criterion : new String[] {"FC", "LR", "GRA", "PR"}) {
            Object obj = criteriaMap.get(criterion);
            if (obj instanceof Map<?, ?> map) {
                Object adjusted = map.get("adjusted_band");
                if (adjusted instanceof Number n) {
                    criterionBands.put(criterion, n.doubleValue());
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
            // Higher band = stronger signal that student has learned
            double S = band / 9.0;
            boolean isCorrect = S >= 0.6; // Band >= 5.4 counts as "correct"

            // Update with BKT algorithm
            updateMetricBKT(user, tag, isCorrect, S);
        }

        // Update metric for TOPIC tags specifically attached to the Speaking Test structures
        if (submission.getTest() != null) {
            List<TestStructure> structures =
                    testStructureRepository.findByTestId(submission.getTest().getId());
            if (structures != null) {
                double finalS = finalBand / 9.0;
                boolean finalIsCorrect = finalS >= 0.6;

                for (TestStructure structure : structures) {
                    if (structure.getStimulus() != null
                            && structure.getStimulus().getTags() != null) {
                        for (Tag tag : structure.getStimulus().getTags()) {
                            if (tag.getType() == io.gsp26se16.moni.tag.entity.TagType.TOPIC) {
                                updateMetricBKT(user, tag, finalIsCorrect, finalS);
                            }
                        }
                    }
                }
            }
        }
    }

    /**
     * Update single metric using BKT formula.
     * Integrated from MasteryService for inline use.
     */
    private void updateMetricBKT(Users user, Tag tag, boolean isCorrect, double scoreNormalized) {

        LearnerMetric metric = learnerMetricRepository
                .findByUserAndTagAndSkill(user, tag, Skill.SPEAKING)
                .orElseGet(() -> {
                    LearnerMetric m = new LearnerMetric();
                    m.setUser(user);
                    m.setTag(tag);
                    m.setSkill(Skill.SPEAKING);
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

    private boolean isNoResponseTranscript(String transcript) {
        if (transcript == null) return true;

        String cleaned = transcript
                .replaceAll("=== PART \\d ===", "")
                .replaceAll("Q\\d+:", "")
                .replace("[no response]", "");

        long wordCount = Arrays.stream(cleaned.split("\\s+"))
                .filter(w -> !w.trim().isEmpty())
                .count();

        return wordCount <= 5;
    }
}
