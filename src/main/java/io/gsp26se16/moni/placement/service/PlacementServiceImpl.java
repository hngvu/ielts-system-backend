package io.gsp26se16.moni.placement.service;

import java.time.LocalDateTime;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Lazy;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.core.JsonProcessingException;

import io.gsp26se16.moni.ai.speaking.service.ConversationEngine;
import io.gsp26se16.moni.ai.writing.request.WritingRequest;
import io.gsp26se16.moni.ai.writing.service.WritingTask1Service;
import io.gsp26se16.moni.ai.writing.service.WritingTask2Service;
import io.gsp26se16.moni.authentication.entity.UserCredentials;
import io.gsp26se16.moni.authentication.entity.Users;
import io.gsp26se16.moni.authentication.repository.UserCredentialsRepository;
import io.gsp26se16.moni.common.enumeration.Skill;
import io.gsp26se16.moni.common.exception.AppException;
import io.gsp26se16.moni.common.exception.ErrorCode;
import io.gsp26se16.moni.content.entity.Question;
import io.gsp26se16.moni.content.entity.QuestionOption;
import io.gsp26se16.moni.content.entity.Stimulus;
import io.gsp26se16.moni.content.entity.TestStructure;
import io.gsp26se16.moni.content.repository.QuestionRepository;
import io.gsp26se16.moni.content.repository.TestRepository;
import io.gsp26se16.moni.content.repository.TestStructureRepository;
import io.gsp26se16.moni.content.service.TestService;
import io.gsp26se16.moni.content.service.TranscriptService;
import io.gsp26se16.moni.placement.dto.request.PlacementSelfAssessRequest;
import io.gsp26se16.moni.placement.dto.request.PlacementSubmitRequest;
import io.gsp26se16.moni.placement.dto.response.PlacementResultResponse;
import io.gsp26se16.moni.placement.dto.response.PlacementTestResponse;
import io.gsp26se16.moni.placement.entity.PlacementConfig;
import io.gsp26se16.moni.placement.entity.PlacementResult;
import io.gsp26se16.moni.placement.repository.PlacementConfigRepository;
import io.gsp26se16.moni.placement.repository.PlacementResultRepository;
import io.gsp26se16.moni.placement.util.BandScoreUtil;
import io.gsp26se16.moni.practice.dto.request.AnswerRequest;
import io.gsp26se16.moni.roadmap.entity.LearnerMetric;
import io.gsp26se16.moni.roadmap.repository.LearnerMetricRepository;
import io.gsp26se16.moni.roadmap.service.GoalService;
import io.gsp26se16.moni.tag.entity.Tag;
import io.gsp26se16.moni.tag.repository.TagRepository;
import lombok.extern.slf4j.Slf4j;

@Service
@Transactional
@Slf4j
public class PlacementServiceImpl implements PlacementService {

    private final PlacementResultRepository placementResultRepository;
    private final PlacementConfigRepository placementConfigRepository;
    private final TestRepository testRepository;
    private final TestService testService;
    private final TestStructureRepository testStructureRepository;
    private final QuestionRepository questionRepository;
    private final UserCredentialsRepository userCredentialsRepository;
    private final GoalService goalService;
    private final LearnerMetricRepository learnerMetricRepository;
    private final TagRepository tagRepository;
    private final WritingTask1Service writingTask1Service;
    private final WritingTask2Service writingTask2Service;
    private final ConversationEngine conversationEngine;
    private final TranscriptService transcriptService;
    private final Executor aiExecutor;

