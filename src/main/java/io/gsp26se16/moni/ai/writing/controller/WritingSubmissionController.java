package io.gsp26se16.moni.ai.writing.controller;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import io.gsp26se16.moni.ai.writing.dto.SubmitWritingRequest;
import io.gsp26se16.moni.ai.writing.entity.AiEvaluation;
import io.gsp26se16.moni.ai.writing.entity.WritingSubmission;
import io.gsp26se16.moni.ai.writing.repository.AiEvaluationRepository;
import io.gsp26se16.moni.ai.writing.repository.WritingSubmissionRepository;
import io.gsp26se16.moni.authentication.entity.UserCredentials;
import io.gsp26se16.moni.authentication.entity.Users;
import io.gsp26se16.moni.authentication.repository.UserCredentialsRepository;
import io.gsp26se16.moni.common.dto.ApiResponse;
import io.gsp26se16.moni.common.enumeration.EvaluationStatus;
import io.gsp26se16.moni.common.enumeration.Skill;
import io.gsp26se16.moni.common.enumeration.WritingTaskType;
import io.gsp26se16.moni.common.exception.AppException;
import io.gsp26se16.moni.common.exception.ErrorCode;
import io.gsp26se16.moni.content.entity.Stimulus;
import io.gsp26se16.moni.content.entity.Test;
import io.gsp26se16.moni.content.repository.StimulusRepository;
import io.gsp26se16.moni.content.repository.TestRepository;
import io.gsp26se16.moni.expert.repository.ExpertEvaluationRepository;
import io.gsp26se16.moni.expert.repository.ScoringSessionRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

/**
 * Controller xử lý nộp bài Writing và lấy lịch sử nộp bài.
 * Phase 2: Nộp bài không chấm điểm ngay — status = PENDING.
 */
@RestController
@RequestMapping("/api/v1/writing")
@RequiredArgsConstructor
@Tag(name = "Writing", description = "IELTS Writing - Nộp bài và xem lịch sử")
public class WritingSubmissionController {

    private final WritingSubmissionRepository submissionRepository;
    private final UserCredentialsRepository userCredentialsRepository;
    private final StimulusRepository stimulusRepository;
    private final TestRepository testRepository;
    private final ScoringSessionRepository scoringSessionRepository;
    private final ExpertEvaluationRepository expertEvaluationRepository;
    private final AiEvaluationRepository aiEvaluationRepository;

