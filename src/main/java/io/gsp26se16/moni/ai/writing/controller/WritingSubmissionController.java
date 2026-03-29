package io.gsp26se16.moni.ai.writing.controller;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
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
import io.gsp26se16.moni.content.repository.StimulusRepository;
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
    @Operation(summary = "Lấy lịch sử nộp bài Writing của user")
    public ResponseEntity<ApiResponse<List<WritingSubmissionSummary>>> getMySubmissions() {
        Users user = getCurrentUser();

        List<WritingSubmissionSummary> submissions =
                submissionRepository.findByUserIdOrderBySubmittedAtDesc(user.getId()).stream()
                        .map(s -> new WritingSubmissionSummary(
                                s.getId(),
                                s.getTestId(),
                                s.getTaskType(),
                                s.getWordCount(),
                                s.getEvaluationStatus(),
                                s.getSubmittedAt()))
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
                    java.util.Map<String, Object> analysis = java.util.Map.of(
                            "criteria",
                            java.util.Map.of(
                                    "TR",
                                            java.util.Map.of(
                                                    "adjusted_band",
                                                    ex.getTaskResponse() != null ? ex.getTaskResponse() : 0.0),
                                    "CC",
                                            java.util.Map.of(
                                                    "adjusted_band",
                                                    ex.getCoherence() != null ? ex.getCoherence() : 0.0),
                                    "LR",
                                            java.util.Map.of(
                                                    "adjusted_band",
                                                    ex.getLexicalResource() != null ? ex.getLexicalResource() : 0.0),
                                    "GRA",
                                            java.util.Map.of(
                                                    "adjusted_band",
                                                    ex.getGrammaticalRange() != null
                                                            ? ex.getGrammaticalRange()
                                                            : 0.0)));
                    java.util.Map<String, Object> feedback = new java.util.LinkedHashMap<>();
                    if (ex.getFeedback() != null) feedback.put("summary", ex.getFeedback());
                    if (ex.getStrengths() != null) feedback.put("strengths", ex.getStrengths());
                    if (ex.getAreasForImprovement() != null) feedback.put("improvements", ex.getAreasForImprovement());
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

    /** DTO tóm tắt cho lịch sử nộp bài */
    public record WritingSubmissionSummary(
            Long submissionId,
            Integer testId,
            WritingTaskType taskType,
            Integer wordCount,
            EvaluationStatus evaluationStatus,
            LocalDateTime submittedAt) {}

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
