package io.gsp26se16.moni.roadmap.controller;

import java.util.List;
import java.util.Map;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import io.gsp26se16.moni.common.dto.ApiResponse;
import io.gsp26se16.moni.roadmap.dto.request.GoalCreateRequest;
import io.gsp26se16.moni.roadmap.dto.request.GoalUpdateRequest;
import io.gsp26se16.moni.roadmap.dto.request.TaskStatusUpdateRequest;
import io.gsp26se16.moni.roadmap.dto.response.GoalCreateResponse;
import io.gsp26se16.moni.roadmap.dto.response.GoalResponse;
import io.gsp26se16.moni.roadmap.dto.response.RoadmapDetailResponse;
import io.gsp26se16.moni.roadmap.service.GoalService;
import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/learner/goals")
@RequiredArgsConstructor
public class GoalController {

    private final GoalService goalService;

    @PostMapping
    public ResponseEntity<ApiResponse<GoalCreateResponse>> createGoal(@RequestBody @Valid GoalCreateRequest request) {

        GoalCreateResponse result = goalService.createGoal(request);

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.<GoalCreateResponse>builder()
                        .code(1000)
                        .message("Khởi tạo mục tiêu thành công!")
                        .result(result)
                        .build());
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<GoalResponse>>> getActiveGoals() {

        List<GoalResponse> result = goalService.getActiveGoals();

        return ResponseEntity.ok(ApiResponse.<List<GoalResponse>>builder()
                .code(1000)
                .message("Lấy danh sách mục tiêu thành công!")
                .result(result)
                .build());
    }

    @PutMapping("/{goalId}")
    @Operation(summary = " Cập nhật Mục tiêu (Kích hoạt sinh Roadmap version mới)")
    public ResponseEntity<ApiResponse<GoalCreateResponse>> updateGoal(
            @PathVariable Integer goalId, @RequestBody @Valid GoalUpdateRequest request) {

        GoalCreateResponse result = goalService.updateGoal(goalId, request);

        return ResponseEntity.ok(ApiResponse.<GoalCreateResponse>builder()
                .code(1000)
                .message("Cập nhật mục tiêu thành công!")
                .result(result)
                .build());
    }

    @PatchMapping("/tasks/{taskId}/status")
    @Operation(summary = "6. Cập nhật trạng thái Bài tập (Tự động đẻ Lộ trình mới nếu làm hết bài)")
    public ResponseEntity<ApiResponse<Map<String, Object>>> updateTaskStatus(
            @PathVariable Integer taskId, @RequestBody @Valid TaskStatusUpdateRequest request) {

        goalService.updateTaskStatus(taskId, request);

        return ResponseEntity.ok(ApiResponse.<Map<String, Object>>builder()
                .code(1000)
                .message("Đã cập nhật trạng thái bài tập thành công")
                .result(Map.of("taskId", taskId, "status", request.getStatus().toUpperCase()))
                .build());
    }

    @GetMapping("/roadmap")
    @Operation(summary = "Lấy chi tiết Roadmap + Tasks cho tất cả kỹ năng của user hiện tại")
    public ResponseEntity<ApiResponse<List<RoadmapDetailResponse>>> getRoadmapDetails() {

        List<RoadmapDetailResponse> result = goalService.getRoadmapDetails();

        return ResponseEntity.ok(ApiResponse.<List<RoadmapDetailResponse>>builder()
                .code(1000)
                .message("Lấy chi tiết lộ trình thành công!")
                .result(result)
                .build());
    }
}
