package io.gsp26se16.moni.placement.service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

import org.springframework.context.annotation.Lazy;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.gsp26se16.moni.authentication.entity.UserCredentials;
import io.gsp26se16.moni.authentication.entity.Users;
import io.gsp26se16.moni.authentication.repository.UserCredentialsRepository;
import io.gsp26se16.moni.common.enumeration.Skill;
import io.gsp26se16.moni.common.exception.AppException;
import io.gsp26se16.moni.common.exception.ErrorCode;
import io.gsp26se16.moni.content.entity.Question;
import io.gsp26se16.moni.content.entity.QuestionOption;
import io.gsp26se16.moni.content.entity.Test;
import io.gsp26se16.moni.content.repository.QuestionRepository;
import io.gsp26se16.moni.content.repository.TestRepository;
import io.gsp26se16.moni.content.service.TestService;
import io.gsp26se16.moni.placement.dto.request.PlacementSelfAssessRequest;
import io.gsp26se16.moni.placement.dto.request.PlacementSubmitRequest;
import io.gsp26se16.moni.placement.dto.response.PlacementResultResponse;
import io.gsp26se16.moni.placement.dto.response.PlacementTestResponse;
import io.gsp26se16.moni.placement.entity.PlacementResult;
import io.gsp26se16.moni.placement.repository.PlacementResultRepository;
import io.gsp26se16.moni.placement.util.BandScoreUtil;
import io.gsp26se16.moni.practice.dto.request.AnswerRequest;
import io.gsp26se16.moni.roadmap.entity.LearnerMetric;
import io.gsp26se16.moni.roadmap.repository.LearnerMetricRepository;
import io.gsp26se16.moni.roadmap.service.GoalService;
import io.gsp26se16.moni.tag.entity.Tag;
import lombok.extern.slf4j.Slf4j;

@Service
@Transactional
@Slf4j
public class PlacementServiceImpl implements PlacementService {

    private final PlacementResultRepository placementResultRepository;
    private final TestRepository testRepository;
    private final TestService testService;
    private final QuestionRepository questionRepository;
    private final UserCredentialsRepository userCredentialsRepository;
    private final GoalService goalService;
    private final LearnerMetricRepository learnerMetricRepository;

