package io.gsp26se16.moni.expert.controller;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import io.gsp26se16.moni.common.dto.ApiResponse;
import io.gsp26se16.moni.expert.dto.CreateExpertRequest;
import io.gsp26se16.moni.expert.dto.ExpertProfileResponse;
import io.gsp26se16.moni.expert.dto.UpdateAccountStatusRequest;
import io.gsp26se16.moni.expert.dto.UpdateExpertRequest;
import io.gsp26se16.moni.expert.dto.UpdateExpertStatusRequest;
import io.gsp26se16.moni.expert.service.ExpertService;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/admin/experts")
@RequiredArgsConstructor
public class AdminExpertController {

    private final ExpertService expertService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<ExpertProfileResponse>>> listExperts() {
        return ResponseEntity.ok(ApiResponse.<List<ExpertProfileResponse>>builder()
                .result(expertService.listExperts(null))
                .build());
    }

    @PostMapping
    public ResponseEntity<ApiResponse<ExpertProfileResponse>> createExpert(@RequestBody CreateExpertRequest request) {
        return ResponseEntity.ok(ApiResponse.<ExpertProfileResponse>builder()
                .result(expertService.createExpert(request))
                .build());
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<ExpertProfileResponse>> getExpert(@PathVariable Integer id) {
        return ResponseEntity.ok(ApiResponse.<ExpertProfileResponse>builder()
                .result(expertService.getExpert(id))
                .build());
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<ExpertProfileResponse>> updateExpert(
            @PathVariable Integer id, @RequestBody UpdateExpertRequest request) {
        return ResponseEntity.ok(ApiResponse.<ExpertProfileResponse>builder()
                .result(expertService.updateExpert(id, request))
                .build());
    }

    @PatchMapping("/{id}/status")
    public ResponseEntity<ApiResponse<ExpertProfileResponse>> updateStatus(
            @PathVariable Integer id, @RequestBody UpdateExpertStatusRequest request) {
        ExpertProfileResponse updated = expertService.updateStatus(id, request.getStatus());
        return ResponseEntity.ok(
                ApiResponse.<ExpertProfileResponse>builder().result(updated).build());
    }

    @PatchMapping("/{id}/account-status")
    public ResponseEntity<ApiResponse<ExpertProfileResponse>> updateAccountStatus(
            @PathVariable Integer id, @RequestBody UpdateAccountStatusRequest request) {
        ExpertProfileResponse updated = expertService.updateAccountStatus(id, request.isEnabled());
        return ResponseEntity.ok(
                ApiResponse.<ExpertProfileResponse>builder().result(updated).build());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteExpert(@PathVariable Integer id) {
        expertService.deleteExpert(id);
        return ResponseEntity.noContent().build();
    }
}