    /**
     * POST /api/v1/writing/submit
     * Lưu bài viết với trạng thái PENDING, trả về submissionId.
     */
    @PostMapping("/submit")
    @Operation(summary = "Nộp bài Writing (chưa chấm điểm)")
    public ResponseEntity<ApiResponse<SubmitWritingResponse>> submitWriting(
            @RequestBody @Valid SubmitWritingRequest request) {

        Users user = getCurrentUser();

        Stimulus stimulus = stimulusRepository
                .findById(request.getStimulusId())
                .orElseThrow(() -> new AppException(ErrorCode.STIMULUS_NOT_FOUND));

        WritingTaskType taskType = request.getTaskType() == 1 ? WritingTaskType.TASK_1 : WritingTaskType.TASK_2;

        WritingSubmission submission = WritingSubmission.builder()
                .testId(request.getTestId())
                .user(user)
                .stimulus(stimulus)
                .taskType(taskType)
                .essayContent(request.getEssayContent())
                .wordCount(request.getWordCount())
                .evaluationStatus(EvaluationStatus.PENDING)
                .build();

        submission = submissionRepository.save(submission);

        SubmitWritingResponse response = new SubmitWritingResponse(
                submission.getId(), submission.getEvaluationStatus().name(), submission.getSubmittedAt());

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.<SubmitWritingResponse>builder()
                        .code(1000)
                        .message("Nộp bài thành công")
                        .result(response)
                        .build());
    }

    /**
     * GET /api/v1/writing/submissions
     * Lấy lịch sử nộp bài Writing của user hiện tại.
     */
    @GetMapping("/submissions")
    @Transactional(readOnly = true)
    @Operation(summary = "Lấy lịch sử nộp bài Writing của user")
    public ResponseEntity<ApiResponse<List<WritingSubmissionSummary>>> getMySubmissions() {
        Users user = getCurrentUser();

        List<WritingSubmissionSummary> submissions =
                submissionRepository.findByUserIdOrderBySubmittedAtDesc(user.getId()).stream()
                        .map(s -> {
                            String testTitle = null;
                            String stimulusTitle = null;

                            // Get test title if available
                            if (s.getTestId() != null) {
                                Test test =
                                        testRepository.findById(s.getTestId()).orElse(null);
                                if (test != null) {
                                    testTitle = test.getTitle();
                                }
                            }

                            // Get stimulus title if available
                            if (s.getStimulus() != null) {
                                stimulusTitle = s.getStimulus().getTitle();
                            }

                            // Lấy overall band nếu đã chấm (ưu tiên AI, fallback Expert)
                            Double overallBand = null;
                            if (s.getEvaluationStatus() == EvaluationStatus.COMPLETED) {
                                overallBand = aiEvaluationRepository.findBySubmissionId(s.getId()).stream()
                                        .filter(e -> Skill.WRITING.equals(e.getSkill()))
                                        .findFirst()
                                        .map(AiEvaluation::getOverallScore)
                                        .orElse(null);
                                if (overallBand == null) {
                                    overallBand = scoringSessionRepository
                                            .findByWritingSubmissionId(s.getId())
                                            .flatMap(sess ->
                                                    expertEvaluationRepository.findByScoringSession_Id(sess.getId()))
                                            .map(ex -> ex.getOverallScore())
                                            .orElse(null);
                                }
                            }

                            return new WritingSubmissionSummary(
                                    s.getId(),
                                    s.getTestId(),
                                    s.getStimulus() != null ? s.getStimulus().getId() : null,
                                    testTitle,
                                    stimulusTitle,
                                    s.getTaskType(),
                                    s.getWordCount(),
                                    s.getEvaluationStatus(),
                                    s.getSubmittedAt(),
                                    overallBand);
                        })
                        .toList();

        return ResponseEntity.ok(ApiResponse.<List<WritingSubmissionSummary>>builder()
                .code(1000)
                .message("Lấy lịch sử nộp bài thành công")
                .result(submissions)
                .build());
    }

    /**
     * GET /api/v1/writing/submissions/{id}
     * Lấy chi tiết một bài nộp (bao gồm nội dung bài viết và kết quả chấm nếu có).
     */
    @GetMapping("/submissions/{id}")
    @Transactional(readOnly = true)
    @Operation(summary = "Lấy chi tiết bài viết theo ID")
    public ResponseEntity<ApiResponse<WritingSubmissionDetail>> getSubmissionDetail(@PathVariable Long id) {

        Users user = getCurrentUser();

        WritingSubmission submission = submissionRepository
                .findById(id)
                .orElseThrow(() -> new AppException(ErrorCode.WRITING_SUBMISSION_NOT_FOUND));

        // Kiểm tra quyền sở hữu
        if (!submission.getUser().getId().equals(user.getId())) {
            throw new AppException(ErrorCode.UNAUTHORIZED);
        }

        Integer stimulusId =
                submission.getStimulus() != null ? submission.getStimulus().getId() : null;

        // Lấy kết quả chấm điểm nếu đã hoàn thành
        WritingEvaluationDetail evaluationDetail = null;
        if (submission.getEvaluationStatus() == EvaluationStatus.COMPLETED) {
            // 1. Try AI evaluation
            List<AiEvaluation> evals = aiEvaluationRepository.findBySubmissionId(id);
            AiEvaluation eval = evals.stream()
                    .filter(e -> Skill.WRITING.equals(e.getSkill()))
                    .findFirst()
                    .orElse(evals.isEmpty() ? null : evals.get(0));
            if (eval != null) {
                evaluationDetail = new WritingEvaluationDetail(
                        eval.getOverallScore(), eval.getAnalysisResult(), eval.getFeedbackResponse());
            }

            // 2. Fallback: try Expert evaluation via ScoringSession
            if (evaluationDetail == null) {
                var expertEval = scoringSessionRepository
                        .findByWritingSubmissionId(id)
                        .flatMap(session -> expertEvaluationRepository.findByScoringSession_Id(session.getId()));
                if (expertEval.isPresent()) {
                    var ex = expertEval.get();

                    // Parse compiled feedback into general summary + per-criterion justifications
                    ExpertFeedbackParsed parsed = parseExpertFeedback(ex.getFeedback());

                    java.util.Map<String, Object> criteriaMap = new java.util.LinkedHashMap<>();
                    criteriaMap.put("TR", buildCriterionEntry(ex.getTaskResponse(), parsed.trJustification));
                    criteriaMap.put("CC", buildCriterionEntry(ex.getCoherence(), parsed.ccJustification));
                    criteriaMap.put("LR", buildCriterionEntry(ex.getLexicalResource(), parsed.lrJustification));
                    criteriaMap.put("GRA", buildCriterionEntry(ex.getGrammaticalRange(), parsed.graJustification));

                    java.util.Map<String, Object> analysis = new java.util.LinkedHashMap<>();
                    analysis.put("criteria", criteriaMap);

                    java.util.Map<String, Object> feedback = new java.util.LinkedHashMap<>();
                    if (parsed.generalSummary != null && !parsed.generalSummary.isBlank()) {
                        feedback.put("summary", parsed.generalSummary);
                    }
                    if (ex.getStrengths() != null && !ex.getStrengths().isBlank()) {
                        feedback.put("strengths", ex.getStrengths());
                    }
                    if (ex.getAreasForImprovement() != null
                            && !ex.getAreasForImprovement().isBlank()) {
                        feedback.put("improvements", ex.getAreasForImprovement());
                    }
                    evaluationDetail = new WritingEvaluationDetail(ex.getOverallScore(), analysis, feedback);
                }
            }
        }

        WritingSubmissionDetail detail = new WritingSubmissionDetail(
                submission.getId(),
                submission.getTestId(),
                stimulusId,
                submission.getTaskType(),
                submission.getEssayContent(),
                submission.getWordCount(),
                submission.getEvaluationStatus(),
                submission.getSubmittedAt(),
                evaluationDetail);

        return ResponseEntity.ok(ApiResponse.<WritingSubmissionDetail>builder()
                .code(1000)
                .message("Lấy chi tiết bài viết thành công")
                .result(detail)
                .build());
    }

    // --- Helpers ---

    /** Build một entry criterion gồm band điểm và justification (nếu có) */
    private java.util.Map<String, Object> buildCriterionEntry(Double band, String justification) {
        java.util.Map<String, Object> entry = new java.util.LinkedHashMap<>();
        entry.put("adjusted_band", band != null ? band : 0.0);
        if (justification != null && !justification.isBlank()) {
            entry.put("justification", justification);
        }
        return entry;
    }

    /** Holder cho kết quả parse compiled feedback của chuyên gia */
    private static class ExpertFeedbackParsed {
        String generalSummary;
        String trJustification;
        String ccJustification;
        String lrJustification;
        String graJustification;
    }

    /**
     * Parse compiled feedback từ expert:
     *   [NHẬN XÉT CHUNG]\n...\n\n[ĐÁNH GIÁ CHI TIẾT]\n- Task Response (TR) (6):\n...
     * Nếu không khớp format, toàn bộ xem như general summary.
     */
    private ExpertFeedbackParsed parseExpertFeedback(String raw) {
        ExpertFeedbackParsed out = new ExpertFeedbackParsed();
        if (raw == null || raw.isBlank()) return out;

        String generalMarker = "[NHẬN XÉT CHUNG]";
        String detailMarker = "[ĐÁNH GIÁ CHI TIẾT]";

        int generalIdx = raw.indexOf(generalMarker);
        int detailIdx = raw.indexOf(detailMarker);

        if (generalIdx == -1 && detailIdx == -1) {
            out.generalSummary = raw.trim();
            return out;
        }

        if (generalIdx != -1) {
            int endGeneral = detailIdx != -1 ? detailIdx : raw.length();
            String g = raw.substring(generalIdx + generalMarker.length(), endGeneral)
                    .trim();
            out.generalSummary = g.isBlank() ? null : g;
        }

        if (detailIdx != -1) {
            String detailSection =
                    raw.substring(detailIdx + detailMarker.length()).trim();
            out.trJustification = extractCriterionBlock(detailSection, "Task Response (TR)");
            out.ccJustification = extractCriterionBlock(detailSection, "Coherence & Cohesion (CC)");
            out.lrJustification = extractCriterionBlock(detailSection, "Lexical Resource (LR)");
            out.graJustification = extractCriterionBlock(detailSection, "Grammatical Range (GRA)");
        }
        return out;
    }

    /** Trích nội dung sau "- <label> (score):" đến trước "\n- " kế tiếp hoặc hết chuỗi. */
    private String extractCriterionBlock(String detailSection, String label) {
        String escaped = java.util.regex.Pattern.quote(label);
        java.util.regex.Pattern p =
                java.util.regex.Pattern.compile("- " + escaped + " \\([^)]*\\):\\s*\\n([\\s\\S]*?)(?=\\n- |$)");
        java.util.regex.Matcher m = p.matcher(detailSection);
        if (m.find()) {
            String content = m.group(1).trim();
            if (content.isBlank() || "Không có nhận xét thêm".equals(content)) return null;
            return content;
        }
        return null;
    }

    private Users getCurrentUser() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || auth.getPrincipal() == null) {
            throw new AppException(ErrorCode.UNAUTHENTICATED);
        }
        String credentialId;
        if (auth.getPrincipal() instanceof Jwt jwt) {
            credentialId = jwt.getClaim("userId");
        } else {
            throw new AppException(ErrorCode.UNAUTHENTICATED);
        }
        if (credentialId == null) throw new AppException(ErrorCode.UNAUTHENTICATED);

        UserCredentials credentials = userCredentialsRepository
                .findById(credentialId)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_EXISTED));
        Users user = credentials.getUser();
        if (user == null) throw new AppException(ErrorCode.USER_NOT_EXISTED);
        return user;
    }

    /** Response DTO khi nộp bài thành công */
    public record SubmitWritingResponse(Long submissionId, String evaluationStatus, LocalDateTime submittedAt) {}

    /** DTO tóm tắt cho lịch sử nộp bài — kèm overallBand nếu đã chấm */
    public record WritingSubmissionSummary(
            Long submissionId,
            Integer testId,
            Integer stimulusId,
            String testTitle,
            String stimulusTitle,
            WritingTaskType taskType,
            Integer wordCount,
            EvaluationStatus evaluationStatus,
            LocalDateTime submittedAt,
            Double overallBand) {}

    /** DTO kết quả chấm điểm AI */
    public record WritingEvaluationDetail(
            Double overallScore, Map<String, Object> analysisResult, Map<String, Object> feedbackResponse) {}

    /** DTO chi tiết bài nộp */
    public record WritingSubmissionDetail(
            Long submissionId,
            Integer testId,
            Integer stimulusId,
            WritingTaskType taskType,
            String essayContent,
            Integer wordCount,
            EvaluationStatus evaluationStatus,
            LocalDateTime submittedAt,
            WritingEvaluationDetail evaluation) {}
}
