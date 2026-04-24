package io.gsp26se16.moni.authentication.controller;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.gsp26se16.moni.ai.writing.controller.WritingSubmissionController.WritingSubmissionSummary;
import io.gsp26se16.moni.ai.writing.entity.AiEvaluation;
import io.gsp26se16.moni.ai.writing.repository.AiEvaluationRepository;
import io.gsp26se16.moni.ai.writing.repository.WritingSubmissionRepository;
import io.gsp26se16.moni.authentication.entity.UserCredentials;
import io.gsp26se16.moni.authentication.repository.UserCredentialsRepository;
import io.gsp26se16.moni.common.dto.ApiResponse;
import io.gsp26se16.moni.common.enumeration.EvaluationStatus;
import io.gsp26se16.moni.common.enumeration.Skill;
import io.gsp26se16.moni.common.exception.AppException;
import io.gsp26se16.moni.common.exception.ErrorCode;
import io.gsp26se16.moni.content.entity.Test;
import io.gsp26se16.moni.content.repository.TestRepository;
import io.gsp26se16.moni.expert.dto.ScoringSessionResponse;
import io.gsp26se16.moni.expert.repository.ExpertEvaluationRepository;
import io.gsp26se16.moni.expert.repository.ScoringSessionRepository;
import io.gsp26se16.moni.expert.service.ScoringSessionService;
import io.gsp26se16.moni.practice.dto.response.AttemptHistoryResponse;
import io.gsp26se16.moni.practice.service.PracticeService;
import io.gsp26se16.moni.roadmap.dto.response.LearnerRoadmapInsightsResponse;
import io.gsp26se16.moni.roadmap.dto.response.WeeklyPlanDetailResponse;
import io.gsp26se16.moni.roadmap.dto.response.WeeklyPlanSummaryResponse;
import io.gsp26se16.moni.roadmap.service.GoalService;
import io.gsp26se16.moni.roadmap.service.WeeklyPlanService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

/**
 * Admin-only endpoints để xem chi tiết 1 học viên: lịch sử làm bài, writing, speaking, roadmap.
 * Tất cả path param {id} là credentialId (giống các endpoint admin hiện có trong UserController).
 */
@RestController
@PreAuthorize("hasRole('ADMIN')")
@RequestMapping("/users/{id}")
@RequiredArgsConstructor
@Tag(name = "Admin - User Detail", description = "Xem chi tiết 1 học viên")
public class AdminUserDetailController {

    private final PracticeService practiceService;
    private final GoalService goalService;
    private final WeeklyPlanService weeklyPlanService;
    private final ScoringSessionService scoringSessionService;
    private final WritingSubmissionRepository writingSubmissionRepository;
    private final UserCredentialsRepository userCredentialsRepository;
    private final TestRepository testRepository;
    private final AiEvaluationRepository aiEvaluationRepository;
    private final ScoringSessionRepository scoringSessionRepository;
    private final ExpertEvaluationRepository expertEvaluationRepository;

    @GetMapping("/attempts")
    @Operation(summary = "Lịch sử làm bài practice (Reading/Listening/Writing/Speaking) của học viên")
    public ResponseEntity<ApiResponse<List<AttemptHistoryResponse>>> getAttempts(@PathVariable String id) {
        return ResponseEntity.ok(ApiResponse.<List<AttemptHistoryResponse>>builder()
                .result(practiceService.getAttemptHistoryByCredentialId(id))
                .build());
    }

    @GetMapping("/scoring-sessions")
    @Operation(summary = "Lịch sử phiên chấm bởi Expert của học viên")
    public ResponseEntity<ApiResponse<List<ScoringSessionResponse>>> getScoringSessions(@PathVariable String id) {
        return ResponseEntity.ok(ApiResponse.<List<ScoringSessionResponse>>builder()
                .result(scoringSessionService.getUserSessions(id))
                .build());
    }

    @GetMapping("/roadmap-insights")
    @Operation(summary = "Roadmap insights của học viên")
    public ResponseEntity<ApiResponse<LearnerRoadmapInsightsResponse>> getRoadmapInsights(@PathVariable String id) {
        return ResponseEntity.ok(ApiResponse.<LearnerRoadmapInsightsResponse>builder()
                .result(goalService.getRoadmapInsightsByCredentialId(id))
                .build());
    }

    @GetMapping("/weekly-plan")
    @Operation(summary = "Weekly plan hiện tại của học viên")
    public ResponseEntity<ApiResponse<WeeklyPlanDetailResponse>> getWeeklyPlan(@PathVariable String id) {
        return ResponseEntity.ok(ApiResponse.<WeeklyPlanDetailResponse>builder()
                .result(weeklyPlanService.getCurrentPlanByCredentialId(id))
                .build());
    }

    @GetMapping("/weekly-plan-history")
    @Operation(summary = "Lịch sử weekly plan đã hoàn thành của học viên")
    public ResponseEntity<ApiResponse<List<WeeklyPlanSummaryResponse>>> getWeeklyPlanHistory(@PathVariable String id) {
        return ResponseEntity.ok(ApiResponse.<List<WeeklyPlanSummaryResponse>>builder()
                .result(weeklyPlanService.getHistoryByCredentialId(id))
                .build());
    }

    @GetMapping("/writing-submissions")
    @Transactional(readOnly = true)
    @Operation(summary = "Lịch sử submit Writing của học viên")
    public ResponseEntity<ApiResponse<List<WritingSubmissionSummary>>> getWritingSubmissions(@PathVariable String id) {
        UserCredentials credentials =
                userCredentialsRepository.findById(id).orElseThrow(() -> new AppException(ErrorCode.USER_NOT_EXISTED));
        if (credentials.getUser() == null) {
            throw new AppException(ErrorCode.USER_NOT_EXISTED);
        }
        String userId = credentials.getUser().getId();

        List<WritingSubmissionSummary> submissions =
                writingSubmissionRepository.findByUserIdOrderBySubmittedAtDesc(userId).stream()
                        .map(s -> {
                            String testTitle = null;
                            String stimulusTitle = null;

                            if (s.getTestId() != null) {
                                Test test =
                                        testRepository.findById(s.getTestId()).orElse(null);
                                if (test != null) testTitle = test.getTitle();
                            }
                            if (s.getStimulus() != null) {
                                stimulusTitle = s.getStimulus().getTitle();
                            }

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
                .result(submissions)
                .build());
    }
}