    public PlacementServiceImpl(
            PlacementResultRepository placementResultRepository,
            TestRepository testRepository,
            TestService testService,
            QuestionRepository questionRepository,
            UserCredentialsRepository userCredentialsRepository,
            @Lazy GoalService goalService,
            LearnerMetricRepository learnerMetricRepository) {
        this.placementResultRepository = placementResultRepository;
        this.testRepository = testRepository;
        this.testService = testService;
        this.questionRepository = questionRepository;
        this.userCredentialsRepository = userCredentialsRepository;
        this.goalService = goalService;
        this.learnerMetricRepository = learnerMetricRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public PlacementTestResponse generate() {
        Test readingTest = testRepository
                .findRandomPublishedFullTest("READING")
                .or(() -> testRepository.findRandomPublishedTest("READING"))
                .orElseThrow(() -> new AppException(ErrorCode.PLACEMENT_NO_READING_TEST));

        Test listeningTest = testRepository
                .findRandomPublishedFullTest("LISTENING")
                .or(() -> testRepository.findRandomPublishedTest("LISTENING"))
                .orElseThrow(() -> new AppException(ErrorCode.PLACEMENT_NO_LISTENING_TEST));

        return PlacementTestResponse.builder()
                .readingTest(testService.getTestDetail(readingTest.getId()))
                .listeningTest(testService.getTestDetail(listeningTest.getId()))
                .build();
    }

    @Override
    public PlacementResultResponse submit(PlacementSubmitRequest request) {
        Users user = getCurrentUser();
        validateBand(request.getWritingBand());
        validateBand(request.getSpeakingBand());
        validateBand(request.getTargetBand());

        int readingTotal = testRepository.countQuestionsByTestId(request.getReadingTestId());
        int listeningTotal = testRepository.countQuestionsByTestId(request.getListeningTestId());

        int readingCorrect = gradeAnswers(user, Skill.READING, request.getReadingAnswers());
        int listeningCorrect = gradeAnswers(user, Skill.LISTENING, request.getListeningAnswers());

        double readingBand = BandScoreUtil.readingBand(readingCorrect, readingTotal);
        double listeningBand = BandScoreUtil.listeningBand(listeningCorrect, listeningTotal);
        double overallBand = BandScoreUtil.overallBand(
                readingBand, listeningBand, request.getWritingBand(), request.getSpeakingBand());

        PlacementResult result = PlacementResult.builder()
                .user(user)
                .readingBand(readingBand)
                .listeningBand(listeningBand)
                .writingBand(request.getWritingBand())
                .speakingBand(request.getSpeakingBand())
                .overallBand(overallBand)
                .targetBand(request.getTargetBand())
                .readingCorrect(readingCorrect)
                .listeningCorrect(listeningCorrect)
                .isSelfAssessed(false)
                .build();

        result = placementResultRepository.save(result);

        // Auto-create Goals from placement result
        try {
            goalService.createGoalsFromPlacement(
                    user,
                    readingBand,
                    listeningBand,
                    request.getWritingBand(),
                    request.getSpeakingBand(),
                    user.getTargetReading(),
                    user.getTargetListening(),
                    user.getTargetWriting(),
                    user.getTargetSpeaking(),
                    user.getExamDate());
        } catch (Exception e) {
            log.warn("Không tạo được Goals từ placement result: {}", e.getMessage());
        }

        return toResponse(result);
    }

    @Override
    public PlacementResultResponse selfAssess(PlacementSelfAssessRequest request) {
        Users user = getCurrentUser();
        validateBand(request.getReadingBand());
        validateBand(request.getListeningBand());
        validateBand(request.getWritingBand());
        validateBand(request.getSpeakingBand());
        validateBand(request.getTargetBand());

        double overallBand = BandScoreUtil.overallBand(
                request.getReadingBand(),
                request.getListeningBand(),
                request.getWritingBand(),
                request.getSpeakingBand());

        PlacementResult result = PlacementResult.builder()
                .user(user)
                .readingBand(request.getReadingBand())
                .listeningBand(request.getListeningBand())
                .writingBand(request.getWritingBand())
                .speakingBand(request.getSpeakingBand())
                .overallBand(overallBand)
                .targetBand(request.getTargetBand())
                .isSelfAssessed(true)
                .build();

        result = placementResultRepository.save(result);

        // Auto-create Goals from self-assessed placement result
        try {
            goalService.createGoalsFromPlacement(
                    user,
                    request.getReadingBand(),
                    request.getListeningBand(),
                    request.getWritingBand(),
                    request.getSpeakingBand(),
                    user.getTargetReading(),
                    user.getTargetListening(),
                    user.getTargetWriting(),
                    user.getTargetSpeaking(),
                    user.getExamDate());
        } catch (Exception e) {
            log.warn("Không tạo được Goals từ self-assess result: {}", e.getMessage());
        }

        return toResponse(result);
    }

    @Override
    @Transactional(readOnly = true)
    public PlacementResultResponse getResult() {
        Users user = getCurrentUser();
        return placementResultRepository
                .findFirstByUserOrderByCompletedAtDesc(user)
                .map(this::toResponse)
                .orElse(null);
    }

    @Override
    public void reset() {
        Users user = getCurrentUser();
        placementResultRepository.deleteAllByUser(user);
    }

    // --- Helpers ---

    private int gradeAnswers(Users user, Skill skill, List<AnswerRequest> answers) {
        int correctCount = 0;
        LocalDateTime now = LocalDateTime.now();

        for (AnswerRequest ans : answers) {
            Question question = questionRepository
                    .findById(ans.getQuestionId())
                    .orElseThrow(() -> new AppException(ErrorCode.QUESTION_NOT_FOUND));

            QuestionOption correctOption = findCorrectOption(question);
            boolean isCorrect = false;

            if (ans.getSelectedOptionId() != null) {
                var selectedOpt = question.getOptions().stream()
                        .filter(o -> o.getId().equals(ans.getSelectedOptionId()))
                        .findFirst()
                        .orElse(null);
                if (selectedOpt != null && selectedOpt.isCorrect()) {
                    isCorrect = true;
                }
            } else if (ans.getAnswerText() != null && correctOption != null) {
                if (ans.getAnswerText()
                        .trim()
                        .equalsIgnoreCase(
                                correctOption.getContent() != null
                                        ? correctOption.getContent().trim()
                                        : "")) {
                    isCorrect = true;
                }
            }

            if (isCorrect) correctCount++;

            // ============================================================
            // [AI ENGINE] Cập nhật LearnerMetric ngay từ bài Placement Test
            // ============================================================
            Set<Tag> questionTags = question.getTags();
            if (questionTags != null && !questionTags.isEmpty()) {
                for (Tag tag : questionTags) {
                    LearnerMetric metric = learnerMetricRepository
                            .findByUserAndTag(user, tag)
                            .orElseGet(() -> {
                                LearnerMetric newMetric = new LearnerMetric();
                                newMetric.setUser(user);
                                newMetric.setTag(tag);
                                newMetric.setMasteryLevel(0.3); // Prior
                                newMetric.setConfidenceScore(0.0);
                                newMetric.setAttemptCount(0);

                                // Placement Test chỉ có Reading và Listening
                                newMetric.setPGuess(0.25);
                                newMetric.setPSlip(0.10);
                                newMetric.setPTransit(0.1);
                                return newMetric;
                            });

                    double pL = metric.getMasteryLevel();
                    double pGuess = metric.getPGuess();
                    double pSlip = metric.getPSlip();
                    double pTransit = metric.getPTransit();

                    // Toán học Bayes
                    double pLnew;
                    if (isCorrect) {
                        double pCorrectGivenL = 1.0 - pSlip;
                        double pCorrectGivenNotL = pGuess;
                        double pCorrect = (pL * pCorrectGivenL) + ((1.0 - pL) * pCorrectGivenNotL);
                        pLnew = (pL * pCorrectGivenL) / pCorrect;
                    } else {
                        double pIncorrectGivenL = pSlip;
                        double pIncorrectGivenNotL = 1.0 - pGuess;
                        double pIncorrect = (pL * pIncorrectGivenL) + ((1.0 - pL) * pIncorrectGivenNotL);
                        pLnew = (pL * pIncorrectGivenL) / pIncorrect;
                    }

                    double pLfinal = pLnew + ((1.0 - pLnew) * pTransit);
                    pLfinal = Math.max(0.0, Math.min(1.0, pLfinal));

                    metric.setMasteryLevel(pLfinal);
                    metric.setAttemptCount(metric.getAttemptCount() == null ? 1 : metric.getAttemptCount() + 1);
                    metric.setConfidenceScore(1.0 - (1.0 / (metric.getAttemptCount() + 1.0)));
                    metric.setUpdatedAt(now);

                    learnerMetricRepository.save(metric);
                }
            }
        }
        return correctCount;
    }

    private QuestionOption findCorrectOption(Question question) {
        if (question.getOptions() == null) return null;
        return question.getOptions().stream()
                .filter(QuestionOption::isCorrect)
                .findFirst()
                .orElse(null);
    }

    private void validateBand(Double band) {
        if (!BandScoreUtil.isValidBand(band)) {
            throw new AppException(ErrorCode.PLACEMENT_INVALID_BAND);
        }
    }

    private Users getCurrentUser() {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        String credentialId = null;
        if (authentication.getPrincipal() instanceof Jwt jwt) {
            credentialId = jwt.getClaim("userId");
        }
        if (credentialId == null) {
            throw new AppException(ErrorCode.UNAUTHENTICATED);
        }
        UserCredentials credentials = userCredentialsRepository
                .findById(credentialId)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_EXISTED));
        Users user = credentials.getUser();
        if (user == null) throw new AppException(ErrorCode.USER_NOT_EXISTED);
        return user;
    }

    private PlacementResultResponse toResponse(PlacementResult result) {
        return PlacementResultResponse.builder()
                .id(result.getId())
                .readingBand(result.getReadingBand())
                .listeningBand(result.getListeningBand())
                .writingBand(result.getWritingBand())
                .speakingBand(result.getSpeakingBand())
                .overallBand(result.getOverallBand())
                .targetBand(result.getTargetBand())
                .readingCorrect(result.getReadingCorrect())
                .listeningCorrect(result.getListeningCorrect())
                .isSelfAssessed(result.getIsSelfAssessed())
                .completedAt(result.getCompletedAt())
                .build();
    }
}
