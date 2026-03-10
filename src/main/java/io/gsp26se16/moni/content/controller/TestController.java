package io.gsp26se16.moni.content.controller;

import jakarta.validation.Valid;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import io.gsp26se16.moni.common.dto.ApiResponse;
import io.gsp26se16.moni.common.enumeration.Skill;
import io.gsp26se16.moni.content.dto.request.TestImportRequest;
import io.gsp26se16.moni.content.dto.request.TestStructureRequest;
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
@PreAuthorize("hasAuthority('ROLE_ADMIN')")
@Tag(name = "Admin Test Management", description = "Quản lý đề thi và kho ngữ liệu (Admin)")
public class TestController {

    private final TestService testService;

    /**
     * API Import đề thi trọn gói (Test + Stimulus + Question)
     * Status: 201 CREATED
     */
    @PostMapping("/import")
    @Operation(summary = "Import Full Test")
    public ResponseEntity<ApiResponse<Integer>> importTest(@RequestBody @Valid TestImportRequest request) {
        Integer testId = testService.importTest(request);
        ApiResponse<Integer> response = ApiResponse.<Integer>builder()
                .code(1000)
                .message("Import test successfully")
                .result(testId)
                .build();
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping
    @Operation(summary = "Get All Tests with Pagination & Filter")
    public ResponseEntity<ApiResponse<Page<TestResponse>>> getAllTests(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Skill skill,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "id") String sortBy,
            @RequestParam(defaultValue = "desc") String direction) {
        Sort sort = direction.equalsIgnoreCase("asc")
                ? Sort.by(sortBy).ascending()
                : Sort.by(sortBy).descending();
        Pageable pageable = PageRequest.of(page, size, sort);

        Page<TestResponse> result = testService.getAllTests(keyword, skill, pageable);

        ApiResponse<Page<TestResponse>> response = ApiResponse.<Page<TestResponse>>builder()
                .code(1000)
                .message("Lấy danh sách đề thi thành công")
                .result(result)
                .build();
        return ResponseEntity.ok(response);
    }

    // API 3: LẤY CHI TIẾT ĐỀ THI
    @GetMapping("/{id}")
    @Operation(summary = "Get Test Detail")
    public ResponseEntity<ApiResponse<TestDetailResponse>> getTestDetail(@PathVariable Integer id) {
        TestDetailResponse result = testService.getTestDetail(id);
        ApiResponse<TestDetailResponse> response = ApiResponse.<TestDetailResponse>builder()
                .code(1000)
                .message("Lấy chi tiết đề thi thành công")
                .result(result)
                .build();
        return ResponseEntity.ok(response);
    }

    // API 4: CẬP NHẬT ĐỀ THI
    @PutMapping("/{id}")
    @Operation(summary = "Update Test Info")
    public ResponseEntity<ApiResponse<Void>> updateTest(
            @PathVariable Integer id, @RequestBody @Valid TestUpdateRequest request) {
        testService.updateTest(id, request);
        ApiResponse<Void> response = ApiResponse.<Void>builder()
                .code(1000)
                .message("Cập nhật thông tin đề thi thành công")
                .build();
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Soft Delete Test")
    public ResponseEntity<ApiResponse<Void>> deleteTest(@PathVariable Integer id) {
        testService.deleteTest(id);
        return ResponseEntity.ok(ApiResponse.<Void>builder()
                .code(1000)
                .message("Đã ẩn đề thi thành công")
                .build());
    }

    @PostMapping("/{id}/structure")
    @Operation(summary = "Add Stimulus to Test")
    public ResponseEntity<ApiResponse<Void>> addStimulusToTest(
            @PathVariable Integer id, @RequestBody @Valid TestStructureRequest request) {
        testService.addStimulusToTest(id, request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.<Void>builder()
                        .code(1000)
                        .message("Ghép ngữ liệu vào đề thi thành công")
                        .build());
    }

    @DeleteMapping("/{testId}/structure/{stimulusId}")
    @Operation(summary = "Remove Stimulus from Test")
    public ResponseEntity<ApiResponse<Void>> removeStimulusFromTest(
            @PathVariable Integer testId, @PathVariable Integer stimulusId) {
        testService.removeStimulusFromTest(testId, stimulusId);
        return ResponseEntity.ok(ApiResponse.<Void>builder()
                .code(1000)
                .message("Gỡ ngữ liệu khỏi đề thi thành công")
                .build());
    }
}
