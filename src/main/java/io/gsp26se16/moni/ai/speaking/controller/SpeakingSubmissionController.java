package io.gsp26se16.moni.ai.speaking.controller;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.gsp26se16.moni.ai.speaking.entity.SpeakingSubmission;
import io.gsp26se16.moni.ai.speaking.repository.SpeakingSubmissionRepository;
import io.gsp26se16.moni.ai.writing.entity.AiEvaluation;
import io.gsp26se16.moni.ai.writing.repository.AiEvaluationRepository;
import io.gsp26se16.moni.authentication.entity.UserCredentials;
import io.gsp26se16.moni.authentication.repository.UserCredentialsRepository;
import io.gsp26se16.moni.common.dto.ApiResponse;
import io.gsp26se16.moni.common.enumeration.Skill;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RestController
@RequestMapping("/api/v1/speaking/submissions")
@RequiredArgsConstructor
public class SpeakingSubmissionController {

    private final SpeakingSubmissionRepository speakingSubmissionRepository;
    private final AiEvaluationRepository aiEvaluationRepository;
    private final UserCredentialsRepository userCredentialsRepository;

    @GetMapping
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> getMySubmissions() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        String credentialId = null;
        if (auth != null && auth.getPrincipal() instanceof Jwt jwt) {
            credentialId = jwt.getClaimAsString("userId");
        }
        if (credentialId == null) {
            return ResponseEntity.status(401)
                    .body(ApiResponse.<List<Map<String, Object>>>builder()
                            .result(null)
                            .message("Unauthorized")
                            .build());
        }

        // Resolve credential ID → Users entity ID
        String usersId = credentialId;
        UserCredentials cred = userCredentialsRepository.findById(credentialId).orElse(null);
        if (cred != null && cred.getUser() != null) {
            usersId = cred.getUser().getId();
        }

        List<SpeakingSubmission> submissions = speakingSubmissionRepository.findByUserIdOrderBySubmittedAtDesc(usersId);

        List<Map<String, Object>> dtos = new ArrayList<>();
        for (SpeakingSubmission sub : submissions) {
            // Fetch evaluation if exists
            AiEvaluation eval = aiEvaluationRepository
                    .findBySubmissionIdAndSkill(sub.getId(), Skill.SPEAKING)
                    .orElse(null);

            Map<String, Object> testInfo = null;
            if (sub.getTest() != null) {
                try {
                    testInfo = Map.of(
                            "id",
                            sub.getTest().getId(),
                            "title",
                            sub.getTest().getTitle() != null ? sub.getTest().getTitle() : "Speaking Test");
                } catch (Exception e) {
                    log.debug("Failed to load test info for submission {}", sub.getId());
                }
            }

            Map<String, Object> evalInfo = null;
            if (eval != null) {
                evalInfo = Map.of(
                        "overallScore",
                        eval.getOverallScore() != null ? eval.getOverallScore() : 0.0,
                        "analysisResult",
                        eval.getAnalysisResult() != null ? eval.getAnalysisResult() : Map.of());
            }

            Map<String, Object> dto = new java.util.LinkedHashMap<>();
            dto.put("id", sub.getId());
            dto.put("test", testInfo);
            dto.put("evaluationStatus", sub.getEvaluationStatus().name());
            dto.put(
                    "submittedAt",
                    sub.getSubmittedAt() != null ? sub.getSubmittedAt().toString() : null);
            dto.put("evaluation", evalInfo);

            dtos.add(dto);
        }

        return ResponseEntity.ok(
                ApiResponse.<List<Map<String, Object>>>builder().result(dtos).build());
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getSubmission(@PathVariable Long id) {
        SpeakingSubmission submission =
                speakingSubmissionRepository.findById(id).orElse(null);
        if (submission == null) {
            return ResponseEntity.notFound().build();
        }

        AiEvaluation eval = aiEvaluationRepository
                .findBySubmissionIdAndSkill(submission.getId(), Skill.SPEAKING)
                .orElse(null);

        Map<String, Object> dto = new java.util.LinkedHashMap<>();
        dto.put("id", submission.getId());
        dto.put("evaluationStatus", submission.getEvaluationStatus().name());
        dto.put(
                "submittedAt",
                submission.getSubmittedAt() != null
                        ? submission.getSubmittedAt().toString()
                        : null);
        dto.put("audioTranscript", submission.getAudioTranscript());
        dto.put("audioUrl", submission.getAudioUrl()); // JSON array of per-question audio URLs

        // Include test info
        Map<String, Object> testInfo = null;
        if (submission.getTest() != null) {
            try {
                testInfo = Map.of(
                        "id",
                        submission.getTest().getId(),
                        "title",
                        submission.getTest().getTitle() != null
                                ? submission.getTest().getTitle()
                                : "Speaking Test");
            } catch (Exception e) {
                log.debug("Failed to load test info for submission {}", submission.getId());
            }
        }
        dto.put("test", testInfo);

        if (eval != null) {
            dto.put(
                    "evaluation",
                    Map.of(
                            "overallScore",
                            eval.getOverallScore() != null ? eval.getOverallScore() : 0.0,
                            "analysisResult",
                            eval.getAnalysisResult() != null ? eval.getAnalysisResult() : Map.of(),
                            "feedbackResponse",
                            eval.getFeedbackResponse() != null ? eval.getFeedbackResponse() : Map.of()));
        }

        return ResponseEntity.ok(
                ApiResponse.<Map<String, Object>>builder().result(dto).build());
    }
}