    public PlacementServiceImpl(
            PlacementResultRepository placementResultRepository,
            PlacementConfigRepository placementConfigRepository,
            TestRepository testRepository,
            TestService testService,
            TestStructureRepository testStructureRepository,
            QuestionRepository questionRepository,
            UserCredentialsRepository userCredentialsRepository,
            @Lazy GoalService goalService,
            LearnerMetricRepository learnerMetricRepository,
            TagRepository tagRepository,
            WritingTask1Service writingTask1Service,
            WritingTask2Service writingTask2Service,
            ConversationEngine conversationEngine,
            TranscriptService transcriptService,
            @Qualifier("aiExecutor") Executor aiExecutor) {
        this.placementResultRepository = placementResultRepository;
        this.placementConfigRepository = placementConfigRepository;
        this.testRepository = testRepository;
        this.testService = testService;
        this.testStructureRepository = testStructureRepository;
        this.questionRepository = questionRepository;
        this.userCredentialsRepository = userCredentialsRepository;
        this.goalService = goalService;
        this.learnerMetricRepository = learnerMetricRepository;
        this.tagRepository = tagRepository;
        this.writingTask1Service = writingTask1Service;
        this.writingTask2Service = writingTask2Service;
        this.conversationEngine = conversationEngine;
        this.transcriptService = transcriptService;
        this.aiExecutor = aiExecutor;
    }

    @Override
    @Transactional(readOnly = true)
    public PlacementTestResponse generate() {
        PlacementConfig config = placementConfigRepository
                .findByIsActiveTrue()
                .orElseThrow(() -> new AppException(ErrorCode.PLACEMENT_CONFIG_NONE_ACTIVE));

        return PlacementTestResponse.builder()
                .readingTest(testService.getTestDetail(config.getReadingTest().getId()))
                .listeningTest(
                        testService.getTestDetail(config.getListeningTest().getId()))
                .writingTest(testService.getTestDetail(config.getWritingTest().getId()))
                .speakingTest(testService.getTestDetail(config.getSpeakingTest().getId()))
                .build();
    }

    @Override
    public PlacementResultResponse submit(PlacementSubmitRequest request) {
        Users user = getCurrentUser();
        validateBand(request.getTargetBand());

        // 1. Grade Reading & Listening (instant)
        int readingTotal = testRepository.countQuestionsByTestId(request.getReadingTestId());
        int listeningTotal = testRepository.countQuestionsByTestId(request.getListeningTestId());

        int readingCorrect = gradeAnswers(user, Skill.READING, request.getReadingAnswers());
        int listeningCorrect = gradeAnswers(user, Skill.LISTENING, request.getListeningAnswers());

        double readingBand = BandScoreUtil.readingBand(readingCorrect, readingTotal);
        double listeningBand = BandScoreUtil.listeningBand(listeningCorrect, listeningTotal);

        // 2. Grade Writing & Speaking in parallel (AI-based)
        CompletableFuture<Map<String, Object>> writingFuture =
                CompletableFuture.supplyAsync(() -> gradeWriting(request), aiExecutor);

        CompletableFuture<Map<String, Object>> speakingFuture =
                CompletableFuture.supplyAsync(() -> gradeSpeaking(user, request), aiExecutor);

        CompletableFuture.allOf(writingFuture, speakingFuture).join();

        Map<String, Object> writingResult = writingFuture.join();
        Map<String, Object> speakingResult = speakingFuture.join();

        double writingBand = extractWritingFinalBand(writingResult);
        double speakingBand = extractSpeakingFinalBand(speakingResult);

        double overallBand = BandScoreUtil.overallBand(readingBand, listeningBand, writingBand, speakingBand);

        // 3. Save PlacementResult
        PlacementResult result = PlacementResult.builder()
                .user(user)
                .readingBand(readingBand)
                .listeningBand(listeningBand)
                .writingBand(writingBand)
                .speakingBand(speakingBand)
                .overallBand(overallBand)
                .targetBand(request.getTargetBand())
                .readingCorrect(readingCorrect)
                .listeningCorrect(listeningCorrect)
                .isSelfAssessed(false)
                .build();

        result = placementResultRepository.save(result);

        // 4. Seed Writing & Speaking LearnerMetrics from AI criteria
        seedWritingSpeakingMetricsFromAI(user, writingResult, speakingResult);

        // 5. Auto-create Goals
        try {
            goalService.createGoalsFromPlacement(
                    user,
                    readingBand,
                    listeningBand,
                    writingBand,
                    speakingBand,
                    user.getTargetReading(),
                    user.getTargetListening(),
                    user.getTargetWriting(),
                    user.getTargetSpeaking(),
                    user.getExamDate());
        } catch (Exception e) {
            log.warn("Không tạo được Goals từ placement result: {}", e.getMessage());
        }

        return toResponseWithAIDetails(result, writingResult, speakingResult);
    }

