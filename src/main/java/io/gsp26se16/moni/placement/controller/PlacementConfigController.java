package io.gsp26se16.moni.placement.controller;

import java.util.List;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import io.gsp26se16.moni.common.dto.ApiResponse;
import io.gsp26se16.moni.placement.dto.request.PlacementConfigRequest;
import io.gsp26se16.moni.placement.dto.response.PlacementConfigResponse;
import io.gsp26se16.moni.placement.service.PlacementConfigService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

@RestController
@PreAuthorize("hasAuthority('ROLE_ADMIN')")
@RequestMapping("/api/v1/admin/placement-configs")
@RequiredArgsConstructor
@Tag(name = "Placement Config", description = "Quản lý cấu hình bài kiểm tra trình độ")
public class PlacementConfigController {

    private final PlacementConfigService placementConfigService;

    @GetMapping
    @Operation(summary = "Danh sách tất cả cấu hình placement")
    public ResponseEntity<ApiResponse<List<PlacementConfigResponse>>> listAll() {
        List<PlacementConfigResponse> result = placementConfigService.listAll();
        return ResponseEntity.ok(ApiResponse.<List<PlacementConfigResponse>>builder()
                .code(1000)
                .message("Lấy danh sách cấu hình thành công")
                .result(result)
                .build());
    }

    @PostMapping
    @Operation(summary = "Tạo cấu hình placement mới")
    public ResponseEntity<ApiResponse<PlacementConfigResponse>> create(
            @RequestBody @Valid PlacementConfigRequest request) {
        PlacementConfigResponse result = placementConfigService.create(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.<PlacementConfigResponse>builder()
                        .code(1000)
                        .message("Tạo cấu hình thành công")
                        .result(result)
                        .build());
    }

    @PutMapping("/{id}")
    @Operation(summary = "Cập nhật cấu hình placement")
    public ResponseEntity<ApiResponse<PlacementConfigResponse>> update(
            @PathVariable Integer id, @RequestBody @Valid PlacementConfigRequest request) {
        PlacementConfigResponse result = placementConfigService.update(id, request);
        return ResponseEntity.ok(ApiResponse.<PlacementConfigResponse>builder()
                .code(1000)
                .message("Cập nhật cấu hình thành công")
                .result(result)
                .build());
    }

    @PutMapping("/{id}/activate")
    @Operation(summary = "Kích hoạt cấu hình placement")
    public ResponseEntity<ApiResponse<Void>> activate(@PathVariable Integer id) {
        placementConfigService.activate(id);
        return ResponseEntity.ok(ApiResponse.<Void>builder()
                .code(1000)
                .message("Kích hoạt cấu hình thành công")
                .build());
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Xóa cấu hình placement")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable Integer id) {
        placementConfigService.delete(id);
        return ResponseEntity.ok(ApiResponse.<Void>builder()
                .code(1000)
                .message("Xóa cấu hình thành công")
                .build());
    }
}
