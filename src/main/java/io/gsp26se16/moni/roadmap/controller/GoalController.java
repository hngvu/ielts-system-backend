package io.gsp26se16.moni.roadmap.controller;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import io.gsp26se16.moni.common.dto.ApiResponse;
import io.gsp26se16.moni.roadmap.dto.response.GoalResponse;
import io.gsp26se16.moni.roadmap.dto.response.LearnerRoadmapInsightsResponse;
import io.gsp26se16.moni.roadmap.dto.response.MetricSummaryResponse;
import io.gsp26se16.moni.roadmap.service.GoalService;
import io.gsp26se16.moni.roadmap.service.MetricAiSummaryService;
import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;

@RestController
@PreAuthorize("hasRole('LEARNER')")
@RequestMapping("/api/v1/learner/goals")
@RequiredArgsConstructor
public class GoalController {

    private final GoalService goalService;
    private final MetricAiSummaryService metricAiSummaryService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<GoalResponse>>> getActiveGoals() {

        List<GoalResponse> result = goalService.getActiveGoals();

        return ResponseEntity.ok(ApiResponse.<List<GoalResponse>>builder()
                .code(1000)
                .message("Lấy danh sách mục tiêu thành công!")
                .result(result)
                .build());
    }

    @GetMapping("/insights")
    @Operation(summary = "Lay chi so ca nhan cho roadmap (calibration, metric indices, warning theo ngay thi)")
    public ResponseEntity<ApiResponse<LearnerRoadmapInsightsResponse>> getRoadmapInsights() {

        LearnerRoadmapInsightsResponse result = goalService.getRoadmapInsights();

        return ResponseEntity.ok(ApiResponse.<LearnerRoadmapInsightsResponse>builder()
                .code(1000)
                .message("Lấy thông tin insights thành công!")
                .result(result)
                .build());
    }

    @GetMapping("/insights/ai-summary")
    @Operation(summary = "AI phân tích chỉ số học tập và đưa ra khuyến nghị")
    public ResponseEntity<ApiResponse<MetricSummaryResponse>> getMetricAiSummary() {

        MetricSummaryResponse result = metricAiSummaryService.generateMetricSummary();

        return ResponseEntity.ok(ApiResponse.<MetricSummaryResponse>builder()
                .code(1000)
                .message("Tạo phân tích AI thành công!")
                .result(result)
                .build());
    }

    @GetMapping("/insights/week/{weekNumber}")
    @Operation(summary = "Lấy chỉ số cá nhân snapshot theo tuần")
    public ResponseEntity<ApiResponse<LearnerRoadmapInsightsResponse>> getRoadmapInsightsByWeek(
            @PathVariable Integer weekNumber) {

        LearnerRoadmapInsightsResponse result = goalService.getRoadmapInsightsByWeek(weekNumber);

        return ResponseEntity.ok(ApiResponse.<LearnerRoadmapInsightsResponse>builder()
                .code(1000)
                .message("Lấy thông tin insights lịch sử thành công!")
                .result(result)
                .build());
    }
}
