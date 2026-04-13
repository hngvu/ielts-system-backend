package io.gsp26se16.moni.roadmap.controller;

import java.util.List;
import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import io.gsp26se16.moni.common.dto.ApiResponse;
import io.gsp26se16.moni.roadmap.dto.response.MonthlyAssessmentResponse;
import io.gsp26se16.moni.roadmap.dto.response.WeeklyPlanDetailResponse;
import io.gsp26se16.moni.roadmap.dto.response.WeeklyPlanSummaryResponse;
import io.gsp26se16.moni.roadmap.service.WeeklyPlanService;
import io.gsp26se16.moni.vocab.dto.VocabResponse;
import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/learner/weekly-plan")
@RequiredArgsConstructor
public class WeeklyPlanController {

    private final WeeklyPlanService weeklyPlanService;

    @GetMapping
    @Operation(summary = "Lấy plan tuần hiện tại + tất cả slots")
    public ResponseEntity<ApiResponse<WeeklyPlanDetailResponse>> getCurrentPlan() {
        WeeklyPlanDetailResponse result = weeklyPlanService.getCurrentPlan();
        return ResponseEntity.ok(ApiResponse.<WeeklyPlanDetailResponse>builder()
                .code(1000)
                .message("Lấy lộ trình tuần thành công!")
                .result(result)
                .build());
    }

    @GetMapping("/today")
    @Operation(summary = "Lấy tasks hôm nay")
    public ResponseEntity<ApiResponse<WeeklyPlanDetailResponse>> getTodaySlots() {
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

        Integer score = body.containsKey("score") ? (Integer) body.get("score") : 0;
        Integer totalQuestions = body.containsKey("totalQuestions") ? (Integer) body.get("totalQuestions") : 0;

        List<String> correctWords = null;
        if (body.containsKey("correctWords")) {
            correctWords = (List<String>) body.get("correctWords");
        }

        weeklyPlanService.completeSlot(slotId, score, totalQuestions, correctWords);

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
        MonthlyAssessmentResponse result = weeklyPlanService.getPendingMonthlyAssessment();
        return ResponseEntity.ok(ApiResponse.<MonthlyAssessmentResponse>builder()
                .code(1000)
                .message(result != null ? "Có bài đánh giá tháng!" : "Chưa đến kỳ đánh giá tháng")
                .result(result)
                .build());
    }
}
