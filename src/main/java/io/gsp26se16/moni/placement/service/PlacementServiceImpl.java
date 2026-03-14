package io.gsp26se16.moni.placement.service;

import java.util.List;

import org.springframework.context.annotation.Lazy;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.gsp26se16.moni.authentication.entity.UserCredentials;
import io.gsp26se16.moni.authentication.entity.Users;
import io.gsp26se16.moni.authentication.repository.UserCredentialsRepository;
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
import io.gsp26se16.moni.roadmap.service.GoalService;
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

    public PlacementServiceImpl(
            PlacementResultRepository placementResultRepository,
            TestRepository testRepository,
            TestService testService,
            QuestionRepository questionRepository,
            UserCredentialsRepository userCredentialsRepository,
            @Lazy GoalService goalService) {
        this.placementResultRepository = placementResultRepository;
        this.testRepository = testRepository;
        this.testService = testService;
        this.questionRepository = questionRepository;
        this.userCredentialsRepository = userCredentialsRepository;
        this.goalService = goalService;
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

        int readingTotal = request.getReadingAnswers().size();
        int listeningTotal = request.getListeningAnswers().size();

        int readingCorrect = gradeAnswers(request.getReadingAnswers());
        int listeningCorrect = gradeAnswers(request.getListeningAnswers());

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

    private int gradeAnswers(List<AnswerRequest> answers) {
        int correct = 0;
        for (AnswerRequest ans : answers) {
            Question question = questionRepository
                    .findById(ans.getQuestionId())
                    .orElseThrow(() -> new AppException(ErrorCode.QUESTION_NOT_FOUND));

            QuestionOption correctOption = findCorrectOption(question);

            if (ans.getSelectedOptionId() != null) {
                var selectedOpt = question.getOptions().stream()
                        .filter(o -> o.getId().equals(ans.getSelectedOptionId()))
                        .findFirst()
                        .orElse(null);
                if (selectedOpt != null && selectedOpt.isCorrect()) {
                    correct++;
                }
            } else if (ans.getAnswerText() != null && correctOption != null) {
                if (ans.getAnswerText()
                        .trim()
                        .equalsIgnoreCase(
                                correctOption.getContent() != null
                                        ? correctOption.getContent().trim()
                                        : "")) {
                    correct++;
                }
            }
        }
        return correct;
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
