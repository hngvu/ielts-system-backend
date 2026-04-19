package io.gsp26se16.moni.placement.controller;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import io.gsp26se16.moni.common.dto.ApiResponse;
import io.gsp26se16.moni.placement.dto.request.AiRecommendRequest;
import io.gsp26se16.moni.placement.dto.request.PlacementSelfAssessRequest;
import io.gsp26se16.moni.placement.dto.request.PlacementSubmitRequest;
import io.gsp26se16.moni.placement.dto.response.AiRecommendResponse;
import io.gsp26se16.moni.placement.dto.response.PlacementResultResponse;
import io.gsp26se16.moni.placement.dto.response.PlacementTestResponse;
import io.gsp26se16.moni.placement.service.PlacementAiService;
import io.gsp26se16.moni.placement.service.PlacementService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

@RestController
@PreAuthorize("hasRole('LEARNER')")
@RequestMapping("/api/v1/placement")
@RequiredArgsConstructor
@Tag(name = "Placement", description = "Kiểm tra trình độ IELTS")
public class PlacementController {

    private final PlacementService placementService;
    private final PlacementAiService placementAiService;

    @PostMapping("/generate")
    @Operation(summary = "Tạo bài kiểm tra trình độ (random Reading + Listening)")
    public ResponseEntity<ApiResponse<PlacementTestResponse>> generate() {
        PlacementTestResponse result = placementService.generate();
        return ResponseEntity.ok(ApiResponse.<PlacementTestResponse>builder()
                .code(1000)
                .message("Tạo bài kiểm tra thành công")
                .result(result)
                .build());
    }

    @PostMapping("/submit")
    @Operation(summary = "Nộp bài kiểm tra trình độ")
    public ResponseEntity<ApiResponse<PlacementResultResponse>> submit(
            @RequestBody @Valid PlacementSubmitRequest request) {
        PlacementResultResponse result = placementService.submit(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.<PlacementResultResponse>builder()
                        .code(1000)
                        .message("Nộp bài kiểm tra thành công")
                        .result(result)
                        .build());
    }

    @PostMapping("/self-assess")
    @Operation(summary = "Tự đánh giá trình độ")
    public ResponseEntity<ApiResponse<PlacementResultResponse>> selfAssess(
            @RequestBody @Valid PlacementSelfAssessRequest request) {
        PlacementResultResponse result = placementService.selfAssess(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.<PlacementResultResponse>builder()
                        .code(1000)
                        .message("Lưu đánh giá thành công")
                        .result(result)
                        .build());
    }

    @GetMapping("/result")
    @Operation(summary = "Lấy kết quả kiểm tra trình độ mới nhất")
    public ResponseEntity<ApiResponse<PlacementResultResponse>> getResult() {
        PlacementResultResponse result = placementService.getResult();
        return ResponseEntity.ok(ApiResponse.<PlacementResultResponse>builder()
                .code(1000)
                .message("Lấy kết quả thành công")
                .result(result)
                .build());
    }

    @DeleteMapping("/reset")
    @Operation(summary = "Xóa kết quả kiểm tra để làm lại")
    public ResponseEntity<ApiResponse<Void>> reset() {
        placementService.reset();
        return ResponseEntity.ok(ApiResponse.<Void>builder()
                .code(1000)
                .message("Đã xóa kết quả kiểm tra")
                .build());
    }

    @PostMapping("/ai-recommend")
    @Operation(summary = "AI gợi ý mục tiêu band điểm dựa trên trình độ và lịch thi")
    public ResponseEntity<ApiResponse<AiRecommendResponse>> aiRecommend(
            @RequestBody @Valid AiRecommendRequest request) {
        AiRecommendResponse result = placementAiService.recommend(request);
        return ResponseEntity.ok(ApiResponse.<AiRecommendResponse>builder()
                .code(1000)
                .message("Gợi ý thành công")
                .result(result)
                .build());
    }
}
