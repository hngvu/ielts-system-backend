package io.gsp26se16.moni.practice.controller;

import java.util.List;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import io.gsp26se16.moni.common.dto.ApiResponse;
import io.gsp26se16.moni.practice.dto.request.SaveProgressRequest;
import io.gsp26se16.moni.practice.dto.request.StartExamRequest;
import io.gsp26se16.moni.practice.dto.request.SubmitExamRequest;
import io.gsp26se16.moni.practice.dto.response.ExamSessionResponse;
import io.gsp26se16.moni.practice.dto.response.SubmitAttemptResponse;
import io.gsp26se16.moni.practice.service.ExamSessionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

@RestController
@PreAuthorize("hasRole('LEARNER')")
@RequestMapping("/api/v1/practice/exam")
@RequiredArgsConstructor
@Tag(name = "Exam Session", description = "Quản lý phiên thi thử - start, save, submit, resume")
public class ExamSessionController {

    private final ExamSessionService examSessionService;

    @PostMapping("/start")
    @Operation(summary = "Bắt đầu phiên thi mới")
    public ResponseEntity<ApiResponse<ExamSessionResponse>> startExam(@RequestBody @Valid StartExamRequest request) {
        ExamSessionResponse result = examSessionService.startExam(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.<ExamSessionResponse>builder()
                        .code(1000)
                        .message("Bắt đầu phiên thi thành công")
                        .result(result)
                        .build());
    }

    @GetMapping("/active")
    @Operation(summary = "Kiểm tra phiên thi đang diễn ra")
    public ResponseEntity<ApiResponse<ExamSessionResponse>> getActiveSession(@RequestParam Integer testId) {
        ExamSessionResponse result = examSessionService.getActiveSession(testId);
        return ResponseEntity.ok(ApiResponse.<ExamSessionResponse>builder()
                .code(1000)
                .message(result != null ? "Tìm thấy phiên thi" : "Không có phiên thi đang diễn ra")
                .result(result)
                .build());
    }

    @GetMapping("/active-sessions")
    @Operation(summary = "Lấy tất cả phiên thi đang diễn ra của user")
    public ResponseEntity<ApiResponse<List<ExamSessionResponse>>> getAllActiveSessions() {
        List<ExamSessionResponse> result = examSessionService.getAllActiveSessions();
        return ResponseEntity.ok(ApiResponse.<List<ExamSessionResponse>>builder()
                .code(1000)
                .message("OK")
                .result(result)
                .build());
    }

    @PutMapping("/save")
    @Operation(summary = "Lưu tiến trình làm bài")
    public ResponseEntity<ApiResponse<Void>> saveProgress(@RequestBody @Valid SaveProgressRequest request) {
        examSessionService.saveProgress(request);
        return ResponseEntity.ok(ApiResponse.<Void>builder()
                .code(1000)
                .message("Đã lưu tiến trình")
                .build());
    }

    @PostMapping("/submit")
    @Operation(summary = "Nộp bài thi")
    public ResponseEntity<ApiResponse<SubmitAttemptResponse>> submitExam(
            @RequestBody @Valid SubmitExamRequest request) {
        SubmitAttemptResponse result = examSessionService.submitExam(request);
        return ResponseEntity.ok(ApiResponse.<SubmitAttemptResponse>builder()
                .code(1000)
                .message("Nộp bài thành công")
                .result(result)
                .build());
    }
}
