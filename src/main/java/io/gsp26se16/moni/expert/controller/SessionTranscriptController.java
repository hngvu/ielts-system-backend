package io.gsp26se16.moni.expert.controller;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import io.gsp26se16.moni.common.dto.ApiResponse;
import io.gsp26se16.moni.common.exception.AppException;
import io.gsp26se16.moni.common.exception.ErrorCode;
import io.gsp26se16.moni.expert.dto.SessionTranscriptCreateRequest;
import io.gsp26se16.moni.expert.dto.SessionTranscriptResponse;
import io.gsp26se16.moni.expert.service.SessionTranscriptService;
import lombok.RequiredArgsConstructor;

/**
 * Live caption transcript persistence for scoring sessions.
 * - POST: learner or expert of the session appends a batch.
 * - GET: learner/expert of the session, or any ADMIN, reads the full transcript.
 */
@RestController
@RequestMapping("/api/v1/scoring-sessions/{sessionId}/transcripts")
@RequiredArgsConstructor
public class SessionTranscriptController {

    private final SessionTranscriptService transcriptService;

    @PostMapping
    @PreAuthorize("hasAnyRole('LEARNER', 'EXPERT')")
    public ResponseEntity<ApiResponse<List<SessionTranscriptResponse>>> append(
            @PathVariable Integer sessionId, @RequestBody SessionTranscriptCreateRequest request) {
        return ResponseEntity.ok(ApiResponse.<List<SessionTranscriptResponse>>builder()
                .result(transcriptService.append(sessionId, request, getCurrentUserId()))
                .build());
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('LEARNER', 'EXPERT', 'ADMIN')")
    public ResponseEntity<ApiResponse<List<SessionTranscriptResponse>>> list(@PathVariable Integer sessionId) {
        return ResponseEntity.ok(ApiResponse.<List<SessionTranscriptResponse>>builder()
                .result(transcriptService.list(sessionId, getCurrentUserId(), isAdmin()))
                .build());
    }

    private String getCurrentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof Jwt jwt) {
            return jwt.getClaim("userId");
        }
        throw new AppException(ErrorCode.UNAUTHENTICATED);
    }

    private boolean isAdmin() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null) return false;
        return auth.getAuthorities().stream().anyMatch(a -> "ROLE_ADMIN".equals(a.getAuthority()));
    }
}
