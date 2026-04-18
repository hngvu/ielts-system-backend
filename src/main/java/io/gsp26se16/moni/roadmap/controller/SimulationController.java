package io.gsp26se16.moni.roadmap.controller;

import java.time.LocalDateTime;
import java.util.*;

import jakarta.transaction.Transactional;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import io.gsp26se16.moni.authentication.entity.UserCredentials;
import io.gsp26se16.moni.authentication.entity.Users;
import io.gsp26se16.moni.authentication.repository.UserCredentialsRepository;
import io.gsp26se16.moni.common.dto.ApiResponse;
import io.gsp26se16.moni.common.exception.AppException;
import io.gsp26se16.moni.common.exception.ErrorCode;
import io.gsp26se16.moni.content.repository.StimulusRepository;
import io.gsp26se16.moni.roadmap.entity.DailySlot;
import io.gsp26se16.moni.roadmap.entity.LearnerMetric;
import io.gsp26se16.moni.roadmap.entity.WeeklyPlan;
import io.gsp26se16.moni.roadmap.repository.*;
import io.gsp26se16.moni.roadmap.service.GoalService;
import io.gsp26se16.moni.roadmap.service.WeeklyPlanService;
import io.gsp26se16.moni.tag.entity.Tag;
import io.gsp26se16.moni.tag.repository.TagRepository;
import io.gsp26se16.moni.vocab.repository.VocabQuizHistoryRepository;
import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Admin-only controller for simulating weekly plan progression.
 * Allows time-skip testing of the Adaptive Learning system.
 */
@RestController
@RequestMapping("/api/v1/admin/simulation")
@RequiredArgsConstructor
@Slf4j
public class SimulationController {

    private final WeeklyPlanRepository weeklyPlanRepository;
    private final DailySlotRepository dailySlotRepository;
    private final WeeklyPlanService weeklyPlanService;
    private final GoalService goalService;
    private final UserCredentialsRepository userCredentialsRepository;
    private final LearnerMetricRepository learnerMetricRepository;
    private final TagRepository tagRepository;
    private final InsightSnapshotRepository insightSnapshotRepository;
    private final MonthlyAssessmentRepository monthlyAssessmentRepository;
    private final VocabQuizHistoryRepository vocabQuizHistoryRepository;
    private final StimulusRepository stimulusRepository;

    /**
     * Simulate completing a single day's slots with random scores.
     */
    @PostMapping("/complete-day/{userId}/{dayOfWeek}")
    @Operation(summary = "[Simulation] Hoàn thành tất cả slot của 1 ngày với điểm ngẫu nhiên")
    @Transactional
    public ResponseEntity<ApiResponse<Map<String, Object>>> simulateCompleteDay(
            @PathVariable String userId, @PathVariable Integer dayOfWeek) {

        Users user = resolveUser(userId);
        WeeklyPlan plan = getActivePlan(user);
        List<DailySlot> slots = dailySlotRepository.findByWeeklyPlanOrderByDayOfWeekAscIdAsc(plan);

        Random rng = new Random();
        int completed = 0;

        for (DailySlot slot : slots) {
            if (slot.getDayOfWeek().equals(dayOfWeek) && "TODO".equals(slot.getStatus())) {
                simulateSlotCompletion(slot, rng);
                completed++;
                log.info(
                        "[Simulation] Completed slot {} (day={}, skill={}, score={}/{})",
                        slot.getId(),
                        dayOfWeek,
                        slot.getSkill(),
                        slot.getScore(),
                        slot.getTotalQuestions());
            }
        }

        return ResponseEntity.ok(ApiResponse.<Map<String, Object>>builder()
                .code(1000)
                .message("Simulated day " + dayOfWeek + " completion")
                .result(Map.of(
                        "weekNumber", plan.getWeekNumber(),
                        "dayOfWeek", dayOfWeek,
                        "slotsCompleted", completed))
                .build());
    }

    /**
     * Simulate completing ALL remaining TODO slots in the current week.
     */
    @PostMapping("/complete-week/{userId}")
    @Operation(summary = "[Simulation] Hoàn thành toàn bộ tuần với điểm ngẫu nhiên")
    @Transactional
    public ResponseEntity<ApiResponse<Map<String, Object>>> simulateCompleteWeek(@PathVariable String userId) {

        Users user = resolveUser(userId);
        WeeklyPlan plan = getActivePlan(user);
        List<DailySlot> slots = dailySlotRepository.findByWeeklyPlanOrderByDayOfWeekAscIdAsc(plan);

        Random rng = new Random();
        int completed = 0;

        for (DailySlot slot : slots) {
            if ("TODO".equals(slot.getStatus())) {
                simulateSlotCompletion(slot, rng);
                completed++;
            }
        }

        log.info(
                "[Simulation] Completed entire week {} for user {} ({} slots)",
                plan.getWeekNumber(),
                userId,
                completed);

        return ResponseEntity.ok(ApiResponse.<Map<String, Object>>builder()
                .code(1000)
                .message("Simulated full week completion")
                .result(Map.of("weekNumber", plan.getWeekNumber(), "slotsCompleted", completed))
                .build());
    }

