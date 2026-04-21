package io.gsp26se16.moni.roadmap.controller;

import java.util.List;
import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import io.gsp26se16.moni.common.dto.ApiResponse;
import io.gsp26se16.moni.common.exception.AppException;
import io.gsp26se16.moni.common.exception.ErrorCode;
import io.gsp26se16.moni.payment.service.SubscriptionService;
import io.gsp26se16.moni.roadmap.dto.response.MonthlyAssessmentResponse;
import io.gsp26se16.moni.roadmap.dto.response.WeeklyPlanDetailResponse;
import io.gsp26se16.moni.roadmap.dto.response.WeeklyPlanSummaryResponse;
import io.gsp26se16.moni.roadmap.service.WeeklyPlanService;
import io.gsp26se16.moni.vocab.dto.VocabResponse;
import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;

@RestController
@PreAuthorize("hasRole('LEARNER')")
@RequestMapping("/api/v1/learner/weekly-plan")
@RequiredArgsConstructor
public class WeeklyPlanController {

    private final WeeklyPlanService weeklyPlanService;
    private final SubscriptionService subscriptionService;
    private final com.fasterxml.jackson.databind.ObjectMapper objectMapper;

    private void requireRoadmapSubscription() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof Jwt jwt) {
            String credentialId = jwt.getClaimAsString("userId");
            if (credentialId != null) {
                var sub = subscriptionService.getActiveRoadmapSubscription(credentialId);
                if (sub.isPresent()) return;
            }
        }
        throw new AppException(ErrorCode.ROADMAP_SUBSCRIPTION_REQUIRED);
    }

    @GetMapping
    @Operation(summary = "Lấy plan tuần hiện tại + tất cả slots")
    public ResponseEntity<ApiResponse<WeeklyPlanDetailResponse>> getCurrentPlan() {
        requireRoadmapSubscription();
        WeeklyPlanDetailResponse result = weeklyPlanService.getCurrentPlan();
        return ResponseEntity.ok(ApiResponse.<WeeklyPlanDetailResponse>builder()
                .code(1000)
                .message("Lấy lộ trình tuần thành công!")
                .result(result)
                .build());
    }

    @GetMapping("/week/{weekNumber}")
    @Operation(summary = "Lấy plan tuần theo số tuần + tất cả slots")
    public ResponseEntity<ApiResponse<WeeklyPlanDetailResponse>> getPlanByWeek(@PathVariable Integer weekNumber) {
        requireRoadmapSubscription();
        WeeklyPlanDetailResponse result = weeklyPlanService.getPlanByWeek(weekNumber);
        return ResponseEntity.ok(ApiResponse.<WeeklyPlanDetailResponse>builder()
                .code(1000)
                .message("Lấy lộ trình tuần lịch sử thành công!")
                .result(result)
                .build());
    }

    @GetMapping("/today")
    @Operation(summary = "Lấy tasks hôm nay")
    public ResponseEntity<ApiResponse<WeeklyPlanDetailResponse>> getTodaySlots() {
        requireRoadmapSubscription();
        WeeklyPlanDetailResponse result = weeklyPlanService.getTodaySlots();
        return ResponseEntity.ok(ApiResponse.<WeeklyPlanDetailResponse>builder()
                .code(1000)
                .message("Lấy bài tập hôm nay thành công!")
                .result(result)
                .build());
    }

    @PatchMapping("/slots/{slotId}/complete")
    @Operation(summary = "Hoàn thành 1 slot")
    public ResponseEntity<ApiResponse<Map<String, Object>>> completeSlot(
            @PathVariable Integer slotId, @RequestBody Map<String, Object> body) {
        requireRoadmapSubscription();

        Integer score = body.containsKey("score") ? (Integer) body.get("score") : 0;
        Integer totalQuestions = body.containsKey("totalQuestions") ? (Integer) body.get("totalQuestions") : 0;

        List<String> correctWords = null;
        if (body.containsKey("correctWords")) {
            correctWords = (List<String>) body.get("correctWords");
        }

        List<String> incorrectWords = null;
        if (body.containsKey("incorrectWords")) {
            incorrectWords = (List<String>) body.get("incorrectWords");
        }

        java.util.Map<String, Object> quizData = null;
        if (body.containsKey("quizData")) {
            try {
                // Extracts the JSON map as is
                quizData = (java.util.Map<String, Object>) body.get("quizData");
            } catch (Exception e) {
                // ignore
            }
        }

        weeklyPlanService.completeSlot(slotId, score, totalQuestions, correctWords, incorrectWords, quizData);

        return ResponseEntity.ok(ApiResponse.<Map<String, Object>>builder()
                .code(1000)
                .message("Đã hoàn thành bài tập!")
                .result(Map.of("slotId", slotId, "status", "DONE"))
                .build());
    }

    @PostMapping("/slots/{slotId}/vocab-start")
    @Operation(summary = "Lấy danh sách 15 từ vựng cho bài học (không tự động lưu)")
    public ResponseEntity<ApiResponse<List<io.gsp26se16.moni.vocab.dto.VocabResponse>>> startVocabLearning(
            @PathVariable Integer slotId) {
        requireRoadmapSubscription();
        List<io.gsp26se16.moni.vocab.dto.VocabResponse> result = weeklyPlanService.startVocabLearning(slotId);

        return ResponseEntity.ok(ApiResponse.<List<VocabResponse>>builder()
                .code(1000)
                .message("Đã nạp 15 từ vựng thành công")
                .result(result)
                .build());
    }

    @PostMapping("/slots/{slotId}/vocab-submit-learn")
    @Operation(summary = "Lưu kết quả học từ vựng (Chưa học / Đã học) và hoàn thành slot")
    public ResponseEntity<ApiResponse<Map<String, Object>>> submitVocabLearning(
            @PathVariable Integer slotId, @RequestBody Map<String, List<Integer>> body) {
        requireRoadmapSubscription();

        List<Integer> notLearnedIds = body.getOrDefault("notLearnedIds", List.of());
        List<Integer> learnedIds = body.getOrDefault("learnedIds", List.of());

        weeklyPlanService.submitVocabLearning(slotId, notLearnedIds, learnedIds);

        return ResponseEntity.ok(ApiResponse.<Map<String, Object>>builder()
                .code(1000)
                .message("Đã lưu từ vựng và hoàn thành bài tập!")
                .result(Map.of("slotId", slotId, "status", "DONE"))
                .build());
    }

    @GetMapping("/slots/{slotId}/vocab-test")
    @Operation(summary = "Lấy bài thi Quiz từ vựng cho slot kiểm tra")
    public ResponseEntity<ApiResponse<io.gsp26se16.moni.vocab.dto.QuizResponse>> getVocabQuiz(
            @PathVariable Integer slotId) {
        requireRoadmapSubscription();
        io.gsp26se16.moni.vocab.dto.QuizResponse result = weeklyPlanService.getVocabQuiz(slotId);

        return ResponseEntity.ok(ApiResponse.<io.gsp26se16.moni.vocab.dto.QuizResponse>builder()
                .code(1000)
                .message("Lấy bài kiểm tra từ vựng thành công")
                .result(result)
                .build());
    }

    @PostMapping("/evaluate")
    @Operation(summary = "Đánh giá cuối tuần + sinh tuần tiếp theo")
    public ResponseEntity<ApiResponse<WeeklyPlanDetailResponse>> evaluateWeek() {
        requireRoadmapSubscription();
        WeeklyPlanDetailResponse result = weeklyPlanService.evaluateWeekAndGenerateNext();
        return ResponseEntity.ok(ApiResponse.<WeeklyPlanDetailResponse>builder()
                .code(1000)
                .message("Đã đánh giá tuần và tạo lộ trình mới!")
                .result(result)
                .build());
    }

    @GetMapping("/history")
    @Operation(summary = "Lịch sử các tuần đã qua")
    public ResponseEntity<ApiResponse<List<WeeklyPlanSummaryResponse>>> getHistory() {
        requireRoadmapSubscription();
        List<WeeklyPlanSummaryResponse> result = weeklyPlanService.getHistory();
        return ResponseEntity.ok(ApiResponse.<List<WeeklyPlanSummaryResponse>>builder()
                .code(1000)
                .message("Lấy lịch sử thành công!")
                .result(result)
                .build());
    }

    @GetMapping("/monthly-assessment")
    @Operation(summary = "Lấy bài đánh giá tháng (nếu có)")
    public ResponseEntity<ApiResponse<MonthlyAssessmentResponse>> getMonthlyAssessment() {
        requireRoadmapSubscription();
        MonthlyAssessmentResponse result = weeklyPlanService.getPendingMonthlyAssessment();
        return ResponseEntity.ok(ApiResponse.<MonthlyAssessmentResponse>builder()
                .code(1000)
                .message(result != null ? "Có bài đánh giá tháng!" : "Chưa đến kỳ đánh giá tháng")
                .result(result)
                .build());
    }

    @GetMapping("/learn-metric-status")
    @Operation(summary = "Kiểm tra user có learn_metric cũ không (dùng trước khi mua gói)")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getLearnMetricStatus() {
        // Không cần subscription - endpoint này dùng để check trước khi mua
        Map<String, Object> result = weeklyPlanService.getLearnMetricStatus();
        return ResponseEntity.ok(ApiResponse.<Map<String, Object>>builder()
                .code(1000)
                .message("Kiểm tra trạng thái learn metric thành công")
                .result(result)
                .build());
    }
}
