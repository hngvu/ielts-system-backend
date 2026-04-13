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
    private final VocabLearningService vocabLearningService;

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
                    // [PHASE 3] Intensive mode: assign FULL TEST (mode=FULL_TEST) instead of single Stimulus
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
                        // Keep track so we don't repeat the test; here we use test id + offset for exclusion if needed
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

            // [NEW] Generate Vocab tasks for practice days (Day 1 to 6)
            // OMIT Vocab in Phase 3
            if (phase != 3) {
                String vocabTaskType = (day % 2 != 0) ? "VOCAB_LEARN" : "VOCAB_TEST";

                String topicHint = null;
                for (DailySlot s : dailySlotRepository.findByWeeklyPlanOrderByDayOfWeekAscIdAsc(plan)) {
                    if (s.getDayOfWeek().equals(day)
                            && (s.getSkill() == Skill.READING || s.getSkill() == Skill.LISTENING)) {
                        if (s.getStimulus() != null
                                && s.getStimulus().getTags() != null
                                && !s.getStimulus().getTags().isEmpty()) {
                            topicHint =
                                    s.getStimulus().getTags().iterator().next().getName();
                            break;
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
            Stimulus assessmentStimulus = selectAssessmentStimulus(skill, user, doneStimulusIds);

            DailySlot assessmentSlot = DailySlot.builder()
                    .weeklyPlan(plan)
                    .dayOfWeek(7)
                    .slotDate(day7Date)
                    .skill(skill)
                    .taskType("ASSESSMENT")
                    .stimulus(assessmentStimulus)
                    .test(findTestForStimulus(assessmentStimulus))
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
        WeeklyPlan plan =
                weeklyPlanRepository.findByUserAndStatus(user, "ACTIVE").orElse(null);

        if (plan == null) {
            return null;
        }

        return buildDetailResponse(plan, user);
    }

    @Override
    @Transactional(readOnly = true)
    public WeeklyPlanDetailResponse getTodaySlots() {
        Users user = getCurrentUser();
        WeeklyPlan plan =
                weeklyPlanRepository.findByUserAndStatus(user, "ACTIVE").orElse(null);

        if (plan == null) {
            return null;
        }

        return buildDetailResponse(plan, user);
    }

    @Override
    @Transactional
    public void completeSlot(Integer slotId, Integer score, Integer totalQuestions, List<String> correctWords) {
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

        if ("VOCAB_TEST".equals(slot.getTaskType()) && correctWords != null && !correctWords.isEmpty()) {
            vocabLearningService.markWordsAsMastered(user, correctWords);
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

        // Only generate new words if slot isn't already DONE (to avoid re-generating on accidental re-clicks)
        if ("DONE".equals(slot.getStatus())) {
            return List.of(); // Return empty if already complete. Or throw exception based on product rules.
        }

        double band = getCurrentBandForSkill(user, Skill.VOCABULARY, null);
        String bandRange = formatBandRange(band);
        String topic = slot.getReferenceMetadata();

        return vocabLearningService.generateRoadmapVocabList(user, bandRange, topic, 15);
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

        return vocabLearningService.generateRoadmapQuiz(user);
    }

    private String formatBandRange(double band) {
        if (band < 4.5) return "3-4";
        if (band < 5.5) return "4-5";
        if (band < 6.5) return "5.5-6.5";
        if (band < 7.5) return "6.5-7.5";
        if (band >= 8.5) return "8.5-9.0";
        return "7-8";
    }

    @Override
    @Transactional
    public WeeklyPlanDetailResponse evaluateWeekAndGenerateNext() {
        Users user = getCurrentUser();
        WeeklyPlan currentPlan = weeklyPlanRepository
                .findByUserAndStatus(user, "ACTIVE")
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
        WeeklyPlan newPlan =
                weeklyPlanRepository.findByUserAndStatus(user, "ACTIVE").orElse(null);
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
                .findByUserAndStatus(user, "PENDING")
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

    /**
     * Select the best stimulus for a practice slot based on weak tags and difficulty alignment.
     */
    private Stimulus selectStimulus(Skill skill, Users user, double targetDifficulty, Set<Integer> excludeIds) {
        // 1. Get weak tags for this user
        List<LearnerMetric> metrics = learnerMetricRepository.findByUser(user);
        List<Tag> weakTags = metrics.stream()
                .sorted(Comparator.comparingDouble(this::weakAreaScore))
                .map(LearnerMetric::getTag)
                .limit(5)
                .toList();

        // 2. Find smart stimuli matching skill + weak tags
        List<Stimulus> candidates;
        if (!weakTags.isEmpty()) {
            candidates = stimulusRepository.findSmartStimuli(skill, weakTags);
        } else {
            candidates = stimulusRepository.findBySkill(skill);
        }

        if (candidates.isEmpty()) {
            // Ultimate fallback
            candidates = stimulusRepository.findBySkill(skill);
        }

        if (candidates.isEmpty()) {
            log.warn("[WeeklyPlan] No stimuli found for skill {}", skill);
            return null;
        }

        // 3. Prefer stimuli not yet done (exclude IDs)
        List<Stimulus> unseen =
                candidates.stream().filter(s -> !excludeIds.contains(s.getId())).toList();

        List<Stimulus> pool = unseen.isEmpty() ? candidates : unseen;

        // 4. Sort by stimulus ID distance for variety (simple, avoids lazy-load)
        return pool.stream()
                .min(Comparator.comparingInt(s -> Math.abs(s.getId() % 100 - (int) (targetDifficulty * 100))))
                .orElse(pool.get(0));
    }

    /**
     * Select a stimulus for the weekly assessment — targets the weakest tag for the given skill.
     */
    private Stimulus selectAssessmentStimulus(Skill skill, Users user, Set<Integer> excludeIds) {
        List<LearnerMetric> metrics = learnerMetricRepository.findByUser(user);

        // Find weakest tag for this skill by looking at tag associations
        List<Tag> weakTags = metrics.stream()
                .sorted(Comparator.comparingDouble(this::weakAreaScore))
                .map(LearnerMetric::getTag)
                .limit(3)
                .toList();

        List<Stimulus> candidates;
        if (!weakTags.isEmpty()) {
            candidates = stimulusRepository.findSmartStimuli(skill, weakTags);
        } else {
            candidates = stimulusRepository.findBySkill(skill);
        }

        if (candidates.isEmpty()) {
            candidates = stimulusRepository.findBySkill(skill);
        }

        if (candidates.isEmpty()) {
            return null;
        }

        // Prefer unseen
        List<Stimulus> unseen =
                candidates.stream().filter(s -> !excludeIds.contains(s.getId())).toList();

        List<Stimulus> pool = unseen.isEmpty() ? new ArrayList<>(candidates) : new ArrayList<>(unseen);
        Collections.shuffle(pool);
        return pool.get(0);
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
    public double getCurrentBandForSkill(Users user, Skill skill, WeeklyPlan previousPlan) {
        // Priority 1: If creating Week 1 of Month (WeekInMonth == 1 and WeekNumber > 1), use Monthly Assessment
        if (previousPlan != null && previousPlan.getWeekInMonth() == 4) {
            Optional<MonthlyAssessment> monthlyAss =
                    monthlyAssessmentRepository.findTopByUserAndStatusOrderByIdDesc(user, "COMPLETED");
            if (monthlyAss.isPresent()) {
                Double band = getBandFromMonthlyAssessment(monthlyAss.get(), skill);
                if (band != null) return band;
            }
        }

        // Priority 2: Use Week Assessment from previous plan (Day 7)
        if (previousPlan != null) {
            Optional<DailySlot> weekAssessment =
                    dailySlotRepository.findByWeeklyPlanAndDayOfWeekAndSkill(previousPlan, 7, skill);
            if (weekAssessment.isPresent() && "DONE".equals(weekAssessment.get().getStatus())) {
                return calculateBandFromSlot(weekAssessment.get());
            }
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
        if (slot.getSkill() == Skill.WRITING || slot.getSkill() == Skill.SPEAKING) {
            return slot.getScore() != null ? slot.getScore() / 9.0 * 9.0 : 4.0; // Ensure logic handles raw band
        } else {
            if (slot.getTotalQuestions() == null || slot.getTotalQuestions() == 0) return 4.0;
            double accuracy = (double) slot.getScore() / slot.getTotalQuestions();
            return convertAccuracyToBand(accuracy);
        }
    }

    private double convertAccuracyToBand(double accuracy) {
        if (accuracy >= 0.875) return 8.0; // ~35/40
        if (accuracy >= 0.75) return 7.0; // ~30/40
        if (accuracy >= 0.625) return 6.0; // ~25/40
        if (accuracy >= 0.40) return 5.0; // ~16/40
        if (accuracy >= 0.25) return 4.0; // ~10/40
        return 3.0;
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
                .skip(0) // Current plan is still ACTIVE at query time, so first COMPLETED is actual previous
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
        Optional<MonthlyAssessment> existing = monthlyAssessmentRepository.findByUserAndStatus(user, "PENDING");
        if (existing.isPresent()) {
            log.info("[MonthlyAssessment] Already pending for user {}", user.getId());
            return;
        }

        // For now, create assessment without full test (test will be assigned later via admin or auto-generation)
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

    private double weakAreaScore(LearnerMetric metric) {
        double mastery = metric.getMasteryLevel() != null ? metric.getMasteryLevel() : 0.5;
        double confidence = metric.getConfidenceScore() != null ? metric.getConfidenceScore() : 0.0;
        return mastery + ((1.0 - confidence) * 0.5);
    }

    private double estimateDifficulty(Stimulus stimulus) {
        if (stimulus.getQuestionGroups() == null || stimulus.getQuestionGroups().isEmpty()) {
            return 0.5;
        }
        return stimulus.getQuestionGroups().stream()
                .flatMap(qg -> qg.getQuestions().stream())
                .mapToDouble(q -> {
                    if (q.getTags() == null || q.getTags().isEmpty()) return 0.5;
                    return q.getTags().stream()
                            .mapToDouble(tag -> {
                                String name = tag.getName().toUpperCase();
                                if (name.contains("8") || name.contains("BAND_8")) return 0.8;
                                if (name.contains("7") || name.contains("BAND_7")) return 0.7;
                                if (name.contains("6") || name.contains("BAND_6")) return 0.6;
                                if (name.contains("5") || name.contains("BAND_5")) return 0.5;
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
        boolean monthlyPending =
                monthlyAssessmentRepository.findByUserAndStatus(user, "PENDING").isPresent();

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