    /**
     * Skip 1 day in the simulation.
     * Brings future slots into "today" by subtracting 1 day from all current plan dates.
     */
    @PostMapping("/skip-day/{userId}")
    @Operation(summary = "[Simulation] Bỏ qua 1 ngày (đưa lịch ngày mai về hôm nay)")
    @Transactional
    public ResponseEntity<ApiResponse<Map<String, Object>>> skipOneDay(@PathVariable String userId) {
        Users user = resolveUser(userId);
        WeeklyPlan currentPlan = getActivePlan(user);

        // Shift weekly plan dates back by 1
        currentPlan.setWeekStartDate(currentPlan.getWeekStartDate().minusDays(1));
        currentPlan.setWeekEndDate(currentPlan.getWeekEndDate().minusDays(1));
        weeklyPlanRepository.save(currentPlan);

        // Shift all slot dates back by 1
        List<DailySlot> slots = dailySlotRepository.findByWeeklyPlanOrderByDayOfWeekAscIdAsc(currentPlan);
        int shifted = 0;
        for (DailySlot slot : slots) {
            slot.setSlotDate(slot.getSlotDate().minusDays(1));
            dailySlotRepository.save(slot);
            shifted++;
        }

        log.info("[Simulation] Skipped 1 day for user {}, shifted {} slots", userId, shifted);

        return ResponseEntity.ok(ApiResponse.<Map<String, Object>>builder()
                .code(1000)
                .message("Đã skip 1 ngày thành công!")
                .result(Map.of("success", true, "shiftedSlots", shifted))
                .build());
    }

    /**
     * Evaluate the current week AND generate next week.
     * This is the key "time-skip" — it closes the current week and creates a new one.
     */
    @PostMapping("/evaluate-and-next/{userId}")
    @Operation(summary = "[Simulation] Đánh giá tuần hiện tại + sinh tuần mới (time skip)")
    @Transactional
    public ResponseEntity<ApiResponse<Map<String, Object>>> simulateEvaluateAndNext(@PathVariable String userId) {

        Users user = resolveUser(userId);
        WeeklyPlan currentPlan = getActivePlan(user);

        // Calculate metrics
        List<DailySlot> allSlots = dailySlotRepository.findByWeeklyPlanOrderByDayOfWeekAscIdAsc(currentPlan);
        long totalSlots = allSlots.size();
        long doneSlots =
                allSlots.stream().filter(s -> "DONE".equals(s.getStatus())).count();
        double completionRate = totalSlots > 0 ? (double) doneSlots / totalSlots : 0.0;

        double weeklyAccuracy = allSlots.stream()
                .filter(s -> "DONE".equals(s.getStatus()) && s.getScore() != null && s.getTotalQuestions() != null)
                .mapToDouble(s -> s.getTotalQuestions() > 0 ? (double) s.getScore() / s.getTotalQuestions() : 0.0)
                .average()
                .orElse(0.0);

        // Close current week
        currentPlan.setWeeklyAccuracy(weeklyAccuracy);
        currentPlan.setCompletionRate(completionRate);
        currentPlan.setPerformanceVerdict(
                weeklyAccuracy >= 0.7 ? "IMPROVED" : weeklyAccuracy >= 0.5 ? "STABLE" : "DECLINED");
        currentPlan.setStatus("COMPLETED");
        weeklyPlanRepository.save(currentPlan);

        // Fake AI Evaluation progress for Tags
        simulateMetricProgress(user, new Random());

        // Snapshot insights
        try {
            goalService.snapshotInsightsForWeek(user, currentPlan.getWeekNumber());
        } catch (Exception e) {
            log.warn("[Simulation] Insight snapshot failed: {}", e.getMessage());
        }

        // Generate next week
        weeklyPlanService.generateWeeklyPlan(user);

        WeeklyPlan newPlan = weeklyPlanRepository
                .findFirstByUserAndStatusOrderByWeekNumberDesc(user, "ACTIVE")
                .orElse(null);

        int newWeekNumber = newPlan != null ? newPlan.getWeekNumber() : -1;

        log.info(
                "[Simulation] Week {} evaluated → Week {} generated for user {}",
                currentPlan.getWeekNumber(),
                newWeekNumber,
                userId);

        return ResponseEntity.ok(ApiResponse.<Map<String, Object>>builder()
                .code(1000)
                .message("Week " + currentPlan.getWeekNumber() + " evaluated, Week " + newWeekNumber + " generated")
                .result(Map.of(
                        "previousWeek",
                        currentPlan.getWeekNumber(),
                        "newWeek",
                        newWeekNumber,
                        "accuracy",
                        Math.round(weeklyAccuracy * 100) + "%",
                        "completionRate",
                        Math.round(completionRate * 100) + "%",
                        "verdict",
                        currentPlan.getPerformanceVerdict()))
                .build());
    }

