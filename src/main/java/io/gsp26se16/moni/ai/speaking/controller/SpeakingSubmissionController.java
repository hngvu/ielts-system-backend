package io.gsp26se16.moni.ai.speaking.controller;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.gsp26se16.moni.ai.speaking.entity.SpeakingSubmission;
import io.gsp26se16.moni.ai.speaking.repository.SpeakingSubmissionRepository;
import io.gsp26se16.moni.common.dto.ApiResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RestController
@RequestMapping("/api/v1/speaking/submissions")
@RequiredArgsConstructor
public class SpeakingSubmissionController {

    private final SpeakingSubmissionRepository speakingSubmissionRepository;

    @GetMapping
    public ResponseEntity<ApiResponse<List<SpeakingSubmission>>> getMySubmissions() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        String userId = null;
        if (auth != null && auth.getPrincipal() instanceof Jwt jwt) {
            userId = jwt.getClaimAsString("userId");
        }
        if (userId == null) {
            return ResponseEntity.status(401)
                    .body(ApiResponse.<List<SpeakingSubmission>>builder()
                            .result(null)
                            .message("Unauthorized")
                            .build());
        }

        List<SpeakingSubmission> submissions = speakingSubmissionRepository.findByUserIdOrderBySubmittedAtDesc(userId);
        return ResponseEntity.ok(ApiResponse.<List<SpeakingSubmission>>builder()
                .result(submissions)
                .build());
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<SpeakingSubmission>> getSubmission(@PathVariable Long id) {
        SpeakingSubmission submission =
                speakingSubmissionRepository.findById(id).orElse(null);
        if (submission == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(
                ApiResponse.<SpeakingSubmission>builder().result(submission).build());
    }
}
