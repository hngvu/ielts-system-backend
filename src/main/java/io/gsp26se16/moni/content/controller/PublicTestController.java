package io.gsp26se16.moni.content.controller;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import io.gsp26se16.moni.common.dto.ApiResponse;
import io.gsp26se16.moni.common.enumeration.Skill;
import io.gsp26se16.moni.content.dto.response.TestDetailResponse;
import io.gsp26se16.moni.content.dto.response.TestResponse;
import io.gsp26se16.moni.content.service.TestService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/tests")
@RequiredArgsConstructor
@Tag(name = "Public Tests", description = "API công khai cho user lấy danh sách bài thi PUBLISHED")
public class PublicTestController {

    private final TestService testService;

    @GetMapping
    @Operation(summary = "Get published tests for practice")
    public ResponseEntity<ApiResponse<Page<TestResponse>>> getPublishedTests(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Skill skill,
            @RequestParam(required = false) Integer section,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "12") int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("id").descending());
        Page<TestResponse> result = testService.getPublishedTests(keyword, skill, section, pageable);
        return ResponseEntity.ok(ApiResponse.<Page<TestResponse>>builder()
                .code(1000)
                .message("Lấy danh sách bài thi thành công")
                .result(result)
                .build());
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get test detail for practice")
    public ResponseEntity<ApiResponse<TestDetailResponse>> getTestDetail(@PathVariable Integer id) {
        TestDetailResponse result = testService.getTestDetail(id);
        return ResponseEntity.ok(ApiResponse.<TestDetailResponse>builder()
                .code(1000)
                .message("Lấy chi tiết bài thi thành công")
                .result(result)
                .build());
    }
}
