package io.gsp26se16.moni.practice.service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.gsp26se16.moni.authentication.entity.UserCredentials;
import io.gsp26se16.moni.authentication.entity.Users;
import io.gsp26se16.moni.authentication.repository.UserCredentialsRepository;
import io.gsp26se16.moni.common.enumeration.TestSessionStatus;
import io.gsp26se16.moni.common.exception.AppException;
import io.gsp26se16.moni.common.exception.ErrorCode;
import io.gsp26se16.moni.content.entity.Question;
import io.gsp26se16.moni.content.entity.QuestionOption;
import io.gsp26se16.moni.content.entity.Stimulus;
import io.gsp26se16.moni.content.entity.Test;
import io.gsp26se16.moni.content.repository.QuestionOptionRepository;
import io.gsp26se16.moni.content.repository.QuestionRepository;
import io.gsp26se16.moni.content.repository.StimulusRepository;
import io.gsp26se16.moni.content.repository.TestRepository;
import io.gsp26se16.moni.practice.dto.request.AnswerRequest;
import io.gsp26se16.moni.practice.dto.request.SubmitAttemptRequest;
import io.gsp26se16.moni.practice.dto.response.AttemptHistoryResponse;
import io.gsp26se16.moni.practice.dto.response.SubmitAttemptResponse;
import io.gsp26se16.moni.practice.entity.Attempt;
import io.gsp26se16.moni.practice.entity.AttemptAnswer;
import io.gsp26se16.moni.practice.entity.TestSession;
import io.gsp26se16.moni.practice.repository.AttemptAnswerRepository;
import io.gsp26se16.moni.practice.repository.AttemptRepository;
import io.gsp26se16.moni.practice.repository.TestSessionRepository;
import io.gsp26se16.moni.roadmap.entity.LearnerMetric;
import io.gsp26se16.moni.roadmap.repository.LearnerMetricRepository;
import io.gsp26se16.moni.tag.entity.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@Transactional
@RequiredArgsConstructor
@Slf4j
public class PracticeServiceImpl implements PracticeService {

    private final TestSessionRepository testSessionRepository;
    private final AttemptRepository attemptRepository;
    private final AttemptAnswerRepository attemptAnswerRepository;
    private final TestRepository testRepository;
    private final StimulusRepository stimulusRepository;
    private final QuestionRepository questionRepository;
    private final QuestionOptionRepository questionOptionRepository;
    private final UserCredentialsRepository userCredentialsRepository;
    private final LearnerMetricRepository learnerMetricRepository;

