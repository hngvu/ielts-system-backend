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
import io.gsp26se16.moni.roadmap.entity.DailySlot;
import io.gsp26se16.moni.roadmap.entity.WeeklyPlan;
import io.gsp26se16.moni.roadmap.repository.DailySlotRepository;
import io.gsp26se16.moni.roadmap.repository.WeeklyPlanRepository;
import io.gsp26se16.moni.roadmap.service.GoalService;
import io.gsp26se16.moni.roadmap.service.WeeklyPlanService;
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
                int totalQ = 10 + rng.nextInt(31); // 10-40 questions
                int score = (int) (totalQ * (0.4 + rng.nextDouble() * 0.5)); // 40%-90% accuracy
                slot.setStatus("DONE");
                slot.setScore(score);
                slot.setTotalQuestions(totalQ);
                slot.setCompletedAt(LocalDateTime.now());
                dailySlotRepository.save(slot);
                completed++;
                log.info(
                        "[Simulation] Completed slot {} (day={}, skill={}, score={}/{})",
                        slot.getId(),
                        dayOfWeek,
                        slot.getSkill(),
                        score,
                        totalQ);
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
                int totalQ = 10 + rng.nextInt(31);
                int score = (int) (totalQ * (0.4 + rng.nextDouble() * 0.5));
                slot.setStatus("DONE");
                slot.setScore(score);
                slot.setTotalQuestions(totalQ);
                slot.setCompletedAt(LocalDateTime.now());
                dailySlotRepository.save(slot);
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
                    int totalQ = 10 + rng.nextInt(31);
                    int score = (int) (totalQ * (0.4 + rng.nextDouble() * 0.5));
                    slot.setStatus("DONE");
                    slot.setScore(score);
                    slot.setTotalQuestions(totalQ);
                    slot.setCompletedAt(LocalDateTime.now());
                    dailySlotRepository.save(slot);
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
                .code(1000)
                .message("Fast-forwarded " + weekResults.size() + " weeks")
                .result(Map.of("weeksSimulated", weekResults.size(), "details", weekResults))
                .build());
    }

    /**
     * Reset: delete all weekly plans and regenerate from scratch.
     */
    @PostMapping("/reset/{userId}")
    @Operation(summary = "[Simulation] Reset toàn bộ weekly plan + sinh lại từ đầu")
    @Transactional
    public ResponseEntity<ApiResponse<Map<String, Object>>> simulateReset(@PathVariable String userId) {

        Users user = resolveUser(userId);

        // Delete all daily slots for this user's plans
        List<WeeklyPlan> allPlans = weeklyPlanRepository.findByUserOrderByWeekNumberDesc(user);
        int deletedSlots = 0;
        for (WeeklyPlan plan : allPlans) {
            List<DailySlot> slots = dailySlotRepository.findByWeeklyPlanOrderByDayOfWeekAscIdAsc(plan);
            dailySlotRepository.deleteAll(slots);
            deletedSlots += slots.size();
        }
        weeklyPlanRepository.deleteAll(allPlans);

        log.info(
                "[Simulation] Reset: deleted {} plans and {} slots for user {}", allPlans.size(), deletedSlots, userId);

        // Regenerate fresh plan
        weeklyPlanService.generateWeeklyPlan(user);

        WeeklyPlan newPlan = weeklyPlanRepository
                .findFirstByUserAndStatusOrderByWeekNumberDesc(user, "ACTIVE")
                .orElse(null);

        return ResponseEntity.ok(ApiResponse.<Map<String, Object>>builder()
                .code(1000)
                .message("Reset complete. New plan generated.")
                .result(Map.of(
                        "deletedPlans",
                        allPlans.size(),
                        "deletedSlots",
                        deletedSlots,
                        "newWeekNumber",
                        newPlan != null ? newPlan.getWeekNumber() : 0))
                .build());
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
}