    /**
     * Full simulation: complete all slots + evaluate + generate next week in one call.
     * Optionally repeat for N weeks.
     */
    @PostMapping("/fast-forward/{userId}")
    @Operation(summary = "[Simulation] Fast-forward: hoàn thành + đánh giá + sinh lộ trình mới cho N tuần")
    @Transactional
    public ResponseEntity<ApiResponse<Map<String, Object>>> simulateFastForward(
            @PathVariable String userId, @RequestParam(defaultValue = "1") Integer weeks) {

        Users user = resolveUser(userId);
        List<Map<String, Object>> weekResults = new ArrayList<>();

        for (int w = 0; w < weeks; w++) {
            WeeklyPlan plan = weeklyPlanRepository
                    .findFirstByUserAndStatusOrderByWeekNumberDesc(user, "ACTIVE")
                    .orElse(null);

            if (plan == null) {
                log.warn("[Simulation] No active plan found at iteration {}, stopping", w);
                break;
            }

            // Complete all TODO slots
            List<DailySlot> slots = dailySlotRepository.findByWeeklyPlanOrderByDayOfWeekAscIdAsc(plan);
            Random rng = new Random();
            int completed = 0;

            for (DailySlot slot : slots) {
                if ("TODO".equals(slot.getStatus())) {
                    simulateSlotCompletion(slot, rng);
                    completed++;
                }
            }

            // Evaluate
            double accuracy = slots.stream()
                    .filter(s -> "DONE".equals(s.getStatus()) && s.getScore() != null && s.getTotalQuestions() != null)
                    .mapToDouble(s -> s.getTotalQuestions() > 0 ? (double) s.getScore() / s.getTotalQuestions() : 0.0)
                    .average()
                    .orElse(0.0);

            double completion = slots.isEmpty() ? 0.0 : (double) completed / slots.size();

            plan.setWeeklyAccuracy(accuracy);
            plan.setCompletionRate(1.0); // always 100% in simulation
            plan.setPerformanceVerdict(accuracy >= 0.7 ? "IMPROVED" : accuracy >= 0.5 ? "STABLE" : "DECLINED");
            plan.setStatus("COMPLETED");
            weeklyPlanRepository.save(plan);

            // Fake AI Evaluation progress for Tags
            simulateMetricProgress(user, rng);

            try {
                goalService.snapshotInsightsForWeek(user, plan.getWeekNumber());
            } catch (Exception e) {
                log.warn("[Simulation] Insight snapshot failed for week {}: {}", plan.getWeekNumber(), e.getMessage());
            }

            // Generate next
            weeklyPlanService.generateWeeklyPlan(user);

            weekResults.add(Map.of(
                    "week",
                    plan.getWeekNumber(),
                    "slotsCompleted",
                    completed,
                    "accuracy",
                    Math.round(accuracy * 100) + "%",
                    "verdict",
                    plan.getPerformanceVerdict()));

            log.info(
                    "[Simulation] Fast-forward week {} done (accuracy={}%, verdict={})",
                    plan.getWeekNumber(), Math.round(accuracy * 100), plan.getPerformanceVerdict());
        }

        return ResponseEntity.ok(ApiResponse.<Map<String, Object>>builder()
                .result(Map.of("weeksSimulated", weekResults.size(), "details", weekResults))
                .build());
    }