    @Override
    public SubmitAttemptResponse submitAttempt(SubmitAttemptRequest request) {
        Users user = getCurrentUser();

        Test test = testRepository
                .findById(request.getTestId())
                .orElseThrow(() -> new AppException(ErrorCode.TEST_NOT_FOUND));

        Stimulus stimulus = stimulusRepository
                .findById(request.getStimulusId())
                .orElseThrow(() -> new AppException(ErrorCode.STIMULUS_NOT_FOUND));

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime startedAt = now.minusSeconds(request.getElapsedSeconds());

        TestSession testSession = new TestSession();
        testSession.setTest(test);
        testSession.setUser(user);
        testSession.setStartedAt(startedAt);
        testSession.setEndedAt(now);
        testSession.setBandScore(0.0);
        testSession.setStatus(TestSessionStatus.SUBMITTED);
        testSession = testSessionRepository.save(testSession);

        Attempt attempt = new Attempt();
        attempt.setTestSession(testSession);
        attempt.setStimulus(stimulus);
        attempt.setUser(user);
        attempt.setStartedAt(startedAt);
        attempt.setSubmittedAt(now);
        attempt.setScore(0);
        attempt.setTotalQuestions(request.getAnswers().size());
        attempt = attemptRepository.save(attempt);

        int correctCount = 0;
        List<SubmitAttemptResponse.AnswerResult> results = new ArrayList<>();

        // 🔥 [AI ENGINE] Khai báo hằng số cho thuật toán EMA
        final double ALPHA = 0.7; // Trọng số: Lịch sử chiếm 70%, Bài vừa làm chiếm 30%

        for (AnswerRequest answerReq : request.getAnswers()) {
            Question question = questionRepository
                    .findById(answerReq.getQuestionId())
                    .orElseThrow(() -> new AppException(ErrorCode.QUESTION_NOT_FOUND));

            QuestionOption correctOption = findCorrectOption(question);
            boolean isCorrect = false;
            QuestionOption selectedOption = null;

            if (answerReq.getSelectedOptionId() != null) {
                selectedOption = questionOptionRepository
                        .findById(answerReq.getSelectedOptionId())
                        .orElseThrow(() -> new AppException(ErrorCode.OPTION_NOT_FOUND));
                isCorrect = selectedOption.isCorrect();
            } else if (answerReq.getAnswerText() != null && correctOption != null) {
                isCorrect = answerReq
                        .getAnswerText()
                        .trim()
                        .equalsIgnoreCase(
                                correctOption.getContent() != null
                                        ? correctOption.getContent().trim()
                                        : "");
            }

            if (isCorrect) correctCount++;

            AttemptAnswer attemptAnswer = new AttemptAnswer();
            attemptAnswer.setAttempt(attempt);
            attemptAnswer.setQuestion(question);
            attemptAnswer.setQuestionOption(selectedOption);
            attemptAnswer.setAnswerText(answerReq.getAnswerText());
            attemptAnswer.setCorrect(isCorrect);
            attemptAnswer.setCreatedAt(now);
            attemptAnswer.setSubmittedAt(now);
            attemptAnswer.setChangeCount(0);
            attemptAnswerRepository.save(attemptAnswer);

            double S = isCorrect ? 1.0 : 0.0; // S: Điểm của câu hỏi này

            // Lấy tất cả các Tag đang gắn vào câu hỏi này (Ví dụ: TFNG, BAND_6.0)
            Set<Tag> questionTags = question.getTags();

            if (questionTags != null && !questionTags.isEmpty()) {
                for (Tag tag : questionTags) {
                    // Bài toán Cold Start: Tìm metric cũ, nếu chưa có thì tạo mới
                    LearnerMetric metric = learnerMetricRepository
                            .findByUserAndTag(user, tag)
                            .orElseGet(() -> {
                                LearnerMetric newMetric = new LearnerMetric();
                                newMetric.setUser(user);
                                newMetric.setTag(tag);
                                newMetric.setMasteryLevel(0.5); // Điểm xuất phát trung bình
                                newMetric.setConfidenceScore(0.0); // Chưa đáng tin vì mới làm lần đầu
                                return newMetric;
                            });

                    // Áp dụng công thức EMA
                    double oldMastery = metric.getMasteryLevel();
                    double newMastery = (oldMastery * ALPHA) + (S * (1.0 - ALPHA));

                    // Đảm bảo điểm không bị tràn khung [0.0 - 1.0]
                    newMastery = Math.max(0.0, Math.min(1.0, newMastery));
                    metric.setMasteryLevel(newMastery);

                    // Tăng độ tin cậy (Confidence) sau mỗi câu trả lời (cộng thêm 5%)
                    double oldConfidence = metric.getConfidenceScore();
                    double newConfidence = Math.min(1.0, oldConfidence + 0.05);
                    metric.setConfidenceScore(newConfidence);

                    // Lưu lại ngay lập tức
                    learnerMetricRepository.save(metric);
                }
            }

            results.add(buildAnswerResult(question, answerReq, selectedOption, isCorrect, correctOption));
        }

        attempt.setScore(correctCount);
        attemptRepository.save(attempt);

        return SubmitAttemptResponse.builder()
                .attemptId(attempt.getId())
                .testSessionId(testSession.getId())
                .score(correctCount)
                .totalQuestions(request.getAnswers().size())
                .elapsedSeconds(request.getElapsedSeconds())
                .results(results)
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public SubmitAttemptResponse getAttemptResult(Integer attemptId) {
        Attempt attempt =
                attemptRepository.findById(attemptId).orElseThrow(() -> new AppException(ErrorCode.ATTEMPT_NOT_FOUND));

        List<AttemptAnswer> answers = attemptAnswerRepository.findAllByAttempt(attempt);
        List<SubmitAttemptResponse.AnswerResult> results = new ArrayList<>();

        for (AttemptAnswer aa : answers) {
            Question question = aa.getQuestion();
            QuestionOption correctOption = findCorrectOption(question);
            AnswerRequest answerReq = AnswerRequest.builder()
                    .questionId(question.getId())
                    .selectedOptionId(
                            aa.getQuestionOption() != null
                                    ? aa.getQuestionOption().getId()
                                    : null)
                    .answerText(aa.getAnswerText())
                    .build();
            results.add(buildAnswerResult(question, answerReq, aa.getQuestionOption(), aa.isCorrect(), correctOption));
        }

        int elapsedSeconds = 0;
        if (attempt.getStartedAt() != null && attempt.getSubmittedAt() != null) {
            elapsedSeconds = (int) java.time.Duration.between(attempt.getStartedAt(), attempt.getSubmittedAt())
                    .getSeconds();
        }

        return SubmitAttemptResponse.builder()
                .attemptId(attempt.getId())
                .testSessionId(
                        attempt.getTestSession() != null
                                ? attempt.getTestSession().getId()
                                : null)
                .score(attempt.getScore())
                .totalQuestions(attempt.getTotalQuestions())
                .elapsedSeconds(elapsedSeconds)
                .results(results)
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public List<AttemptHistoryResponse> getAttemptHistory() {
        Users user = getCurrentUser();
        List<Attempt> attempts = attemptRepository.findByUserOrderBySubmittedAtDesc(user);

        return attempts.stream()
                .map(a -> {
                    int elapsed = 0;
                    if (a.getStartedAt() != null && a.getSubmittedAt() != null) {
                        elapsed = (int) java.time.Duration.between(a.getStartedAt(), a.getSubmittedAt())
                                .getSeconds();
                    }
                    Stimulus s = a.getStimulus();
                    return AttemptHistoryResponse.builder()
                            .attemptId(a.getId())
                            .stimulusId(s != null ? s.getId() : null)
                            .stimulusTitle(s != null ? s.getTitle() : null)
                            .skill(
                                    s != null && s.getSkill() != null
                                            ? s.getSkill().name()
                                            : null)
                            .score(a.getScore())
                            .totalQuestions(a.getTotalQuestions())
                            .elapsedSeconds(elapsed)
                            .submittedAt(a.getSubmittedAt())
                            .build();
                })
                .toList();
    }

    // --- Helpers ---

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

    private QuestionOption findCorrectOption(Question question) {
        if (question.getOptions() == null) return null;
        return question.getOptions().stream()
                .filter(QuestionOption::isCorrect)
                .findFirst()
                .orElse(null);
    }

    private SubmitAttemptResponse.AnswerResult buildAnswerResult(
            Question question,
            AnswerRequest answerReq,
            QuestionOption selectedOption,
            boolean isCorrect,
            QuestionOption correctOption) {

        String explanationText = null;
        String evidenceText = null;
        Map<String, Object> explanationMap = question.getExplanation();
        if (explanationMap != null) {
            Object textObj = explanationMap.get("text");
            if (textObj != null) explanationText = textObj.toString();
            Object evidenceObj = explanationMap.get("evidence");
            if (evidenceObj != null) evidenceText = evidenceObj.toString();
        }

        return SubmitAttemptResponse.AnswerResult.builder()
                .questionId(question.getId())
                .selectedOptionId(answerReq.getSelectedOptionId())
                .answerText(answerReq.getAnswerText())
                .isCorrect(isCorrect)
                .correctOptionId(correctOption != null ? correctOption.getId() : null)
                .correctOptionLabel(correctOption != null ? correctOption.getLabel() : null)
                .correctOptionContent(correctOption != null ? correctOption.getContent() : null)
                .explanation(explanationText)
                .evidence(evidenceText)
                .build();
    }
}
