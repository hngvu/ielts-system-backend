package io.gsp26se16.moni.roadmap.controller;

import io.gsp26se16.moni.common.dto.ApiResponse;
import io.gsp26se16.moni.roadmap.dto.request.GoalCreateRequest;
import io.gsp26se16.moni.roadmap.dto.request.GoalUpdateRequest;
import io.gsp26se16.moni.roadmap.dto.response.GoalCreateResponse;
import io.gsp26se16.moni.roadmap.dto.response.GoalResponse;
import io.gsp26se16.moni.roadmap.service.GoalService;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/learner/goals")
@RequiredArgsConstructor
public class GoalController {

    private final GoalService goalService;

    @PostMapping
    public ResponseEntity<ApiResponse<GoalCreateResponse>> createGoal(
            @RequestBody @Valid GoalCreateRequest request) {

        GoalCreateResponse result = goalService.createGoal(request);

        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.<GoalCreateResponse>builder()
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
            @PathVariable Integer goalId,
            @RequestBody @Valid GoalUpdateRequest request) {

        GoalCreateResponse result = goalService.updateGoal(goalId, request);

        return ResponseEntity.ok(ApiResponse.<GoalCreateResponse>builder()
                .code(1000)
                .message("Cập nhật mục tiêu thành công!")
                .result(result)
                .build());
    }
}
