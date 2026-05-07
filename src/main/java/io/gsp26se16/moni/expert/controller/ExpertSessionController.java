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
import io.gsp26se16.moni.expert.dto.ScoringSessionResponse;
import io.gsp26se16.moni.expert.dto.SubmitEvaluationRequest;
import io.gsp26se16.moni.expert.service.ScoringSessionService;
import lombok.RequiredArgsConstructor;

@RestController
@PreAuthorize("hasAnyRole('EXPERT', 'ADMIN')")
@RequestMapping("/api/v1/expert/sessions")
@RequiredArgsConstructor
public class ExpertSessionController {

    private final ScoringSessionService sessionService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<ScoringSessionResponse>>> listQueuedSessions() {
        String credentialId = getCurrentUserId();
        return ResponseEntity.ok(ApiResponse.<List<ScoringSessionResponse>>builder()
                .result(sessionService.getSessionsForExpert(credentialId))
                .build());
    }

    @GetMapping("/history")
    public ResponseEntity<ApiResponse<List<ScoringSessionResponse>>> listAllSessions() {
        String credentialId = getCurrentUserId();
        return ResponseEntity.ok(ApiResponse.<List<ScoringSessionResponse>>builder()
                .result(sessionService.getAllSessionsForExpert(credentialId))
                .build());
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<ScoringSessionResponse>> getSession(@PathVariable Integer id) {
        String credentialId = getCurrentUserId();
        return ResponseEntity.ok(ApiResponse.<ScoringSessionResponse>builder()
                .result(sessionService.getSessionById(id, credentialId))
                .build());
    }

    @PatchMapping("/{id}/start")
    public ResponseEntity<ApiResponse<ScoringSessionResponse>> startSession(@PathVariable Integer id) {
        String credentialId = getCurrentUserId();
        return ResponseEntity.ok(ApiResponse.<ScoringSessionResponse>builder()
                .result(sessionService.startSession(id, credentialId))
                .build());
    }

    @PostMapping("/{id}/evaluate")
    public ResponseEntity<ApiResponse<ScoringSessionResponse>> submitEvaluation(
            @PathVariable Integer id, @RequestBody SubmitEvaluationRequest request) {
        String credentialId = getCurrentUserId();
        ScoringSessionResponse completed = sessionService.completeSession(id, request, credentialId);
        return ResponseEntity.ok(
                ApiResponse.<ScoringSessionResponse>builder().result(completed).build());
    }

    @PostMapping("/{id}/recording")
    public ResponseEntity<ApiResponse<ScoringSessionResponse>> saveExpertRecording(
            @PathVariable Integer id, @RequestBody Map<String, String> body) {
        String credentialId = getCurrentUserId();
        return ResponseEntity.ok(ApiResponse.<ScoringSessionResponse>builder()
                .result(sessionService.saveExpertRecording(id, body.get("expertRecordingUrl"), credentialId))
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