    /**
     * Reset: delete all weekly plans and regenerate from scratch.
     */
    @PostMapping("/reset/{userId}")
    @Operation(summary = "[Simulation] Reset toàn bộ weekly plan + sinh lại từ đầu (bao gồm cả Tags/Metrics)")
    @Transactional
    public ResponseEntity<ApiResponse<Map<String, Object>>> simulateReset(@PathVariable String userId) {
        Users user = resolveUser(userId);

        // Delete all vocab quiz history (foreign key constraint with daily_slots)
        vocabQuizHistoryRepository.deleteByUser(user);

        // Delete all planning data
        List<WeeklyPlan> allPlans = weeklyPlanRepository.findByUserOrderByWeekNumberDesc(user);
        int deletedSlots = 0;
        for (WeeklyPlan p : allPlans) {
            deletedSlots += dailySlotRepository.deleteByWeeklyPlan(p);
        }
        weeklyPlanRepository.deleteAll(allPlans);

        // Delete all progress data (Tags/Metrics snapshots)
        learnerMetricRepository.deleteAll(learnerMetricRepository.findByUser(user));
        insightSnapshotRepository.deleteAll(insightSnapshotRepository.findByUser(user));
        monthlyAssessmentRepository.deleteAll(monthlyAssessmentRepository.findByUser(user));

        // Regenerate fresh plan
        weeklyPlanService.generateWeeklyPlan(user);

        WeeklyPlan newPlan = weeklyPlanRepository
                .findFirstByUserAndStatusOrderByWeekNumberDesc(user, "ACTIVE")
                .orElse(null);

        return ResponseEntity.ok(ApiResponse.<Map<String, Object>>builder()
                .code(1000)
                .message("Reset complete. Tags/Metrics cleared. New plan generated.")
                .result(Map.of(
                        "deletedPlans",
                        allPlans.size(),
                        "deletedSlots",
                        deletedSlots,
                        "newWeekNumber",
                        newPlan != null ? newPlan.getWeekNumber() : 0))
                .build());
    }

    /**
     * Diagnostic: Check if adaptive selection for Day 7 matches user's current weaknesses.
     */
    @GetMapping("/verify-assessment/{userId}")
    @Operation(summary = "[Simulation] Kiểm tra logic AI chọn bài test (Weakness vs. Selection)")
    @Transactional
    public ResponseEntity<ApiResponse<Map<String, Object>>> verifyAssessmentSelection(@PathVariable String userId) {
        Users user = resolveUser(userId);

        // 1. Fetch current top weaknesses
        List<LearnerMetric> weakMetrics = learnerMetricRepository.findByUserOrderByMasteryLevelAsc(user).stream()
                .filter(m -> m.getMasteryLevel() != null && m.getMasteryLevel() < 0.6)
                .limit(10)
                .toList();

        List<Map<String, Object>> weakInfo = new ArrayList<>();
        for (LearnerMetric m : weakMetrics) {
            Map<String, Object> map = new HashMap<>();
            map.put("tagName", m.getTag().getName());
            map.put("tagType", m.getTag().getType());
            map.put("mastery", Math.round(m.getMasteryLevel() * 100) + "%");
            weakInfo.add(map);
        }

        // 2. Fetch Day 7 Slots for current active plan
        WeeklyPlan plan = getActivePlan(user);
        List<DailySlot> allSlots = dailySlotRepository.findByWeeklyPlanOrderByDayOfWeekAscIdAsc(plan);
        List<DailySlot> assessmentSlots = allSlots.stream()
                .filter(s -> "ASSESSMENT".equals(s.getTaskType()))
                .toList();

        List<Map<String, Object>> assignments = new ArrayList<>();
        for (DailySlot slot : assessmentSlots) {
            Map<String, Object> info = new HashMap<>();
            info.put("skill", slot.getSkill());

            if (slot.getStimulus() != null) {
                info.put("assignedStimulus", slot.getStimulus().getTitle());
                List<String> sTags =
                        slot.getStimulus().getTags().stream().map(Tag::getName).toList();
                info.put("stimulusTags", sTags);

                // Find intersections with user's specific weak tags
                List<String> matches = sTags.stream()
                        .filter(st ->
                                weakInfo.stream().anyMatch(w -> w.get("tagName").equals(st)))
                        .toList();
                info.put("matchedWeaknesses", matches);
                info.put("isMatchFound", !matches.isEmpty());
            } else {
                info.put("assignedStimulus", "CHƯA GÁN (Bấm vào ngày Sunday trên UI để kích hoạt JIT assignment)");
            }
            assignments.add(info);
        }
        Map<String, Object> result = new HashMap<>();
        result.put("user", user.getFull_name());
        result.put("userWeaknesses", weakInfo);
        result.put("sundayAssignments", assignments);

        return ResponseEntity.ok(
                ApiResponse.<Map<String, Object>>builder().result(result).build());
    }

    // =====================================================================
    // HELPERS
    // =====================================================================

