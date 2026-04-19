package io.gsp26se16.moni.roadmap.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import io.gsp26se16.moni.authentication.entity.UserCredentials;
import io.gsp26se16.moni.authentication.entity.Users;
import io.gsp26se16.moni.authentication.repository.UserCredentialsRepository;
import io.gsp26se16.moni.common.enumeration.Skill;
import io.gsp26se16.moni.common.exception.AppException;
import io.gsp26se16.moni.common.exception.ErrorCode;
import io.gsp26se16.moni.content.entity.Stimulus;
import io.gsp26se16.moni.content.entity.TestStructure;
import io.gsp26se16.moni.content.repository.StimulusRepository;
import io.gsp26se16.moni.content.repository.TestRepository;
import io.gsp26se16.moni.content.repository.TestStructureRepository;
import io.gsp26se16.moni.placement.entity.PlacementResult;
import io.gsp26se16.moni.placement.repository.PlacementResultRepository;
import io.gsp26se16.moni.roadmap.dto.response.*;
import io.gsp26se16.moni.roadmap.entity.*;
import io.gsp26se16.moni.roadmap.repository.*;
import io.gsp26se16.moni.tag.entity.Tag;
import io.gsp26se16.moni.tag.entity.TagType;
import io.gsp26se16.moni.tag.repository.TagRepository;
import io.gsp26se16.moni.vocab.dto.QuizResponse;
import io.gsp26se16.moni.vocab.dto.VocabResponse;
import io.gsp26se16.moni.vocab.service.VocabLearningService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class WeeklyPlanServiceImpl implements WeeklyPlanService {

    private final WeeklyPlanRepository weeklyPlanRepository;
    private final DailySlotRepository dailySlotRepository;
    private final MonthlyAssessmentRepository monthlyAssessmentRepository;
    private final LearnerMetricRepository learnerMetricRepository;
    private final StimulusRepository stimulusRepository;
    private final TestStructureRepository testStructureRepository;
    private final PlacementResultRepository placementResultRepository;
    private final UserCredentialsRepository userCredentialsRepository;
    private final GoalRepository goalRepository;
    private final TestRepository testRepository;
    private final TagRepository tagRepository;
    private final VocabLearningService vocabLearningService;
    private final io.gsp26se16.moni.vocab.repository.VocabQuizHistoryRepository vocabQuizHistoryRepository;

    @org.springframework.context.annotation.Lazy
    @org.springframework.beans.factory.annotation.Autowired
    private GoalService goalService;

    // =====================================================================
    // PUBLIC API
    // =====================================================================

    @Override
    @Transactional(propagation = Propagation.REQUIRED)
    public void generateWeeklyPlan(Users user) {
        // Find previous plan (if any)
        Optional<WeeklyPlan> previousOpt = weeklyPlanRepository.findTopByUserOrderByWeekNumberDesc(user);
        WeeklyPlan previous = previousOpt.orElse(null);

        // Calculate week metadata
        int weekNumber = previous != null ? previous.getWeekNumber() + 1 : 1;
        int weekInMonth = previous != null ? (previous.getWeekInMonth() % 4) + 1 : 1;
        int monthCycle = previous != null ? previous.getMonthCycle() : 1;

        log.info(
                "[WeeklyPlan] Generating plan for user {}, week {}, month cycle {}",
                user.getId(),
                weekNumber,
                monthCycle);
        if (weekInMonth == 1 && weekNumber > 1) {
            monthCycle = previous.getMonthCycle() + 1;
        }

        // Calculate difficulty
        double difficulty = calculateDifficulty(user, previous);

        // Calculate week dates (next Monday → Sunday)
        LocalDate weekStart = calculateWeekStart();
        LocalDate weekEnd = weekStart.plusDays(6);

        // Create weekly plan
        WeeklyPlan plan = WeeklyPlan.builder()
                .user(user)
                .weekNumber(weekNumber)
                .monthCycle(monthCycle)
                .weekInMonth(weekInMonth)
                .weekStartDate(weekStart)
                .weekEndDate(weekEnd)
                .status("ACTIVE")
                .difficultyLevel(difficulty)
                .createdAt(LocalDateTime.now())
                .build();
        plan = weeklyPlanRepository.save(plan);

        // Get stimulus IDs user has already completed (to avoid repeats)
        Set<Integer> doneStimulusIds = new HashSet<>(dailySlotRepository.findDoneStimulusIdsByUser(user));

        // [EXAM ROADMAP LOGIC] Determine Phase based on daysLeft
        LocalDate examDate = user.getExamDate();
        int daysLeft =
                examDate != null ? (int) java.time.temporal.ChronoUnit.DAYS.between(LocalDate.now(), examDate) : -1;

        int phase = 1; // Default: Foundation (> 60 days)
        if (daysLeft != -1 && daysLeft <= 60 && daysLeft > 30) {
            phase = 2; // Practice Focus
        } else if (daysLeft != -1 && daysLeft <= 30) {
            phase = 3; // Intensive Exam Prep
        }

        log.info("[WeeklyPlan] Generated plan for user {} phase {} with daysLeft {}", user.getId(), phase, daysLeft);

        // Get task distribution for the week (pass phase to adjust volume and weights)
        List<Skill> weekTasks = calculateTaskDistribution(user, previous, phase);

        int totalSlots = weekTasks.size();
        int slotsPerDay = totalSlots / 6; // usually 2, 3 or 1

        List<Skill[]> dailyDistribution = distributeTasksIntoDays(weekTasks, slotsPerDay);

        // Generate practice slots for days 1-6
        for (int day = 1; day <= 6; day++) {
            Skill[] skills = dailyDistribution.get(day - 1);
            LocalDate slotDate = weekStart.plusDays(day - 1);

            for (Skill skill : skills) {
                if (phase == 3) {
                    // [PHASE 3] Intensive mode: assign FULL TEST (mode=FULL_TEST) instead of single
                    // Stimulus
                    io.gsp26se16.moni.content.entity.Test fullTest = selectFullTest(skill, user, doneStimulusIds);

                    DailySlot slot = DailySlot.builder()
                            .weeklyPlan(plan)
                            .dayOfWeek(day)
                            .slotDate(slotDate)
                            .skill(skill)
                            .taskType("FULL_TEST")
                            .stimulus(null) // Full Test has no single stimulus
                            .test(fullTest)
                            .status("TODO")
                            .build();
                    dailySlotRepository.save(slot);

                    if (fullTest != null) {
                        // Keep track so we don't repeat the test; here we use test id + offset for
                        // exclusion if needed
                        doneStimulusIds.add(-fullTest.getId());
                    }
                } else {
                    // [PHASE 1 & 2] Practice mode: assign single Stimulus
                    Stimulus stimulus = selectStimulus(skill, user, difficulty, doneStimulusIds);

                    DailySlot slot = DailySlot.builder()
                            .weeklyPlan(plan)
                            .dayOfWeek(day)
                            .slotDate(slotDate)
                            .skill(skill)
                            .taskType("PRACTICE")
                            .stimulus(stimulus)
                            .test(findTestForStimulus(stimulus))
                            .status("TODO")
                            .build();
                    dailySlotRepository.save(slot);

                    if (stimulus != null) {
                        doneStimulusIds.add(stimulus.getId());
                    }
                }
            }

            // [VOCAB SCHEDULE] Generate vocab tasks only on days 1-4
            // Day 1,3 = VOCAB_LEARN (learn new words)
            // Day 2,4 = VOCAB_TEST (quiz from Sổ biết tuốt + Sổ nhắc lại)
            // Day 5,6 = no vocab (focus on 4 core skills)
            // Phase 3 = no vocab at all
            if (phase != 3 && day <= 4) {
                String vocabTaskType = (day % 2 != 0) ? "VOCAB_LEARN" : "VOCAB_TEST";

                String topicHint = null;
                if ("VOCAB_LEARN".equals(vocabTaskType)) {
                    for (DailySlot s : dailySlotRepository.findByWeeklyPlanOrderByDayOfWeekAscIdAsc(plan)) {
                        if (s.getDayOfWeek().equals(day)
                                && (s.getSkill() == Skill.READING || s.getSkill() == Skill.LISTENING)) {
                            if (s.getStimulus() != null
                                    && s.getStimulus().getTags() != null
                                    && !s.getStimulus().getTags().isEmpty()) {
                                topicHint = s.getStimulus()
                                        .getTags()
                                        .iterator()
                                        .next()
                                        .getName();
                                break;
                            }
                        }
                    }
                }

                DailySlot vocabSlot = DailySlot.builder()
                        .weeklyPlan(plan)
                        .dayOfWeek(day)
                        .slotDate(slotDate)
                        .skill(Skill.VOCABULARY)
                        .taskType(vocabTaskType)
                        .referenceMetadata(topicHint)
                        .status("TODO")
                        .build();
                dailySlotRepository.save(vocabSlot);
            }
        }

        // Generate assessment slots for day 7 (Sunday) — one per skill
        LocalDate day7Date = weekStart.plusDays(6);
        for (Skill skill : Arrays.asList(Skill.READING, Skill.LISTENING, Skill.WRITING, Skill.SPEAKING)) {
            DailySlot assessmentSlot = DailySlot.builder()
                    .weeklyPlan(plan)
                    .dayOfWeek(7)
                    .slotDate(day7Date)
                    .skill(skill)
                    .taskType("ASSESSMENT")
                    .stimulus(null) // Assigned JIT on day 7
                    .test(null)
                    .status("TODO")
                    .build();
            dailySlotRepository.save(assessmentSlot);
        }

        log.info(
                "[WeeklyPlan] Generated week {} (month {}, weekInMonth {}) for user {}. Difficulty: {}",
                weekNumber,
                monthCycle,
                weekInMonth,
                user.getId(),
                difficulty);
    }

    @Override
    @Transactional(readOnly = true)
    public WeeklyPlanDetailResponse getCurrentPlan() {
        Users user = getCurrentUser();
        WeeklyPlan plan = weeklyPlanRepository
                .findFirstByUserAndStatusOrderByWeekNumberDesc(user, "ACTIVE")
                .orElse(null);

        if (plan == null) {
            return null;
        }

        return buildDetailResponse(plan, user);
    }

    @Override
    @Transactional(readOnly = true)
    public WeeklyPlanDetailResponse getPlanByWeek(Integer weekNumber) {
        Users user = getCurrentUser();
        WeeklyPlan plan = weeklyPlanRepository
                .findFirstByUserAndWeekNumber(user, weekNumber)
                .orElse(null);

        if (plan == null) {
            return null;
        }

        return buildDetailResponse(plan, user);
    }

    @Override
    @Transactional(readOnly = true)
    public WeeklyPlanDetailResponse getTodaySlots() {
        Users user = getCurrentUser();
        WeeklyPlan plan = weeklyPlanRepository
                .findFirstByUserAndStatusOrderByWeekNumberDesc(user, "ACTIVE")
                .orElse(null);

        if (plan == null) {
            return null;
        }

        return buildDetailResponse(plan, user);
    }

    @Override
    @Transactional
    public void completeSlot(
            Integer slotId,
            Integer score,
            Integer totalQuestions,
            List<String> correctWords,
            List<String> incorrectWords,
            java.util.Map<String, Object> quizData) {
        Users user = getCurrentUser();

        DailySlot slot =
                dailySlotRepository.findById(slotId).orElseThrow(() -> new AppException(ErrorCode.TASK_NOT_FOUND));

        if (!slot.getWeeklyPlan().getUser().getId().equals(user.getId())) {
            throw new AppException(ErrorCode.UNAUTHORIZED);
        }

        slot.setStatus("DONE");
        slot.setScore(score);
        slot.setTotalQuestions(totalQuestions);
        slot.setCompletedAt(LocalDateTime.now());

        if ("VOCAB_TEST".equals(slot.getTaskType())) {
            // Process notebook transitions:
            // DRAFT + correct → ACTIVE (Sổ biết tuốt → Sổ nhắc lại)
            // ACTIVE + correct → MASTERED (Sổ nhắc lại → Sổ tay Master)
            // incorrect → stay in current notebook
            vocabLearningService.processQuizResults(
                    user,
                    correctWords != null ? correctWords : List.of(),
                    incorrectWords != null ? incorrectWords : List.of());

            if (quizData != null && !quizData.isEmpty()) {
                io.gsp26se16.moni.vocab.entity.VocabQuizHistory history =
                        io.gsp26se16.moni.vocab.entity.VocabQuizHistory.builder()
                                .user(user)
                                .slot(slot)
                                .score(score)
                                .totalQuestions(totalQuestions)
                                .quizData(quizData)
                                .build();
                vocabQuizHistoryRepository.save(history);
            }
        }

        dailySlotRepository.save(slot);

        log.info(
                "[WeeklyPlan] Slot {} completed. Skill: {}, Score: {}/{}",
                slotId,
                slot.getSkill(),
                score,
                totalQuestions);
    }

    @Override
    @Transactional
    public void autoCompleteSlot(Users user, Integer stimulusId, Integer score, Integer totalQuestions) {
        LocalDate today = LocalDate.now();
        List<DailySlot> matchingSlots = dailySlotRepository.findMatchingSlots(user, stimulusId, today);

        // Fallback: search entire active plan if today-specific query finds nothing
        if (matchingSlots.isEmpty()) {
            matchingSlots = dailySlotRepository.findMatchingSlotsInActivePlan(user, stimulusId);
            if (!matchingSlots.isEmpty()) {
                log.info(
                        "[WeeklyPlan Auto] Date-specific match failed, found slot in active plan for stimulus {}",
                        stimulusId);
            }
        }

        if (!matchingSlots.isEmpty()) {
            DailySlot slot = matchingSlots.get(0);
            slot.setStatus("DONE");
            slot.setScore(score);
            slot.setTotalQuestions(totalQuestions);
            slot.setCompletedAt(LocalDateTime.now());
            dailySlotRepository.save(slot);

            log.info(
                    "[WeeklyPlan Auto] Completed slot {} for user {} (stimulus {})",
                    slot.getId(),
                    user.getId(),
                    stimulusId);
        } else {
            log.warn("[WeeklyPlan Auto] No matching TODO slot found for user {} stimulus {}", user.getId(), stimulusId);
        }
    }

    @Override
    @Transactional
    public void autoCompleteTestSlot(Users user, Integer testId, Integer score, Integer totalQuestions) {
        // First try: find TODO slot (not yet completed)
        List<DailySlot> matchingSlots = dailySlotRepository.findMatchingTestSlotsInActivePlan(user, testId);

        if (!matchingSlots.isEmpty()) {
            DailySlot slot = matchingSlots.get(0);
            slot.setStatus("DONE");
            slot.setScore(score);
            slot.setTotalQuestions(totalQuestions);
            slot.setCompletedAt(LocalDateTime.now());
            dailySlotRepository.save(slot);
            log.info("[WeeklyPlan Auto] Completed slot {} for user {} (test {})", slot.getId(), user.getId(), testId);
            return;
        }

        // Fallback: find already-DONE slot to update score (frontend may have marked DONE without score)
        List<DailySlot> doneSlots = dailySlotRepository.findDoneTestSlotsInActivePlan(user, testId);
        if (!doneSlots.isEmpty()) {
            DailySlot slot = doneSlots.get(0);
            slot.setScore(score);
            slot.setTotalQuestions(totalQuestions);
            dailySlotRepository.save(slot);
            log.info("[WeeklyPlan Auto] Updated score for done slot {} (test {})", slot.getId(), testId);
        } else {
            log.warn("[WeeklyPlan Auto] No matching slot found for user {} test {}", user.getId(), testId);
        }
    }

    @Override
    @Transactional
    public List<VocabResponse> startVocabLearning(Integer slotId) {
        Users user = getCurrentUser();
        DailySlot slot =
                dailySlotRepository.findById(slotId).orElseThrow(() -> new AppException(ErrorCode.TASK_NOT_FOUND));

        if (!slot.getWeeklyPlan().getUser().getId().equals(user.getId())) {
            throw new AppException(ErrorCode.UNAUTHORIZED);
        }

        if (!"VOCAB_LEARN".equals(slot.getTaskType())) {
            throw new AppException(ErrorCode.UNCATEGORIZED_EXCEPTION); // Provide proper logic error later
        }

        // Only generate new words if slot isn't already DONE (to avoid re-generating on
        // accidental re-clicks)
        if ("DONE".equals(slot.getStatus())) {
            return List.of(); // Return empty if already complete. Or throw exception based on product rules.
        }

        // Calculate overall band and target CEFR levels (1-2 levels above current)
        double overallBand = getOverallBand(user);
        List<String> targetLevels = getTargetVocabLevels(overallBand);
        String topic = slot.getReferenceMetadata();

        log.info(
                "[VocabLearn] User {} overall band: {}, target levels: {}, topic: {}",
                user.getId(),
                overallBand,
                targetLevels,
                topic);

        return vocabLearningService.generateRoadmapVocabList(user, targetLevels, topic, 15);
    }

    @Override
    @Transactional
    public void submitVocabLearning(Integer slotId, List<Integer> notLearnedIds, List<Integer> learnedIds) {
        Users user = getCurrentUser();
        DailySlot slot =
                dailySlotRepository.findById(slotId).orElseThrow(() -> new AppException(ErrorCode.TASK_NOT_FOUND));

        if (!slot.getWeeklyPlan().getUser().getId().equals(user.getId())) {
            throw new AppException(ErrorCode.UNAUTHORIZED);
        }

        if (!"VOCAB_LEARN".equals(slot.getTaskType())) {
            throw new AppException(ErrorCode.UNCATEGORIZED_EXCEPTION);
        }

        vocabLearningService.submitRoadmapVocabList(user, notLearnedIds, learnedIds);

        slot.setStatus("DONE");
        slot.setCompletedAt(LocalDateTime.now());
        dailySlotRepository.save(slot);
    }

    @Override
    @Transactional(readOnly = true)
    public QuizResponse getVocabQuiz(Integer slotId) {
        Users user = getCurrentUser();
        DailySlot slot =
                dailySlotRepository.findById(slotId).orElseThrow(() -> new AppException(ErrorCode.TASK_NOT_FOUND));

        if (!slot.getWeeklyPlan().getUser().getId().equals(user.getId())) {
            throw new AppException(ErrorCode.UNAUTHORIZED);
        }

        if (!"VOCAB_TEST".equals(slot.getTaskType())) {
            throw new AppException(ErrorCode.UNCATEGORIZED_EXCEPTION);
        }

        if ("DONE".equals(slot.getStatus())) {
            return vocabQuizHistoryRepository
                    .findTopBySlotAndUserOrderByCreatedAtDesc(slot, user)
                    .map(history -> {
                        try {
                            com.fasterxml.jackson.databind.ObjectMapper mapper =
                                    new com.fasterxml.jackson.databind.ObjectMapper();
                            QuizResponse response = mapper.convertValue(history.getQuizData(), QuizResponse.class);
                            response.setIsHistory(true);
                            response.setScore(history.getScore());
                            return response;
                        } catch (Exception e) {
                            return QuizResponse.builder()
                                    .questions(List.of())
                                    .isHistory(true)
                                    .build();
                        }
                    })
                    .orElseGet(() -> QuizResponse.builder()
                            .questions(List.of())
                            .isHistory(true)
                            .build());
        }

        return vocabLearningService.generateRoadmapQuiz(user);
    }

    private String formatBandRange(double band) {
        if (band < 4.5) return "A1"; // Beginner / Elementary
        if (band < 5.5) return "A2"; // Pre-Intermediate
        if (band < 6.5) return "B1"; // Intermediate
        if (band < 7.5) return "B2"; // Upper Intermediate
        if (band >= 8.5) return "C2"; // Master
        return "C1"; // 7.5 - 8.0 Advanced
    }

    /**
     * Given the user's overall IELTS band, return target CEFR levels
     * 1-2 levels ABOVE their current level for vocabulary improvement.
     */
    private List<String> getTargetVocabLevels(double band) {
        if (band < 4.5) return List.of("A2", "B1"); // A1 → learn A2, B1
        if (band < 5.5) return List.of("B1", "B2"); // A2 → learn B1, B2
        if (band < 6.5) return List.of("B2", "C1"); // B1 → learn B2, C1
        if (band < 7.5) return List.of("C1", "C2"); // B2 → learn C1, C2
        if (band < 8.5) return List.of("C2"); // C1 → learn C2
        return List.of("C2"); // C2 → stay C2 (max)
    }

    /**
     * Get the user's overall IELTS band for CEFR level calculation.
     * Priority: week assessment → placement test → fallback
     */
    private double getOverallBand(Users user) {
        // Priority 1: Check latest completed weekly plan assessment (day 7 scores)
        Optional<WeeklyPlan> latestCompleted = weeklyPlanRepository.findTopByUserOrderByWeekNumberDesc(user);
        if (latestCompleted.isPresent()) {
            WeeklyPlan plan = latestCompleted.get();
            if (plan.getWeeklyAccuracy() != null && plan.getWeeklyAccuracy() > 0) {
                // Convert accuracy to approximate band
                return convertAccuracyToBand(plan.getWeeklyAccuracy());
            }
        }

        // Priority 2: Placement test overall band
        Optional<PlacementResult> placement = placementResultRepository.findFirstByUserOrderByCompletedAtDesc(user);
        if (placement.isPresent() && placement.get().getOverallBand() != null) {
            return placement.get().getOverallBand();
        }

        // Priority 3: Fallback
        return 5.0;
    }

    @Override
    @Transactional
    public WeeklyPlanDetailResponse evaluateWeekAndGenerateNext() {
        Users user = getCurrentUser();
        WeeklyPlan currentPlan = weeklyPlanRepository
                .findFirstByUserAndStatusOrderByWeekNumberDesc(user, "ACTIVE")
                .orElseThrow(() -> new AppException(ErrorCode.ACTIVE_ROADMAP_NOT_FOUND));

        // Calculate metrics
        List<DailySlot> allSlots = dailySlotRepository.findByWeeklyPlanOrderByDayOfWeekAscIdAsc(currentPlan);
        long totalSlots = allSlots.size();
        long doneSlots =
                allSlots.stream().filter(s -> "DONE".equals(s.getStatus())).count();

        double completionRate = totalSlots > 0 ? (double) doneSlots / totalSlots : 0.0;

        // Calculate accuracy only for DONE slots
        double weeklyAccuracy = allSlots.stream()
                .filter(s -> "DONE".equals(s.getStatus()) && s.getScore() != null && s.getTotalQuestions() != null)
                .mapToDouble(s -> {
                    // For Writing/Speaking, score is band (out of 9)
                    if (s.getSkill() == Skill.WRITING || s.getSkill() == Skill.SPEAKING) {
                        return s.getScore() / 9.0;
                    }
                    // For Reading/Listening, score is correct answers
                    return s.getTotalQuestions() > 0 ? (double) s.getScore() / s.getTotalQuestions() : 0.0;
                })
                .average()
                .orElse(0.0);

        // Determine verdict by comparing with previous week
        String verdict = determineVerdict(user, weeklyAccuracy, completionRate);

        currentPlan.setWeeklyAccuracy(weeklyAccuracy);
        currentPlan.setCompletionRate(completionRate);
        currentPlan.setPerformanceVerdict(verdict);
        currentPlan.setStatus("COMPLETED");
        weeklyPlanRepository.save(currentPlan);

        // [NEW] Snapshot the personal insight metrics at the end of the week
        try {
            goalService.snapshotInsightsForWeek(user, currentPlan.getWeekNumber());
        } catch (Exception e) {
            log.error("Failed to snapshot insights for week {}: {}", currentPlan.getWeekNumber(), e.getMessage());
        }

        log.info(
                "[WeeklyPlan] Week {} evaluated. Accuracy: {}, Completion: {}, Verdict: {}",
                currentPlan.getWeekNumber(),
                weeklyAccuracy,
                completionRate,
                verdict);

        // Check if monthly assessment is needed (every 4 weeks)
        if (currentPlan.getWeekInMonth() == 4) {
            triggerMonthlyAssessment(user, currentPlan.getMonthCycle());
        }

        // Generate next week
        generateWeeklyPlan(user);

        // Return the new plan
        WeeklyPlan newPlan = weeklyPlanRepository
                .findFirstByUserAndStatusOrderByWeekNumberDesc(user, "ACTIVE")
                .orElse(null);
        return newPlan != null ? buildDetailResponse(newPlan, user) : null;
    }

    @Override
    @Transactional(readOnly = true)
    public List<WeeklyPlanSummaryResponse> getHistory() {
        Users user = getCurrentUser();
        List<WeeklyPlan> plans = weeklyPlanRepository.findByUserOrderByWeekNumberDesc(user);

        return plans.stream()
                .filter(p -> "COMPLETED".equals(p.getStatus()))
                .map(p -> WeeklyPlanSummaryResponse.builder()
                        .weekNumber(p.getWeekNumber())
                        .monthCycle(p.getMonthCycle())
                        .weekInMonth(p.getWeekInMonth())
                        .weekStartDate(p.getWeekStartDate().toString())
                        .weekEndDate(p.getWeekEndDate().toString())
                        .weeklyAccuracy(p.getWeeklyAccuracy())
                        .completionRate(p.getCompletionRate())
                        .performanceVerdict(p.getPerformanceVerdict())
                        .build())
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public MonthlyAssessmentResponse getPendingMonthlyAssessment() {
        Users user = getCurrentUser();
        return monthlyAssessmentRepository
                .findTopByUserAndStatusOrderByIdDesc(user, "PENDING")
                .map(ma -> MonthlyAssessmentResponse.builder()
                        .id(ma.getId())
                        .monthCycle(ma.getMonthCycle())
                        .fullTestId(ma.getFullTest() != null ? ma.getFullTest().getId() : null)
                        .status(ma.getStatus())
                        .readingBand(ma.getReadingBand())
                        .listeningBand(ma.getListeningBand())
                        .writingBand(ma.getWritingBand())
                        .speakingBand(ma.getSpeakingBand())
                        .overallBand(ma.getOverallBand())
                        .build())
                .orElse(null);
    }

    // =====================================================================
    // PRIVATE HELPERS — Stimulus Selection
    // =====================================================================

    private Stimulus selectStimulus(Skill skill, Users user, double targetDifficulty, Set<Integer> excludeIds) {
        List<LearnerMetric> metrics = learnerMetricRepository.findByUser(user);

        io.gsp26se16.moni.tag.entity.TagType structType = getStructType(skill);
        List<Tag> weakStructs = structType != null ? getWeakTags(metrics, structType, 2) : Collections.emptyList();

        io.gsp26se16.moni.tag.entity.TagType subType = getSubType(skill);
        List<Tag> weakSubTypes = subType != null ? getWeakTags(metrics, subType, 5) : Collections.emptyList();

        List<Stimulus> candidates = stimulusRepository.findBySkillAndStatus(
                skill, io.gsp26se16.moni.common.enumeration.PublishStatus.PUBLISHED);

        if (candidates.isEmpty()) {
            return null;
        }

        List<Stimulus> unseen =
                candidates.stream().filter(s -> !excludeIds.contains(s.getId())).toList();
        List<Stimulus> pool = unseen.isEmpty() ? candidates : unseen;

        return pool.stream()
                .min(Comparator.comparingDouble(s -> scoreStimulusV4(s, targetDifficulty, weakStructs, weakSubTypes)))
                .orElse(pool.get(0));
    }

    private Stimulus selectAssessmentStimulus(Skill skill, Users user, Set<Integer> excludeIds) {
        List<LearnerMetric> metrics = learnerMetricRepository.findByUser(user);

        io.gsp26se16.moni.tag.entity.TagType structType = getStructType(skill);
        List<Tag> weakStructs = structType != null ? getWeakTags(metrics, structType, 2) : Collections.emptyList();

        io.gsp26se16.moni.tag.entity.TagType subType = getSubType(skill);
        List<Tag> weakSubTypes = subType != null ? getWeakTags(metrics, subType, 3) : Collections.emptyList();

        List<Stimulus> candidates = stimulusRepository.findBySkillAndStatus(
                skill, io.gsp26se16.moni.common.enumeration.PublishStatus.PUBLISHED);

        if (candidates.isEmpty()) {
            return null;
        }

        List<Stimulus> unseen =
                candidates.stream().filter(s -> !excludeIds.contains(s.getId())).toList();
        List<Stimulus> pool = unseen.isEmpty() ? candidates : unseen;

        double baseDifficulty = getTargetBandFromUser(user, skill) / 9.0;
        final double targetDifficulty = Math.max(0.2, Math.min(0.9, baseDifficulty));

        return pool.stream()
                .min(Comparator.comparingDouble(s -> scoreStimulusV4(s, targetDifficulty, weakStructs, weakSubTypes)))
                .orElse(pool.get(0));
    }

    // =====================================================================
    // PRIVATE HELPERS — Difficulty & Evaluation
    // =====================================================================

    private double calculateDifficulty(Users user, WeeklyPlan previous) {
        if (previous == null) {
            // First week: estimate from placement
            PlacementResult placement = placementResultRepository
                    .findFirstByUserOrderByCompletedAtDesc(user)
                    .orElse(null);
            if (placement != null && placement.getOverallBand() != null) {
                return Math.max(0.1, Math.min(0.95, placement.getOverallBand() / 9.0));
            }
            return 0.5; // Default
        }

        double prevDifficulty = previous.getDifficultyLevel() != null ? previous.getDifficultyLevel() : 0.5;
        String verdict = previous.getPerformanceVerdict();

        if ("IMPROVED".equals(verdict)) {
            return Math.min(0.95, prevDifficulty + 0.05);
        } else if ("DECLINED".equals(verdict)) {
            return Math.max(0.1, prevDifficulty - 0.05);
        }
        return prevDifficulty; // STABLE
    }

    // =====================================================================
    // PRIVATE HELPERS — Task Distribution Logic
    // =====================================================================

    private List<Skill> calculateTaskDistribution(Users user, WeeklyPlan previousPlan, int phase) {
        Map<Skill, Double> targetBands = new HashMap<>();
        List<Goal> activeGoals = goalRepository.findAllByUserAndStatus(user, "ACTIVE");
        for (Goal goal : activeGoals) {
            targetBands.put(goal.getSkill(), goal.getTargetBand() != null ? goal.getTargetBand() : 0.0);
        }

        Map<Skill, Double> gaps = new HashMap<>();
        double totalGap = 0.0;

        List<Skill> coreSkills = Arrays.asList(Skill.READING, Skill.LISTENING, Skill.WRITING, Skill.SPEAKING);

        for (Skill skill : coreSkills) {
            double target = targetBands.getOrDefault(skill, 0.0);
            if (target == 0.0) {
                target = getTargetBandFromUser(user, skill);
                if (target == 0.0) {
                    Goal skillGoal = activeGoals.stream()
                            .filter(g -> g.getSkill() == skill)
                            .findFirst()
                            .orElse(null);
                    target = (skillGoal != null && skillGoal.getStartingBand() != null)
                            ? skillGoal.getStartingBand() + 1.0
                            : 6.0;
                }
            }

            double current = getCurrentBandForSkill(user, skill, previousPlan);
            double gap = Math.max(0.0, target - current);

            // [PHASE 2 LOGIC]: Priority multiplier for Reading & Listening
            if (phase == 2 && (skill == Skill.READING || skill == Skill.LISTENING)) {
                gap *= 1.5;
            }
            // Increase gap slightly so 0-gap skills still get some practice
            gap += 0.5;

            gaps.put(skill, gap);
            totalGap += gap;
            log.info(
                    "[WeeklyPlan] Skill: {}, Target: {}, Current: {}, Calculated Gap (with weight): {}",
                    skill,
                    target,
                    current,
                    gap);
        }

        log.info("[WeeklyPlan] Total Gap: {}, Distribution Logic starting...", totalGap);

        List<Skill> taskPool = new ArrayList<>();
        int TARGET_TOTAL_SLOTS = (phase == 1) ? 12 : (phase == 2) ? 18 : 6;

        if (totalGap == 0.0) {
            // Default flat distribution
            int perSkill = TARGET_TOTAL_SLOTS / 4;
            int remainder = TARGET_TOTAL_SLOTS % 4;
            for (Skill skill : coreSkills) {
                int base = perSkill + (remainder-- > 0 ? 1 : 0);
                for (int i = 0; i < base; i++) taskPool.add(skill);
            }
            return taskPool;
        }

        // Largest Remainder Method
        Map<Skill, Integer> assignedTasks = new HashMap<>();
        Map<Skill, Double> remainders = new HashMap<>();
        int totalAssigned = 0;

        for (Skill skill : coreSkills) {
            double rawTasks = TARGET_TOTAL_SLOTS * (gaps.get(skill) / totalGap);
            int floorTasks = (int) Math.floor(rawTasks);
            assignedTasks.put(skill, floorTasks);
            totalAssigned += floorTasks;
            remainders.put(skill, rawTasks - floorTasks);
        }

        int remainingSlots = TARGET_TOTAL_SLOTS - totalAssigned;
        List<Skill> sortedByRemainder = coreSkills.stream()
                .sorted((s1, s2) -> Double.compare(remainders.get(s2), remainders.get(s1)))
                .toList();

        for (int i = 0; i < remainingSlots; i++) {
            Skill skill = sortedByRemainder.get(i);
            assignedTasks.put(skill, assignedTasks.get(skill) + 1);
        }

        for (Skill skill : coreSkills) {
            int count = assignedTasks.get(skill);
            for (int i = 0; i < count; i++) {
                taskPool.add(skill);
            }
        }

        return taskPool;
    }

    @Override
    public double getCurrentBandForSkill(Users user, Skill skill, WeeklyPlan activePlan) {
        // Priority 1: Find the latest COMPLETED MonthlyAssessment
        Optional<MonthlyAssessment> latestMonthlyAss =
                monthlyAssessmentRepository.findTopByUserAndStatusOrderByCompletedAtDesc(user, "COMPLETED");

        // Priority 2: Find the latest DONE weekly assessment slot
        Optional<DailySlot> latestWeeklyAss =
                dailySlotRepository.findFirstByWeeklyPlanUserAndSkillAndTaskTypeAndStatusOrderByCompletedAtDesc(
                        user, skill, "ASSESSMENT", "DONE");

        // Compare which one is more recent if both exist
        if (latestMonthlyAss.isPresent() && latestWeeklyAss.isPresent()) {
            LocalDateTime maTime = latestMonthlyAss.get().getCompletedAt();
            LocalDateTime waTime = latestWeeklyAss.get().getCompletedAt();
            if (waTime != null && maTime != null && waTime.isAfter(maTime)) {
                return calculateBandFromSlot(latestWeeklyAss.get());
            } else {
                Double mBand = getBandFromMonthlyAssessment(latestMonthlyAss.get(), skill);
                if (mBand != null) return mBand;
            }
        } else if (latestWeeklyAss.isPresent()) {
            return calculateBandFromSlot(latestWeeklyAss.get());
        } else if (latestMonthlyAss.isPresent()) {
            Double mBand = getBandFromMonthlyAssessment(latestMonthlyAss.get(), skill);
            if (mBand != null) return mBand;
        }

        // Priority 3: Placement Result
        Optional<PlacementResult> placement = placementResultRepository.findFirstByUserOrderByCompletedAtDesc(user);
        if (placement.isPresent()) {
            Double band = getBandFromPlacementResult(placement.get(), skill);
            if (band != null) return band;
        }

        // Priority 4: Goal starting band
        Optional<Goal> goal = goalRepository.findTopByUserAndSkillAndStatusOrderByIdDesc(user, skill, "ACTIVE");
        if (goal.isPresent() && goal.get().getStartingBand() != null) {
            return goal.get().getStartingBand();
        }

        return 4.0; // Ultimate fallback
    }

    private double getTargetBandFromUser(Users user, Skill skill) {
        Double target =
                switch (skill) {
                    case READING -> user.getTargetReading();
                    case LISTENING -> user.getTargetListening();
                    case WRITING -> user.getTargetWriting();
                    case SPEAKING -> user.getTargetSpeaking();
                    default -> 6.5; // VOCABULARY or others
                };
        return target != null ? target : 0.0;
    }

    private Double getBandFromMonthlyAssessment(MonthlyAssessment asm, Skill skill) {
        return switch (skill) {
            case READING -> asm.getReadingBand();
            case LISTENING -> asm.getListeningBand();
            case WRITING -> asm.getWritingBand();
            case SPEAKING -> asm.getSpeakingBand();
            default -> null; // VOCABULARY has no distinct band in asm yet
        };
    }

    private Double getBandFromPlacementResult(PlacementResult pr, Skill skill) {
        return switch (skill) {
            case READING -> pr.getReadingBand();
            case LISTENING -> pr.getListeningBand();
            case WRITING -> pr.getWritingBand();
            case SPEAKING -> pr.getSpeakingBand();
            default -> null;
        };
    }

    private double calculateBandFromSlot(DailySlot slot) {
        if (slot.getScore() == null) return 0.0;
        int score = slot.getScore();
        int total = slot.getTotalQuestions() != null ? slot.getTotalQuestions() : 0;

        if (slot.getSkill() == Skill.WRITING) {
            // Writing stores band*10 in score, totalQuestions=90
            if (total == 90) return score / 10.0;
            // Legacy: raw band in score, totalQuestions=9
            if (total == 9) return (double) score;
            return score > 0 ? (double) score : 0.0;
        }
        if (slot.getSkill() == Skill.SPEAKING) {
            // Speaking stores raw band in score, totalQuestions=9
            if (total == 9) return (double) score;
            return score > 0 ? (double) score : 0.0;
        }

        // Reading/Listening: convert raw score to IELTS band using official scale
        if (total == 0) return 0.0;
        return convertRawScoreToIeltsBand(score, total, slot.getSkill());
    }

    private static final double[] READING_BANDS = {
        0, 0, 0, 0, 0, 0, 2.5, 3, 3, 3.5, 3.5, 4, 4, 4, 4.5, 4.5,
        5, 5, 5, 5.5, 5.5, 5.5, 6, 6, 6.5, 6.5, 6.5, 7, 7, 7, 7.5, 7.5,
        8, 8, 8.5, 8.5, 9, 9, 9, 9, 9
    };

    private static final double[] LISTENING_BANDS = {
        0, 0, 0, 0, 0, 0, 2.5, 3, 3, 3.5, 3.5, 4, 4, 4, 4.5, 4.5, 5, 5, 5, 5.5, 5.5, 5.5, 6, 6, 6, 6.5, 6.5, 7, 7, 7.5,
        7.5, 8, 8, 8, 8.5, 8.5, 9, 9, 9, 9, 9
    };

    private double convertRawScoreToIeltsBand(int score, int total, Skill skill) {
        // Scale to 40-question equivalent
        int scaledTo40 = (int) Math.round((double) score / total * 40);
        scaledTo40 = Math.max(0, Math.min(scaledTo40, 40));
        double[] bands = (skill == Skill.LISTENING) ? LISTENING_BANDS : READING_BANDS;
        return bands[scaledTo40];
    }

    private List<Skill[]> distributeTasksIntoDays(List<Skill> taskPool, int slotsPerDay) {
        List<Skill> shuffled = new ArrayList<>(taskPool);
        Collections.shuffle(shuffled);

        if (slotsPerDay > 1) {
            // Simple attempt to prevent same skill consecutively
            for (int i = 0; i < shuffled.size() - 1; i++) {
                if (shuffled.get(i) == shuffled.get(i + 1)) {
                    for (int j = i + 2; j < shuffled.size(); j++) {
                        if (shuffled.get(j) != shuffled.get(i)) {
                            Collections.swap(shuffled, i + 1, j);
                            break;
                        }
                    }
                }
            }
        }

        List<Skill[]> days = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            Skill[] daySkills = new Skill[slotsPerDay];
            for (int j = 0; j < slotsPerDay; j++) {
                int index = i * slotsPerDay + j;
                if (index < shuffled.size()) {
                    daySkills[j] = shuffled.get(index);
                } else {
                    daySkills[j] = Skill.READING; // fallback
                }
            }
            days.add(daySkills);
        }
        return days;
    }

    private io.gsp26se16.moni.content.entity.Test selectFullTest(Skill skill, Users user, Set<Integer> excludeIds) {
        // Here we ideally search for FULL_TEST with exclude filtering,
        // but testRepository doesn't natively take excludeIds in the random query yet.
        // We will just fetch random. If we had more time we would write a native query.
        return testRepository.findRandomPublishedFullTest(skill.name()).orElse(null);
    }

    private String determineVerdict(Users user, double currentAccuracy, double completionRate) {
        // Find previous completed plan
        List<WeeklyPlan> recentPlans = weeklyPlanRepository.findTop4ByUserOrderByWeekNumberDesc(user);
        WeeklyPlan previousCompleted = recentPlans.stream()
                .filter(p -> "COMPLETED".equals(p.getStatus()))
                .skip(0) // Current plan is still ACTIVE at query time, so first COMPLETED is actual
                // previous
                .findFirst()
                .orElse(null);

        // Factor in incomplete tasks: if completion < 50%, bias toward DECLINED
        if (completionRate < 0.5) {
            return "DECLINED";
        }

        if (previousCompleted == null || previousCompleted.getWeeklyAccuracy() == null) {
            // No baseline to compare — use absolute threshold
            return currentAccuracy >= 0.7 ? "IMPROVED" : "STABLE";
        }

        double diff = currentAccuracy - previousCompleted.getWeeklyAccuracy();

        if (diff >= 0.05) return "IMPROVED";
        if (diff <= -0.05) return "DECLINED";
        return "STABLE";
    }

    private void triggerMonthlyAssessment(Users user, int monthCycle) {
        // Check if already exists
        Optional<MonthlyAssessment> existing =
                monthlyAssessmentRepository.findTopByUserAndStatusOrderByIdDesc(user, "PENDING");
        if (existing.isPresent()) {
            log.info("[MonthlyAssessment] Already pending for user {}", user.getId());
            return;
        }

        // For now, create assessment without full test (test will be assigned later via
        // admin or auto-generation)
        MonthlyAssessment assessment = MonthlyAssessment.builder()
                .user(user)
                .monthCycle(monthCycle)
                .status("PENDING")
                .createdAt(LocalDateTime.now())
                .build();
        monthlyAssessmentRepository.save(assessment);

        log.info("[MonthlyAssessment] Created monthly assessment for user {}, cycle {}", user.getId(), monthCycle);
    }

    // =====================================================================
    // PRIVATE HELPERS — Utilities
    // =====================================================================

    private io.gsp26se16.moni.tag.entity.TagType getStructType(Skill skill) {
        if (skill == Skill.READING) return io.gsp26se16.moni.tag.entity.TagType.PASSAGE;
        if (skill == Skill.LISTENING) return io.gsp26se16.moni.tag.entity.TagType.SECTION;
        if (skill == Skill.WRITING) return io.gsp26se16.moni.tag.entity.TagType.TASK;
        if (skill == Skill.SPEAKING) return io.gsp26se16.moni.tag.entity.TagType.PART;
        return null;
    }

    private io.gsp26se16.moni.tag.entity.TagType getSubType(Skill skill) {
        if (skill == Skill.SPEAKING) return TagType.TOPIC;
        if (skill == Skill.WRITING) return TagType.WRITING_TYPE;
        return io.gsp26se16.moni.tag.entity.TagType.QUESTION_TYPE;
    }

    private List<Tag> getWeakTags(List<LearnerMetric> metrics, TagType type, int limit) {
        // Find all tags of this type in system
        List<Tag> allTagsOfType = tagRepository.findByType(type);

        // Identify which ones the user hasn't attempted yet
        Set<Integer> attemptedTagIds =
                metrics.stream().map(m -> m.getTag().getId()).collect(java.util.stream.Collectors.toSet());

        List<Tag> unattemptedTags = allTagsOfType.stream()
                .filter(t -> !attemptedTagIds.contains(t.getId()))
                .toList();

        // Get known weak tags
        List<Tag> knownWeakTags = metrics.stream()
                .filter(m -> m.getTag().getType() == type)
                .sorted(Comparator.comparingDouble(this::weakAreaScore))
                .map(LearnerMetric::getTag)
                .toList();

        // Combine: Prioritize unattempted so they get tested and gather data!
        List<Tag> result = new ArrayList<>(unattemptedTags);
        result.addAll(knownWeakTags);

        return result.stream().limit(limit).toList();
    }

    private double scoreStimulusV4(Stimulus s, double targetDifficulty, List<Tag> weakStructs, List<Tag> weakSubTypes) {
        double difficultyPenalty = Math.abs(estimateDifficulty(s) - targetDifficulty);

        double structBonus = 0.0;
        if (weakStructs != null && !weakStructs.isEmpty() && s.getTags() != null) {
            boolean hasWeakStruct = s.getTags().stream().anyMatch(t -> weakStructs.stream()
                    .anyMatch(wt -> wt.getId().equals(t.getId())));
            structBonus = hasWeakStruct ? 0.5 : 0.0;
        }

        double subTypeBonus = 0.0;
        if (weakSubTypes != null && !weakSubTypes.isEmpty()) {
            Set<Integer> sTags = new HashSet<>();
            if (s.getTags() != null) {
                s.getTags().forEach(t -> sTags.add(t.getId()));
            }
            if (s.getQuestionGroups() != null) {
                s.getQuestionGroups().forEach(qg -> qg.getQuestions().forEach(q -> {
                    if (q.getTags() != null) q.getTags().forEach(t -> sTags.add(t.getId()));
                }));
            }
            long matchCount = weakSubTypes.stream()
                    .filter(wt -> sTags.contains(wt.getId()))
                    .count();
            subTypeBonus = Math.min(0.5, (double) matchCount / weakSubTypes.size() * 0.5);
        }

        return 1.0 + (difficultyPenalty * 0.5) - structBonus - subTypeBonus;
    }

    private double weakAreaScore(LearnerMetric metric) {
        double mastery = metric.getMasteryLevel() != null ? metric.getMasteryLevel() : 0.5;
        double confidence = metric.getConfidenceScore() != null ? metric.getConfidenceScore() : 0.0;
        return mastery + ((1.0 - confidence) * 0.5);
    }

    private double estimateDifficulty(Stimulus stimulus) {
        // 1. Try to get difficulty from Stimulus directly
        if (stimulus.getTags() != null) {
            Optional<Tag> diffTag = stimulus.getTags().stream()
                    .filter(t -> t.getType() == io.gsp26se16.moni.tag.entity.TagType.DIFFICULTY)
                    .findFirst();
            if (diffTag.isPresent()) {
                String code = diffTag.get().getCode();
                if ("DIF_HARD".equals(code)) return 0.75;
                if ("DIF_MEDIUM".equals(code)) return 0.55;
                if ("DIF_EASY".equals(code)) return 0.35;
            }
        }

        // 2. Fallback to average of Question tags
        if (stimulus.getQuestionGroups() == null || stimulus.getQuestionGroups().isEmpty()) {
            return 0.5;
        }
        return stimulus.getQuestionGroups().stream()
                .flatMap(qg -> qg.getQuestions().stream())
                .mapToDouble(q -> {
                    if (q.getTags() == null || q.getTags().isEmpty()) return 0.5;
                    return q.getTags().stream()
                            .mapToDouble(tag -> {
                                String code = tag.getCode() != null ? tag.getCode() : "";
                                String name =
                                        tag.getName() != null ? tag.getName().toUpperCase() : "";

                                // Strict code matching
                                if ("DIF_HARD".equals(code)) return 0.75;
                                if ("DIF_MEDIUM".equals(code)) return 0.55;
                                if ("DIF_EASY".equals(code)) return 0.35;

                                // Legacy text matching as fallback
                                if (name.contains("HARD") || name.contains("KHÓ")) return 0.75;
                                if (name.contains("MEDIUM") || name.contains("TRUNG BÌNH")) return 0.55;
                                if (name.contains("EASY") || name.contains("DỄ")) return 0.35;

                                // Legacy band matching
                                if (name.contains("BAND_8") || name.contains("8")) return 0.8;
                                if (name.contains("BAND_7") || name.contains("7")) return 0.7;
                                if (name.contains("BAND_6") || name.contains("6")) return 0.6;
                                if (name.contains("BAND_5") || name.contains("5")) return 0.5;

                                return 0.5;
                            })
                            .average()
                            .orElse(0.5);
                })
                .average()
                .orElse(0.5);
    }

    private io.gsp26se16.moni.content.entity.Test findTestForStimulus(Stimulus stimulus) {
        if (stimulus == null) return null;
        List<TestStructure> structures = testStructureRepository.findByStimulusId(stimulus.getId());
        if (!structures.isEmpty()) {
            return structures.get(0).getTest();
        }
        return null;
    }

    private LocalDate calculateWeekStart() {
        // Plan bắt đầu ngay từ hôm nay, không cần đợi đến thứ Hai
        return LocalDate.now();
    }

    private WeeklyPlanDetailResponse buildDetailResponse(WeeklyPlan plan, Users user) {
        List<DailySlot> allSlots = dailySlotRepository.findByWeeklyPlanOrderByDayOfWeekAscIdAsc(plan);

        LocalDate today = LocalDate.now();
        List<DailySlot> todaySlots =
                allSlots.stream().filter(s -> s.getSlotDate().equals(today)).toList();

        boolean todayCompleted =
                !todaySlots.isEmpty() && todaySlots.stream().allMatch(s -> "DONE".equals(s.getStatus()));
        boolean suggestVocabulary = todayCompleted;

        // Check for pending monthly assessment
        boolean monthlyPending = monthlyAssessmentRepository
                .findTopByUserAndStatusOrderByIdDesc(user, "PENDING")
                .isPresent();

        // Get previous verdict
        String previousVerdict = null;
        List<WeeklyPlan> recent = weeklyPlanRepository.findTop4ByUserOrderByWeekNumberDesc(user);
        for (WeeklyPlan p : recent) {
            if ("COMPLETED".equals(p.getStatus()) && p.getPerformanceVerdict() != null) {
                previousVerdict = p.getPerformanceVerdict();
                break;
            }
        }

        List<DailySlotResponse> slotResponses = allSlots.stream()
                .map(s -> {
                    // JIT Assessment assignment if it's Day 7 and today >= slotDate
                    if (s.getDayOfWeek() == 7
                            && !s.getSlotDate().isAfter(today)
                            && s.getStimulus() == null
                            && "ASSESSMENT".equals(s.getTaskType())) {
                        s = assignAssessmentIfPending(s, user);
                    }

                    Integer totalQ = s.getTotalQuestions();
                    if (totalQ == null && s.getStimulus() != null) {
                        totalQ = calculateStimulusQuestions(s.getStimulus());
                    }

                    return DailySlotResponse.builder()
                            .id(s.getId())
                            .dayOfWeek(s.getDayOfWeek())
                            .slotDate(s.getSlotDate().toString())
                            .skill(s.getSkill().name())
                            .taskType(s.getTaskType())
                            .stimulusId(
                                    s.getStimulus() != null ? s.getStimulus().getId() : null)
                            .stimulusTitle(
                                    s.getTest() != null
                                                    && s.getTest().getTitle() != null
                                                    && !s.getTest().getTitle().isBlank()
                                            ? s.getTest().getTitle()
                                            : (s.getStimulus() != null
                                                    ? s.getStimulus().getTitle()
                                                    : null))
                            .testId(s.getTest() != null ? s.getTest().getId() : null)
                            .status(s.getStatus())
                            .score(s.getScore())
                            .totalQuestions(totalQ)
                            .build();
                })
                .toList();

        return WeeklyPlanDetailResponse.builder()
                .id(plan.getId())
                .weekNumber(plan.getWeekNumber())
                .monthCycle(plan.getMonthCycle())
                .weekInMonth(plan.getWeekInMonth())
                .weekStartDate(plan.getWeekStartDate().toString())
                .weekEndDate(plan.getWeekEndDate().toString())
                .status(plan.getStatus())
                .difficultyLevel(plan.getDifficultyLevel())
                .weeklyAccuracy(plan.getWeeklyAccuracy())
                .completionRate(plan.getCompletionRate())
                .performanceVerdict(plan.getPerformanceVerdict())
                .previousVerdict(previousVerdict)
                .slots(slotResponses)
                .todayCompleted(todayCompleted)
                .suggestVocabulary(suggestVocabulary)
                .monthlyAssessmentPending(monthlyPending)
                .build();
    }

    private DailySlot assignAssessmentIfPending(DailySlot s, Users user) {
        if (s.getStimulus() == null && s.getTest() == null && "TODO".equals(s.getStatus())) {
            Set<Integer> doneStimulusIds = new HashSet<>(dailySlotRepository.findDoneStimulusIdsByUser(user));
            Stimulus assessmentStimulus = selectAssessmentStimulus(s.getSkill(), user, doneStimulusIds);

            s.setStimulus(assessmentStimulus);
            s.setTest(findTestForStimulus(assessmentStimulus));
            return dailySlotRepository.save(s);
        }
        return s;
    }

    @Override
    public void assignAssessmentForSlot(DailySlot slot, Users user, Set<Integer> doneStimulusIds) {
        if (slot.getStimulus() == null && slot.getTest() == null && "TODO".equals(slot.getStatus())) {
            Stimulus assessmentStimulus = selectAssessmentStimulus(slot.getSkill(), user, doneStimulusIds);
            slot.setStimulus(assessmentStimulus);
            slot.setTest(findTestForStimulus(assessmentStimulus));
            dailySlotRepository.save(slot);
        }
    }

    private Users getCurrentUser() {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new AppException(ErrorCode.UNAUTHENTICATED);
        }

        String credentialId = null;
        if (authentication.getPrincipal() instanceof org.springframework.security.oauth2.jwt.Jwt jwt) {
            credentialId = jwt.getClaimAsString("userId");
        }

        if (credentialId == null) {
            throw new AppException(ErrorCode.UNAUTHENTICATED);
        }

        UserCredentials credentials = userCredentialsRepository
                .findById(credentialId)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_EXISTED));

        if (credentials.getUser() == null) {
            throw new AppException(ErrorCode.USER_NOT_EXISTED);
        }
        return credentials.getUser();
    }

    private Integer calculateStimulusQuestions(Stimulus stimulus) {
        if (stimulus == null || stimulus.getId() == null) return 0;
        return stimulusRepository.countQuestionsByStimulusId(stimulus.getId());
    }
}
