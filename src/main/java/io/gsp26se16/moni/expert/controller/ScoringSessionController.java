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
    @PreAuthorize("hasRole('LEARNER')")
    public ResponseEntity<ApiResponse<ScoringSessionResponse>> createSession(
            @RequestBody CreateSessionRequest request) {
        String credentialId = getCurrentUserId();
        return ResponseEntity.ok(ApiResponse.<ScoringSessionResponse>builder()
                .result(sessionService.createSession(
                        credentialId,
                        request.getExpertId(),
                        request.getSkill(),
                        request.getContent(),
                        request.getTestId(),
                        request.getWritingSubmissionId()))
                .build());
    }

    @PatchMapping("/{id}/cancel")
    @PreAuthorize("hasRole('LEARNER')")
    public ResponseEntity<ApiResponse<ScoringSessionResponse>> cancelSession(@PathVariable Integer id) {
        String credentialId = getCurrentUserId();
        ScoringSessionResponse cancelled = sessionService.cancelSession(id, credentialId);
        return ResponseEntity.ok(
                ApiResponse.<ScoringSessionResponse>builder().result(cancelled).build());
    }

    @GetMapping("/{id}/queue-position")
    @PreAuthorize("hasRole('LEARNER')")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getQueuePosition(@PathVariable Integer id) {
        var info = sessionService.getQueuePositionWithStatus(id);
        return ResponseEntity.ok(
                ApiResponse.<Map<String, Object>>builder().result(info).build());
    }

    @PostMapping("/{id}/rate")
    @PreAuthorize("hasRole('LEARNER')")
    public ResponseEntity<ApiResponse<ScoringSessionResponse>> rateSession(
            @PathVariable Integer id, @RequestBody Map<String, Object> body) {
        int rating = ((Number) body.get("rating")).intValue();
        String comment = (String) body.getOrDefault("comment", "");
        String recordingUrl = (String) body.getOrDefault("recordingUrl", null);
        String credentialId = getCurrentUserId();
        ScoringSessionResponse rated = sessionService.rateSession(id, rating, comment, recordingUrl, credentialId);
        return ResponseEntity.ok(
                ApiResponse.<ScoringSessionResponse>builder().result(rated).build());
    }

    // Allow user to attach their recording AFTER rating (fire-and-forget upload pattern).
    // Called by client once the audio upload finishes, if the user already submitted rating.
    @PatchMapping("/{id}/recording")
    @PreAuthorize("hasRole('LEARNER')")
    public ResponseEntity<ApiResponse<ScoringSessionResponse>> attachRecording(
            @PathVariable Integer id, @RequestBody Map<String, Object> body) {
        String recordingUrl = (String) body.get("recordingUrl");
        String credentialId = getCurrentUserId();
        ScoringSessionResponse updated = sessionService.attachUserRecording(id, recordingUrl, credentialId);
        return ResponseEntity.ok(
                ApiResponse.<ScoringSessionResponse>builder().result(updated).build());
    }

    @GetMapping("/admin/all")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<List<ScoringSessionResponse>>> allSessions() {
        return ResponseEntity.ok(ApiResponse.<List<ScoringSessionResponse>>builder()
                .result(sessionService.getAllSessions())
                .build());
    }

    @GetMapping("/my")
    @PreAuthorize("hasRole('LEARNER')")
    public ResponseEntity<ApiResponse<List<ScoringSessionResponse>>> mySessions() {
        String credentialId = getCurrentUserId();
        return ResponseEntity.ok(ApiResponse.<List<ScoringSessionResponse>>builder()
                .result(sessionService.getUserSessions(credentialId))
                .build());
    }

    @GetMapping("/{id}/evaluation")
    @PreAuthorize("hasRole('LEARNER')")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getEvaluation(@PathVariable Integer id) {
        String credentialId = getCurrentUserId();
        return ResponseEntity.ok(ApiResponse.<Map<String, Object>>builder()
                .result(sessionService.getEvaluation(id, credentialId))
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
