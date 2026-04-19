package io.gsp26se16.moni.ai.speaking.controller;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
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
import io.gsp26se16.moni.content.entity.TestStructure;
import io.gsp26se16.moni.content.repository.TestStructureRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RestController
@PreAuthorize("hasRole('LEARNER')")
@RequestMapping("/api/v1/speaking/submissions")
@RequiredArgsConstructor
public class SpeakingSubmissionController {

    private final SpeakingSubmissionRepository speakingSubmissionRepository;
    private final AiEvaluationRepository aiEvaluationRepository;
    private final UserCredentialsRepository userCredentialsRepository;
    private final TestStructureRepository testStructureRepository;

    /**
     * Resolve the best available title for a submission.
     * Priority: Test title → first Stimulus title → fallback "Speaking Test"
     */
    private Map<String, Object> resolveTestInfo(SpeakingSubmission sub) {
        try {
            if (sub.getTest() != null) {
                String title = sub.getTest().getTitle();

                // If test title is generic, try to get stimulus title instead
                if (title == null || title.isBlank() || "Speaking Test".equals(title)) {
                    List<TestStructure> structures =
                            testStructureRepository.findByTestId(sub.getTest().getId());
                    for (TestStructure ts : structures) {
                        if (ts.getStimulus() != null
                                && ts.getStimulus().getTitle() != null
                                && !ts.getStimulus().getTitle().isBlank()) {
                            title = ts.getStimulus().getTitle();
                            break;
                        }
                    }
                }

                return Map.of(
                        "id",
                        sub.getTest().getId(),
                        "title",
                        title != null && !title.isBlank() ? title : "Speaking Test");
            }
        } catch (Exception e) {
            log.debug("Failed to load test info for submission {}", sub.getId());
        }
        return null;
    }

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

            Map<String, Object> testInfo = resolveTestInfo(sub);

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
        dto.put("test", resolveTestInfo(submission));

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