    @Override
    public PlacementResultResponse selfAssess(PlacementSelfAssessRequest request) {
        Users user = getCurrentUser();
        validateBand(request.getReadingBand());
        validateBand(request.getListeningBand());
        validateBand(request.getWritingBand());
        validateBand(request.getSpeakingBand());
        validateBand(request.getTargetBand());

        double overallBand = BandScoreUtil.overallBand(
                request.getReadingBand(),
                request.getListeningBand(),
                request.getWritingBand(),
                request.getSpeakingBand());

        PlacementResult result = PlacementResult.builder()
                .user(user)
                .readingBand(request.getReadingBand())
                .listeningBand(request.getListeningBand())
                .writingBand(request.getWritingBand())
                .speakingBand(request.getSpeakingBand())
                .overallBand(overallBand)
                .targetBand(request.getTargetBand())
                .isSelfAssessed(true)
                .build();

        result = placementResultRepository.save(result);

        seedAllSkillMetrics(
                user,
                request.getReadingBand(),
                request.getListeningBand(),
                request.getWritingBand(),
                request.getSpeakingBand());

        try {
            goalService.createGoalsFromPlacement(
                    user,
                    request.getReadingBand(),
                    request.getListeningBand(),
                    request.getWritingBand(),
                    request.getSpeakingBand(),
                    user.getTargetReading(),
                    user.getTargetListening(),
                    user.getTargetWriting(),
                    user.getTargetSpeaking(),
                    user.getExamDate());
        } catch (Exception e) {
            log.warn("Không tạo được Goals từ self-assess result: {}", e.getMessage());
        }

        return toResponse(result);
    }

    @Override
    @Transactional(readOnly = true)
    public PlacementResultResponse getResult() {
        Users user = getCurrentUser();
        return placementResultRepository
                .findFirstByUserOrderByCompletedAtDesc(user)
                .map(this::toResponse)
                .orElse(null);
    }

    @Override
    public void reset() {
        Users user = getCurrentUser();
        placementResultRepository.deleteAllByUser(user);
    }

    // =========================================================================
    // WRITING & SPEAKING AI GRADING
    // =========================================================================

    private Map<String, Object> gradeWriting(PlacementSubmitRequest request) {
        try {
            String question = extractWritingQuestion(request.getWritingTestId());

            WritingRequest wr = new WritingRequest();
            wr.setQuestion(question);
            wr.setAnswer(request.getWritingEssay());
            wr.setTaskType(request.getWritingTaskType());
            wr.setStimulusId(request.getWritingStimulusId());

            if (request.getWritingTaskType() == 1) {
                return writingTask1Service.score(wr);
            } else {
                return writingTask2Service.score(wr);
            }
        } catch (JsonProcessingException e) {
            log.error("Writing AI grading failed: {}", e.getMessage());
            return Map.of();
        } catch (Exception e) {
            log.error("Writing AI grading failed: {}", e.getMessage());
            return Map.of();
        }
    }

    private Map<String, Object> gradeSpeaking(Users user, PlacementSubmitRequest request) {
        try {
            byte[] audioBytes = Base64.getDecoder().decode(request.getSpeakingAudioBase64());
            String transcript = transcriptService.transcribeAudioBytes(audioBytes, "audio/webm");

            String question = extractSpeakingQuestion(request.getSpeakingTestId());

            return conversationEngine.evaluatePractice(user.getId().toString(), question, transcript);
        } catch (Exception e) {
            log.error("Speaking AI grading failed: {}", e.getMessage());
            return Map.of();
        }
    }

    private String extractWritingQuestion(Integer testId) {
        List<TestStructure> structures = testStructureRepository.findByTestId(testId);
        if (structures.isEmpty()) return "Write an essay on the given topic.";

        Stimulus stimulus = structures.get(0).getStimulus();
        return stimulus.getContent() != null ? stimulus.getContent() : "Write an essay on the given topic.";
    }

