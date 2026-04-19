package io.gsp26se16.moni.expert.controller;

import java.util.List;
import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import io.gsp26se16.moni.common.dto.ApiResponse;
import io.gsp26se16.moni.common.exception.AppException;
import io.gsp26se16.moni.common.exception.ErrorCode;
import io.gsp26se16.moni.expert.dto.ExpertProfileResponse;
import io.gsp26se16.moni.expert.dto.UpdateExpertRequest;
import io.gsp26se16.moni.expert.dto.UpdateExpertStatusRequest;
import io.gsp26se16.moni.expert.enumeration.ExpertSpecialization;
import io.gsp26se16.moni.expert.service.ExpertService;
import io.gsp26se16.moni.expert.service.ScoringSessionService;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/experts")
@RequiredArgsConstructor
public class ExpertController {

    private final ExpertService expertService;
    private final ScoringSessionService sessionService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<ExpertProfileResponse>>> listExperts(
            @RequestParam(required = false) ExpertSpecialization specialization) {
        return ResponseEntity.ok(ApiResponse.<List<ExpertProfileResponse>>builder()
                .result(expertService.listExperts(specialization))
                .build());
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<ExpertProfileResponse>> getExpert(@PathVariable Integer id) {
        return ResponseEntity.ok(ApiResponse.<ExpertProfileResponse>builder()
                .result(expertService.getExpert(id))
                .build());
    }

    @GetMapping("/me")
    @PreAuthorize("hasRole('EXPERT')")
    public ResponseEntity<ApiResponse<ExpertProfileResponse>> getMyProfile() {
        return ResponseEntity.ok(ApiResponse.<ExpertProfileResponse>builder()
                .result(expertService.getMyProfile(getCurrentUserId()))
                .build());
    }

    @PutMapping("/me")
    @PreAuthorize("hasRole('EXPERT')")
    public ResponseEntity<ApiResponse<ExpertProfileResponse>> updateMyProfile(
            @RequestBody UpdateExpertRequest request) {
        return ResponseEntity.ok(ApiResponse.<ExpertProfileResponse>builder()
                .result(expertService.updateMyProfile(getCurrentUserId(), request))
                .message("Cập nhật hồ sơ thành công")
                .build());
    }

    @GetMapping("/{id}/reviews")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> getExpertReviews(@PathVariable Integer id) {
        return ResponseEntity.ok(ApiResponse.<List<Map<String, Object>>>builder()
                .result(sessionService.getExpertReviews(id))
                .build());
    }

    @PatchMapping("/me/status")
    @PreAuthorize("hasRole('EXPERT')")
    public ResponseEntity<ApiResponse<ExpertProfileResponse>> updateMyStatus(
            @RequestBody UpdateExpertStatusRequest request) {
        String userId = getCurrentUserId();
        expertService.updateMyStatus(userId, request.getStatus());
        ExpertProfileResponse profile = expertService.getMyProfile(userId);
        return ResponseEntity.ok(ApiResponse.<ExpertProfileResponse>builder()
                .result(profile)
                .message("Cập nhật trạng thái thành công")
                .build());
    }

    private String getCurrentUserId() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof Jwt jwt) {
            return jwt.getClaim("userId");
        }
        throw new AppException(ErrorCode.UNAUTHENTICATED);
    }
}
