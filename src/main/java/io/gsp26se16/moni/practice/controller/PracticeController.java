package io.gsp26se16.moni.practice.controller;

import io.gsp26se16.moni.practice.dto.request.TestSessionCreateRequest;
import io.gsp26se16.moni.practice.dto.response.TestSessionResponse;
import io.gsp26se16.moni.practice.dto.response.TestSessionResultResponse;
import io.gsp26se16.moni.practice.service.TestSessionService;
import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import io.gsp26se16.moni.common.dto.ApiResponse;
import io.gsp26se16.moni.practice.dto.request.SubmitAttemptRequest;
import io.gsp26se16.moni.practice.dto.response.SubmitAttemptResponse;
import io.gsp26se16.moni.practice.service.PracticeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/learner/test-sessions")
@RequiredArgsConstructor
public class PracticeController {

    private final PracticeService practiceService;
    private final TestSessionService testSessionService;

    @PostMapping("/start")
    @Operation(summary = "1. Bắt đầu bài thi (Tạo Session và các Attempts)")
    public ResponseEntity<ApiResponse<TestSessionResponse>> startTestSession(
            @RequestBody @Valid TestSessionCreateRequest request) {

        Integer learnerId = 1;
        TestSessionResponse result = testSessionService.startTestSession(learnerId, request);

        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.<TestSessionResponse>builder()
                .code(1000).message("Bắt đầu bài thi thành công").result(result).build());
    }

    @PostMapping("/{sessionId}/submit")
    @Operation(summary = "3. Kết thúc toàn bộ bài thi và tính Band Score (Cập nhật Roadmap)")
    public ResponseEntity<ApiResponse<TestSessionResultResponse>> submitTestSession(
            @PathVariable Integer sessionId) {

        TestSessionResultResponse result = testSessionService.submitTestSession(sessionId);

        return ResponseEntity.ok(ApiResponse.<TestSessionResultResponse>builder()
                .code(1000).message("Nộp toàn bộ bài thi thành công").result(result).build());
    }

    @PostMapping("/{sessionId}/attempts/{attemptId}/submit")
    @Operation(summary = "2. Submit practice attempt (Nộp bài một phần thi)")
    public ResponseEntity<ApiResponse<SubmitAttemptResponse>> submitAttempt(
            @PathVariable Integer sessionId,
            @PathVariable Integer attemptId,
            @RequestBody @Valid SubmitAttemptRequest request) {

        SubmitAttemptResponse result = practiceService.submitAttempt(sessionId, attemptId, request);

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.<SubmitAttemptResponse>builder()
                        .code(1000)
                        .message("Nộp bài và chấm điểm thành công")
                        .result(result)
                        .build());
    }

    @GetMapping("/attempts/{attemptId}/result")
    @Operation(summary = "4. Get attempt result (Xem chi tiết giải thích)")
    public ResponseEntity<ApiResponse<SubmitAttemptResponse>> getAttemptResult(
            @PathVariable Integer attemptId) {

        SubmitAttemptResponse result = practiceService.getAttemptResult(attemptId);

        return ResponseEntity.ok(ApiResponse.<SubmitAttemptResponse>builder()
                .code(1000)
                .message("Lấy kết quả chi tiết thành công")
                .result(result)
                .build());
    }


}
