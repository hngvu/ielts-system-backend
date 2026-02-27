package io.gsp26se16.moni.content.controller;

import jakarta.validation.Valid;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import io.gsp26se16.moni.common.dto.ApiResponse;
import io.gsp26se16.moni.common.enumeration.Skill;
import io.gsp26se16.moni.content.dto.request.TestImportRequest;
import io.gsp26se16.moni.content.dto.request.TestUpdateRequest;
import io.gsp26se16.moni.content.dto.response.TestDetailResponse;
import io.gsp26se16.moni.content.dto.response.TestResponse;
import io.gsp26se16.moni.content.service.TestService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

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

    @GetMapping
    @Operation(
            summary = "Get List Tests",
            description = "Lấy danh sách đề thi (Phân trang, Tìm kiếm theo tên, Lọc theo kỹ năng)")
    public ResponseEntity<ApiResponse<Page<TestResponse>>> getTests(
            @RequestParam(required = false, defaultValue = "") String keyword,
            @RequestParam(required = false) Skill skill,
            @RequestParam(defaultValue = "1") int page, // Mặc định trang 1
            @RequestParam(defaultValue = "10") int size // Mặc định 10 dòng/trang
            ) {
        // Lưu ý: JPA tính trang bắt đầu từ 0, nên ta lấy (page - 1)
        // Sort theo ID giảm dần (đề mới nhất lên đầu)
        Pageable pageable = PageRequest.of(page - 1, size, Sort.by("id").descending());

        Page<TestResponse> result = testService.getTests(keyword, skill, pageable);

        return ResponseEntity.ok(ApiResponse.<Page<TestResponse>>builder()
                .result(result)
                .message("Get tests successfully")
                .build());
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get Test Detail", description = "Xem chi tiết nội dung đề thi (bao gồm câu hỏi và đáp án)")
    public ResponseEntity<ApiResponse<TestDetailResponse>> getTestDetail(@PathVariable Integer id) {

        TestDetailResponse result = testService.getTestDetail(id);

        return ResponseEntity.ok(ApiResponse.<TestDetailResponse>builder()
                .result(result)
                .message("Get test detail successfully")
                .build());
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update Test Info", description = "Cập nhật Tên, Mô tả, Loại đề thi và Tags")
    public ResponseEntity<ApiResponse<TestDetailResponse>> updateTest(
            @PathVariable Integer id, @RequestBody TestUpdateRequest request) {
        TestDetailResponse updatedTest = testService.updateTest(id, request);

        return ResponseEntity.ok(ApiResponse.<TestDetailResponse>builder()
                .result(updatedTest)
                .message("Update test successfully")
                .build());
    }
}
