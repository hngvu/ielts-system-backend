package io.gsp26se16.moni.expert.controller;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import io.gsp26se16.moni.common.exception.AppException;
import io.gsp26se16.moni.common.exception.ErrorCode;
import io.gsp26se16.moni.expert.dto.ScoringSessionResponse;
import io.gsp26se16.moni.expert.dto.SubmitEvaluationRequest;
import io.gsp26se16.moni.expert.service.ScoringSessionService;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/expert/sessions")
@RequiredArgsConstructor
public class ExpertSessionController {

    private final ScoringSessionService sessionService;

    @GetMapping
    public ResponseEntity<List<ScoringSessionResponse>> listQueuedSessions() {
        String credentialId = getCurrentUserId();
        return ResponseEntity.ok(sessionService.getSessionsForExpert(credentialId));
    }

    @PatchMapping("/{id}/start")
    public ResponseEntity<ScoringSessionResponse> startSession(@PathVariable Integer id) {
        return ResponseEntity.ok(sessionService.startSession(id));
    }

    @PostMapping("/{id}/evaluate")
    public ResponseEntity<Void> submitEvaluation(
            @PathVariable Integer id, @RequestBody SubmitEvaluationRequest request) {
        sessionService.completeSession(id, request);
        return ResponseEntity.noContent().build();
    }

    private String getCurrentUserId() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof Jwt jwt) {
            return jwt.getClaim("userId");
        }
        throw new AppException(ErrorCode.UNAUTHENTICATED);
    }
}