    private String extractSpeakingQuestion(Integer testId) {
        List<TestStructure> structures = testStructureRepository.findByTestId(testId);
        if (structures.isEmpty()) return "Describe a topic you are interested in.";

        Stimulus stimulus = structures.get(0).getStimulus();
        if (stimulus.getContent() != null) return stimulus.getContent();

        // Fallback: get first question from question groups
        if (stimulus.getQuestionGroups() != null
                && !stimulus.getQuestionGroups().isEmpty()) {
            var firstGroup = stimulus.getQuestionGroups().iterator().next();
            if (firstGroup.getQuestions() != null && !firstGroup.getQuestions().isEmpty()) {
                var firstQuestion = firstGroup.getQuestions().iterator().next();
                if (firstQuestion.getContent() != null) return firstQuestion.getContent();
            }
        }

        return "Describe a topic you are interested in.";
    }

    @SuppressWarnings("unchecked")
    private double extractWritingFinalBand(Map<String, Object> result) {
        try {
            if (result == null || result.isEmpty()) return 0.0;
            Map<String, Object> assessment = (Map<String, Object>) result.get("assessment");
            if (assessment == null) return 0.0;
            Object finalBand = assessment.get("final_band");
            if (finalBand instanceof Number n) return n.doubleValue();
            return 0.0;
        } catch (Exception e) {
            log.warn("Could not extract writing final band: {}", e.getMessage());
            return 0.0;
        }
    }

    private double extractSpeakingFinalBand(Map<String, Object> result) {
        try {
            if (result == null || result.isEmpty()) return 0.0;
            Object score = result.get("overallScore");
            if (score instanceof Number n) return n.doubleValue();
            return 0.0;
        } catch (Exception e) {
            log.warn("Could not extract speaking final band: {}", e.getMessage());
            return 0.0;
        }
    }

    // =========================================================================
    // SEED LEARNER METRICS
    // =========================================================================

    /**
     * Seed Writing & Speaking LearnerMetrics from AI evaluation criteria.
     * Uses per-criterion bands from AI (higher accuracy than self-assessment).
     */
    @SuppressWarnings("unchecked")
    private void seedWritingSpeakingMetricsFromAI(
            Users user, Map<String, Object> writingResult, Map<String, Object> speakingResult) {
        LocalDateTime now = LocalDateTime.now();

        // Writing criteria from AI
        Map<String, Double> writingCriteria = extractWritingCriteriaBands(writingResult);
        seedCriterionMetricFromAI(user, "W_TA", Skill.WRITING, writingCriteria.getOrDefault("TA", 0.0), now);
        seedCriterionMetricFromAI(user, "W_CC", Skill.WRITING, writingCriteria.getOrDefault("CC", 0.0), now);
        seedCriterionMetricFromAI(user, "W_LR", Skill.WRITING, writingCriteria.getOrDefault("LR", 0.0), now);
        seedCriterionMetricFromAI(user, "W_GRA", Skill.WRITING, writingCriteria.getOrDefault("GRA", 0.0), now);

        // Speaking criteria from AI
        Map<String, Double> speakingCriteria = extractSpeakingCriteriaBands(speakingResult);
        seedCriterionMetricFromAI(user, "FC", Skill.SPEAKING, speakingCriteria.getOrDefault("FC", 0.0), now);
        seedCriterionMetricFromAI(user, "LR", Skill.SPEAKING, speakingCriteria.getOrDefault("LR", 0.0), now);
        seedCriterionMetricFromAI(user, "GRA", Skill.SPEAKING, speakingCriteria.getOrDefault("GRA", 0.0), now);
        seedCriterionMetricFromAI(user, "PR", Skill.SPEAKING, speakingCriteria.getOrDefault("PR", 0.0), now);

        log.info("Seeded Writing/Speaking LearnerMetrics from AI evaluation");
    }

