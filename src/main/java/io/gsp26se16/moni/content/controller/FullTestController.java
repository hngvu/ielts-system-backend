package io.gsp26se16.moni.content.controller;

import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import io.gsp26se16.moni.common.dto.ApiResponse;
import io.gsp26se16.moni.content.dto.request.AutoFullTestRequest;
import io.gsp26se16.moni.content.dto.request.CreateFullTestRequest;
import io.gsp26se16.moni.content.dto.response.FullTestResponse;
import io.gsp26se16.moni.content.dto.response.StimulusOption;
import io.gsp26se16.moni.content.service.FullTestService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/admin/full-tests")
@RequiredArgsConstructor
@PreAuthorize("hasAuthority('ROLE_ADMIN')")
@Tag(name = "Admin Full Test Management", description = "Quản lý đề thi đầy đủ (Full Test) - Admin")
public class FullTestController {

    private final FullTestService fullTestService;

    @GetMapping
    @Operation(summary = "Danh sách tất cả Full Test")
    public ResponseEntity<ApiResponse<List<FullTestResponse>>> listFullTests() {
        List<FullTestResponse> result = fullTestService.listFullTests();
        return ResponseEntity.ok(ApiResponse.<List<FullTestResponse>>builder()
                .code(1000)
                .message("Lấy danh sách full test thành công")
                .result(result)
                .build());
    }

    @PostMapping
    @Operation(summary = "Tạo Full Test thủ công (admin chọn ngữ liệu)")
    public ResponseEntity<ApiResponse<FullTestResponse>> createFullTest(@RequestBody CreateFullTestRequest request) {
        FullTestResponse result = fullTestService.createFullTest(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.<FullTestResponse>builder()
                        .code(1000)
                        .message("Tạo full test thành công")
                        .result(result)
                        .build());
    }

    @PostMapping("/auto")
    @Operation(summary = "Tự động tạo Full Test ngẫu nhiên từ kho bài lẻ")
    public ResponseEntity<ApiResponse<FullTestResponse>> autoGenerateFullTest(
            @RequestBody AutoFullTestRequest request) {
        FullTestResponse result = fullTestService.autoGenerateFullTest(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.<FullTestResponse>builder()
                        .code(1000)
                        .message("Tự động tạo full test thành công")
                        .result(result)
                        .build());
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Xóa Full Test")
    public ResponseEntity<Void> deleteFullTest(@PathVariable Integer id) {
        fullTestService.deleteFullTest(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{id}")
    @Operation(summary = "Lấy chi tiết Full Test theo ID")
    public ResponseEntity<ApiResponse<FullTestResponse>> getFullTestById(@PathVariable Integer id) {
        FullTestResponse result = fullTestService.getFullTestById(id);
        return ResponseEntity.ok(ApiResponse.<FullTestResponse>builder()
                .code(1000)
                .message("Lấy chi tiết full test thành công")
                .result(result)
                .build());
    }

    @PutMapping("/{id}")
    @Operation(summary = "Cập nhật Full Test")
    public ResponseEntity<ApiResponse<FullTestResponse>> updateFullTest(
            @PathVariable Integer id, @RequestBody CreateFullTestRequest request) {
        FullTestResponse result = fullTestService.updateFullTest(id, request);
        return ResponseEntity.ok(ApiResponse.<FullTestResponse>builder()
                .code(1000)
                .message("Cập nhật full test thành công")
                .result(result)
                .build());
    }

    @GetMapping("/available-stimuli")
    @Operation(summary = "Lấy danh sách ngữ liệu có sẵn theo kỹ năng (nhóm theo section)")
    public ResponseEntity<ApiResponse<Map<Integer, List<StimulusOption>>>> getAvailableStimuli(
            @RequestParam String skill) {
        Map<Integer, List<StimulusOption>> result = fullTestService.getAvailableStimuli(skill);
        return ResponseEntity.ok(ApiResponse.<Map<Integer, List<StimulusOption>>>builder()
                .code(1000)
                .message("Lấy danh sách ngữ liệu thành công")
                .result(result)
                .build());
    }
}