    private Users resolveUser(String credentialId) {
        UserCredentials credentials = userCredentialsRepository
                .findById(credentialId)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_EXISTED));
        if (credentials.getUser() == null) {
            throw new AppException(ErrorCode.USER_NOT_EXISTED);
        }
        return credentials.getUser();
    }

    private WeeklyPlan getActivePlan(Users user) {
        return weeklyPlanRepository
                .findFirstByUserAndStatusOrderByWeekNumberDesc(user, "ACTIVE")
                .orElseThrow(() -> new AppException(ErrorCode.ACTIVE_ROADMAP_NOT_FOUND));
    }

    private void simulateSlotCompletion(DailySlot slot, Random rng) {
        slot.setStatus("DONE");
        slot.setCompletedAt(LocalDateTime.now());

        if ("VOCAB_LEARN".equals(slot.getTaskType())) {
            // Learning tasks don't have scores
            slot.setScore(null);
            slot.setTotalQuestions(null);
        } else if (slot.getSkill() == io.gsp26se16.moni.common.enumeration.Skill.WRITING
                || slot.getSkill() == io.gsp26se16.moni.common.enumeration.Skill.SPEAKING) {
            // Writing and Speaking use band scores (1-9). Generate firmer scores (5.0 - 8.5)
            // band * 10 then divide by 10 to simulate half band if needed
            double band = 5.0 + rng.nextDouble() * 3.5; // 5.0 to 8.5
            int roundedBand = (int) (Math.round(band * 2.0)); // In half increments
            slot.setScore(roundedBand / 2); // Save as integer band for now (as system logic expects)
            // Wait, does system expect score as integer or can it be double?
            // DailySlot score is Integer. So half bands might need mapping or just use integers.
            // Let's stick to integers 5 to 9 for simplicity in simulation.
            int finalBand = 5 + rng.nextInt(5); // 5, 6, 7, 8, 9
            slot.setScore(finalBand);
            slot.setTotalQuestions(9);
        } else {
            // Reading, Listening, Vocab Quiz => standard correct / total ratio
            // Target band 5.5 to 8.5 => Accuracy 0.55 to 0.95
            int totalQ = 10 + rng.nextInt(31); // 10 to 40 questions
            double targetAccuracy = 0.55 + rng.nextDouble() * 0.40; // 55% to 95%
            int score = (int) (totalQ * targetAccuracy);
            slot.setScore(score);
            slot.setTotalQuestions(totalQ);
        }

        dailySlotRepository.save(slot);
    }

    private void simulateMetricProgress(Users user, Random rng) {
        List<LearnerMetric> metrics = learnerMetricRepository.findByUserOrderByUpdatedAtDesc(user);

        // If user has no tags at all, let's grab random tags to start tracking
        if (metrics.isEmpty()) {
            List<Tag> allTags = tagRepository.findAll();
            if (!allTags.isEmpty()) {
                Collections.shuffle(allTags, rng);
                int count = Math.min(10, allTags.size());
                for (int i = 0; i < count; i++) {
                    LearnerMetric m = LearnerMetric.builder()
                            .user(user)
                            .tag(allTags.get(i))
                            .masteryLevel(0.2 + rng.nextDouble() * 0.3) // 20% to 50%
                            .confidenceScore(0.1 + rng.nextDouble() * 0.3) // 10% to 40%
                            .updatedAt(LocalDateTime.now())
                            .attemptCount(1)
                            .pTransit(0.1)
                            .pGuess(0.2)
                            .pSlip(0.1)
                            .build();
                    learnerMetricRepository.save(m);
                }
            }
        } else {
            // Bump existing ones to show progress
            for (LearnerMetric m : metrics) {
                // Slight chance to not improve or even drop
                double masteryDelta = (rng.nextDouble() * 0.15) - 0.02; // -2% to +13%
                double confDelta = (rng.nextDouble() * 0.20) - 0.05; // -5% to +15%

                double newMastery = Math.min(
                        1.0, Math.max(0.0, (m.getMasteryLevel() != null ? m.getMasteryLevel() : 0.0) + masteryDelta));
                double newConf = Math.min(
                        1.0,
                        Math.max(0.0, (m.getConfidenceScore() != null ? m.getConfidenceScore() : 0.0) + confDelta));

                m.setMasteryLevel(newMastery);
                m.setConfidenceScore(newConf);
                m.setUpdatedAt(LocalDateTime.now());
                m.setAttemptCount((m.getAttemptCount() != null ? m.getAttemptCount() : 0) + 1);
                learnerMetricRepository.save(m);
            }
        }
    }
}
