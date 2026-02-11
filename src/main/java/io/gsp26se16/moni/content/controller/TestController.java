package io.gsp26se16.moni.content.controller;

import io.gsp26se16.moni.common.dto.ApiResponse;
import io.gsp26se16.moni.content.dto.request.TestImportRequest;
import io.gsp26se16.moni.content.service.TestService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/tests")
@RequiredArgsConstructor
@Tag(name = "Admin Test Management", description = "Quản lý đề thi và kho ngữ liệu (Admin)")
public class TestController {

    private final TestService testService;

    /**
     * API Import đề thi trọn gói (Test + Stimulus + Question)
     * Status: 201 CREATED
     */
    @PostMapping("/import")
    @Operation(summary = "Import Full Test", description = "Tạo đề thi mới kèm theo bài đọc/nghe và câu hỏi")
    public ResponseEntity<ApiResponse<Integer>> importTest(@RequestBody @Valid TestImportRequest request) {

        // Gọi Service xử lý
        Integer testId = testService.importTest(request);

        // Tạo Response chuẩn
        ApiResponse<Integer> response = ApiResponse.<Integer>builder()
                .code(1000)
                .message("Import test successfully")
                .result(testId)
                .build();

        // Trả về HTTP 201 Created
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
}