    @SuppressWarnings("unchecked")
    private Map<String, Double> extractWritingCriteriaBands(Map<String, Object> result) {
        try {
            if (result == null || result.isEmpty()) return Map.of();
            Map<String, Object> assessment = (Map<String, Object>) result.get("assessment");
            if (assessment == null) return Map.of();
            Map<String, Object> criteria = (Map<String, Object>) assessment.get("criteria");
            if (criteria == null) return Map.of();

            return Map.of(
                    "TA", getBandFromCriterion(criteria, "TA", "TR"),
                    "CC", getBandFromCriterion(criteria, "CC", null),
                    "LR", getBandFromCriterion(criteria, "LR", null),
                    "GRA", getBandFromCriterion(criteria, "GRA", null));
        } catch (Exception e) {
            log.warn("Could not extract writing criteria bands: {}", e.getMessage());
            return Map.of();
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Double> extractSpeakingCriteriaBands(Map<String, Object> result) {
        try {
            if (result == null || result.isEmpty()) return Map.of();
            Map<String, Object> criteria = (Map<String, Object>) result.get("criteria");
            if (criteria == null) {
                // Fallback to top-level keys
                return Map.of(
                        "FC", getDoubleOrDefault(result, "fluency", 0.0),
                        "LR", getDoubleOrDefault(result, "vocabulary", 0.0),
                        "GRA", getDoubleOrDefault(result, "grammar", 0.0),
                        "PR", getDoubleOrDefault(result, "pronunciation", 0.0));
            }

            return Map.of(
                    "FC", getBandFromCriterion(criteria, "FC", null),
                    "LR", getBandFromCriterion(criteria, "LR", null),
                    "GRA", getBandFromCriterion(criteria, "GRA", null),
                    "PR", getBandFromCriterion(criteria, "PR", null));
        } catch (Exception e) {
            log.warn("Could not extract speaking criteria bands: {}", e.getMessage());
            return Map.of();
        }
    }

    @SuppressWarnings("unchecked")
    private double getBandFromCriterion(Map<String, Object> criteriaMap, String key, String altKey) {
        Object obj = criteriaMap.get(key);
        if (obj == null && altKey != null) obj = criteriaMap.get(altKey);
        if (obj instanceof Map<?, ?> map) {
            Object adjusted = map.get("adjusted_band");
            if (adjusted instanceof Number n) return n.doubleValue();
            Object band = map.get("band");
            if (band instanceof Number n) return n.doubleValue();
        }
        if (obj instanceof Number n) return n.doubleValue();
        return 0.0;
    }

    private double getDoubleOrDefault(Map<String, Object> map, String key, double defaultVal) {
        Object val = map.get(key);
        if (val instanceof Number n) return n.doubleValue();
        return defaultVal;
    }

    /**
     * Seed a single LearnerMetric from AI-evaluated band.
     * Higher confidence (0.5) than self-assessed (0.33) since this is from actual test.
     */
    private void seedCriterionMetricFromAI(Users user, String tagCode, Skill skill, double band, LocalDateTime now) {
        if (band <= 0.0) return; // Skip if AI grading failed for this criterion

        Tag tag = tagRepository.findByCode(tagCode).orElse(null);
        if (tag == null) {
            log.debug("Tag not found for code={}, skipping metric seed", tagCode);
            return;
        }

        LearnerMetric metric = learnerMetricRepository
                .findByUserAndTagAndSkill(user, tag, skill)
                .orElseGet(() -> {
                    LearnerMetric m = new LearnerMetric();
                    m.setUser(user);
                    m.setTag(tag);
                    m.setSkill(skill);
                    m.setPGuess(0.05);
                    m.setPSlip(0.15);
                    m.setPTransit(0.1);
                    return m;
                });

        double mastery = Math.max(0.0, Math.min(1.0, band / 9.0));
        metric.setMasteryLevel(mastery);
        metric.setAttemptCount(metric.getAttemptCount() == null ? 1 : metric.getAttemptCount() + 1);
        metric.setConfidenceScore(0.5); // Higher than self-assess (0.33)
        metric.setUpdatedAt(now);

        learnerMetricRepository.save(metric);
    }

    // =========================================================================
    // SELF-ASSESS SEED (unchanged)
    // =========================================================================

    private void seedWritingSpeakingMetrics(Users user, double writingBand, double speakingBand) {
        LocalDateTime now = LocalDateTime.now();
        seedCriterionMetric(user, "W_TA", Skill.WRITING, writingBand, now);
        seedCriterionMetric(user, "W_CC", Skill.WRITING, writingBand, now);
        seedCriterionMetric(user, "W_LR", Skill.WRITING, writingBand, now);
        seedCriterionMetric(user, "W_GRA", Skill.WRITING, writingBand, now);
        seedCriterionMetric(user, "FC", Skill.SPEAKING, speakingBand, now);
        seedCriterionMetric(user, "LR", Skill.SPEAKING, speakingBand, now);
        seedCriterionMetric(user, "GRA", Skill.SPEAKING, speakingBand, now);
        seedCriterionMetric(user, "PR", Skill.SPEAKING, speakingBand, now);
    }

    private void seedAllSkillMetrics(
            Users user, double readingBand, double listeningBand, double writingBand, double speakingBand) {
        LocalDateTime now = LocalDateTime.now();

        for (String code : List.of(
                "QT_MCQ", "QT_TFNG", "QT_YNNG", "QT_GAP_FILLING", "QT_MATCH_HEAD", "QT_MATCH_INFO", "QT_SHORT_ANS")) {
            seedCriterionMetric(user, code, Skill.READING, readingBand, now);
        }

        for (String code : List.of("QT_MCQ_L", "QT_GAP_FILLING_L", "QT_FORM_COMPLETION", "QT_NOTE_COMPLETION")) {
            seedCriterionMetric(user, code, Skill.LISTENING, listeningBand, now);
        }

        seedWritingSpeakingMetrics(user, writingBand, speakingBand);

        log.info(
                "Seeded ALL LearnerMetrics (self-assess): R={}, L={}, W={}, S={}",
                readingBand,
                listeningBand,
                writingBand,
                speakingBand);
    }

    private void seedCriterionMetric(Users user, String tagCode, Skill skill, double band, LocalDateTime now) {
        Tag tag = tagRepository.findByCode(tagCode).orElse(null);
        if (tag == null) {
            log.debug("Tag not found for code={}, skipping metric seed", tagCode);
            return;
        }

        LearnerMetric metric = learnerMetricRepository
                .findByUserAndTagAndSkill(user, tag, skill)
                .orElseGet(() -> {
                    LearnerMetric m = new LearnerMetric();
                    m.setUser(user);
                    m.setTag(tag);
                    m.setSkill(skill);
                    m.setPGuess(0.05);
                    m.setPSlip(0.15);
                    m.setPTransit(0.1);
                    return m;
                });

        double mastery = Math.max(0.0, Math.min(1.0, band / 9.0));
        metric.setMasteryLevel(mastery);
        metric.setAttemptCount(metric.getAttemptCount() == null ? 1 : metric.getAttemptCount() + 1);
        metric.setConfidenceScore(0.33);
        metric.setUpdatedAt(now);

        learnerMetricRepository.save(metric);
    }

    // =========================================================================
    // GRADING (Reading/Listening MCQ)
    // =========================================================================

    private int gradeAnswers(Users user, Skill skill, List<AnswerRequest> answers) {
        int correctCount = 0;
        LocalDateTime now = LocalDateTime.now();

        for (AnswerRequest ans : answers) {
            Question question = questionRepository
                    .findById(ans.getQuestionId())
                    .orElseThrow(() -> new AppException(ErrorCode.QUESTION_NOT_FOUND));

            QuestionOption correctOption = findCorrectOption(question);
            boolean isCorrect = false;

            if (ans.getSelectedOptionId() != null) {
                var selectedOpt = question.getOptions().stream()
                        .filter(o -> o.getId().equals(ans.getSelectedOptionId()))
                        .findFirst()
                        .orElse(null);
                if (selectedOpt != null && selectedOpt.isCorrect()) {
                    isCorrect = true;
                }
            } else if (ans.getAnswerText() != null && correctOption != null) {
                if (ans.getAnswerText()
                        .trim()
                        .equalsIgnoreCase(
                                correctOption.getContent() != null
                                        ? correctOption.getContent().trim()
                                        : "")) {
                    isCorrect = true;
                }
            }

            if (isCorrect) correctCount++;

            // Bayesian LearnerMetric update
            Set<Tag> questionTags = question.getTags();
            if (questionTags != null && !questionTags.isEmpty()) {
                for (Tag tag : questionTags) {
                    LearnerMetric metric = learnerMetricRepository
                            .findByUserAndTagAndSkill(user, tag, skill)
                            .orElseGet(() -> {
                                LearnerMetric newMetric = new LearnerMetric();
                                newMetric.setUser(user);
                                newMetric.setTag(tag);
                                newMetric.setSkill(skill);
                                newMetric.setMasteryLevel(0.3);
                                newMetric.setConfidenceScore(0.0);
                                newMetric.setAttemptCount(0);
                                newMetric.setPGuess(0.25);
                                newMetric.setPSlip(0.10);
                                newMetric.setPTransit(0.1);
                                return newMetric;
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

                    metric.setMasteryLevel(pLfinal);
                    metric.setAttemptCount(metric.getAttemptCount() == null ? 1 : metric.getAttemptCount() + 1);
                    metric.setConfidenceScore(1.0 - (1.0 / (metric.getAttemptCount() + 1.0)));
                    metric.setUpdatedAt(now);

                    learnerMetricRepository.save(metric);
                }
            }
        }
        return correctCount;
    }

    // =========================================================================
    // HELPERS
    // =========================================================================

    private QuestionOption findCorrectOption(Question question) {
        if (question.getOptions() == null) return null;
        return question.getOptions().stream()
                .filter(QuestionOption::isCorrect)
                .findFirst()
                .orElse(null);
    }

    private void validateBand(Double band) {
        if (!BandScoreUtil.isValidBand(band)) {
            throw new AppException(ErrorCode.PLACEMENT_INVALID_BAND);
        }
    }

    private Users getCurrentUser() {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        String credentialId = null;
        if (authentication.getPrincipal() instanceof Jwt jwt) {
            credentialId = jwt.getClaim("userId");
        }
        if (credentialId == null) {
            throw new AppException(ErrorCode.UNAUTHENTICATED);
        }
        UserCredentials credentials = userCredentialsRepository
                .findById(credentialId)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_EXISTED));
        Users user = credentials.getUser();
        if (user == null) throw new AppException(ErrorCode.USER_NOT_EXISTED);
        return user;
    }

    private PlacementResultResponse toResponse(PlacementResult result) {
        return PlacementResultResponse.builder()
                .id(result.getId())
                .readingBand(result.getReadingBand())
                .listeningBand(result.getListeningBand())
                .writingBand(result.getWritingBand())
                .speakingBand(result.getSpeakingBand())
                .overallBand(result.getOverallBand())
                .targetBand(result.getTargetBand())
                .readingCorrect(result.getReadingCorrect())
                .listeningCorrect(result.getListeningCorrect())
                .isSelfAssessed(result.getIsSelfAssessed())
                .completedAt(result.getCompletedAt())
                .build();
    }

    @SuppressWarnings("unchecked")
    private PlacementResultResponse toResponseWithAIDetails(
            PlacementResult result, Map<String, Object> writingResult, Map<String, Object> speakingResult) {
        PlacementResultResponse response = toResponse(result);

        // Writing criteria
        response.setWritingCriteria(extractWritingCriteriaBands(writingResult));

        // Writing feedback
        try {
            if (writingResult != null && writingResult.containsKey("feedback")) {
                response.setWritingFeedback((Map<String, Object>) writingResult.get("feedback"));
            }
        } catch (Exception e) {
            log.debug("Could not extract writing feedback");
        }

        // Speaking criteria
        response.setSpeakingCriteria(extractSpeakingCriteriaBands(speakingResult));

        // Speaking feedback
        try {
            if (speakingResult != null && speakingResult.containsKey("feedback")) {
                response.setSpeakingFeedback((Map<String, Object>) speakingResult.get("feedback"));
            } else if (speakingResult != null && speakingResult.containsKey("comments")) {
                response.setSpeakingFeedback(Map.of("comments", speakingResult.get("comments")));
            }
        } catch (Exception e) {
            log.debug("Could not extract speaking feedback");
        }

        return response;
    }
}
