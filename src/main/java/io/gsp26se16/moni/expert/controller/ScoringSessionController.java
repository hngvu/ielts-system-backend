package io.gsp26se16.moni.expert.controller;

import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import io.gsp26se16.moni.common.dto.ApiResponse;
import io.gsp26se16.moni.common.exception.AppException;
import io.gsp26se16.moni.common.exception.ErrorCode;
import io.gsp26se16.moni.expert.dto.CreateSessionRequest;
import io.gsp26se16.moni.expert.dto.ScoringSessionResponse;
import io.gsp26se16.moni.expert.service.ScoringSessionService;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/scoring-sessions")
@RequiredArgsConstructor
public class ScoringSessionController {

    private final ScoringSessionService sessionService;

    @PostMapping
    public ResponseEntity<ApiResponse<ScoringSessionResponse>> createSession(
            @RequestBody CreateSessionRequest request) {
        String credentialId = getCurrentUserId();
        return ResponseEntity.ok(ApiResponse.<ScoringSessionResponse>builder()
                .result(sessionService.createSession(
                        credentialId, request.getExpertId(), request.getSkill(), request.getContent()))
                .build());
    }

    @PatchMapping("/{id}/cancel")
    public ResponseEntity<Void> cancelSession(@PathVariable Integer id) {
        String credentialId = getCurrentUserId();
        sessionService.cancelSession(id, credentialId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{id}/queue-position")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getQueuePosition(@PathVariable Integer id) {
        var info = sessionService.getQueuePositionWithStatus(id);
        return ResponseEntity.ok(
                ApiResponse.<Map<String, Object>>builder().result(info).build());
    }

    private String getCurrentUserId() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof Jwt jwt) {
            return jwt.getClaim("userId");
        }
        throw new AppException(ErrorCode.UNAUTHENTICATED);
    }
}
