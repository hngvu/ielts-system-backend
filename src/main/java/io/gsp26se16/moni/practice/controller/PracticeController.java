package io.gsp26se16.moni.practice.controller;

import java.util.List;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import io.gsp26se16.moni.common.dto.ApiResponse;
import io.gsp26se16.moni.practice.dto.request.SubmitAttemptRequest;
import io.gsp26se16.moni.practice.dto.response.AttemptHistoryResponse;
import io.gsp26se16.moni.practice.dto.response.SubmitAttemptResponse;
import io.gsp26se16.moni.practice.service.PracticeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/practice")
@RequiredArgsConstructor
@Tag(name = "Practice", description = "IELTS Reading Practice - Submit và xem kết quả")
public class PracticeController {

    private final PracticeService practiceService;

    @PostMapping("/submit")
    @Operation(summary = "Submit reading practice attempt")
    public ResponseEntity<ApiResponse<SubmitAttemptResponse>> submitAttempt(
            @RequestBody @Valid SubmitAttemptRequest request) {
        SubmitAttemptResponse result = practiceService.submitAttempt(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.<SubmitAttemptResponse>builder()
                        .code(1000)
                        .message("Nộp bài thành công")
                        .result(result)
                        .build());
    }

    @GetMapping("/attempts/{attemptId}")
    @Operation(summary = "Get attempt result by ID")
    public ResponseEntity<ApiResponse<SubmitAttemptResponse>> getAttemptResult(@PathVariable Integer attemptId) {
        SubmitAttemptResponse result = practiceService.getAttemptResult(attemptId);
        return ResponseEntity.ok(ApiResponse.<SubmitAttemptResponse>builder()
                .code(1000)
                .message("Lấy kết quả bài làm thành công")
                .result(result)
                .build());
    }

    @GetMapping("/attempts")
    @Operation(summary = "Get user's practice attempt history")
    public ResponseEntity<ApiResponse<List<AttemptHistoryResponse>>> getAttemptHistory() {
        List<AttemptHistoryResponse> history = practiceService.getAttemptHistory();
        return ResponseEntity.ok(ApiResponse.<List<AttemptHistoryResponse>>builder()
                .code(1000)
                .message("Lấy lịch sử làm bài thành công")
                .result(history)
                .build());
    }
}
