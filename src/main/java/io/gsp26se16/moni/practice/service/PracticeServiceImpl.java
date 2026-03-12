package io.gsp26se16.moni.practice.service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import io.gsp26se16.moni.authentication.repository.UsersRepository;
import io.gsp26se16.moni.roadmap.entity.LearnerMetric;
import io.gsp26se16.moni.roadmap.repository.LearnerMetricRepository;
import io.gsp26se16.moni.tag.entity.Tag;
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
import io.gsp26se16.moni.content.entity.Stimulus;
import io.gsp26se16.moni.content.entity.Test;
import io.gsp26se16.moni.content.repository.QuestionOptionRepository;
import io.gsp26se16.moni.content.repository.QuestionRepository;
import io.gsp26se16.moni.content.repository.StimulusRepository;
import io.gsp26se16.moni.content.repository.TestRepository;
import io.gsp26se16.moni.practice.dto.request.AnswerRequest;
import io.gsp26se16.moni.practice.dto.request.SubmitAttemptRequest;
import io.gsp26se16.moni.practice.dto.response.SubmitAttemptResponse;
import io.gsp26se16.moni.practice.entity.Attempt;
import io.gsp26se16.moni.practice.entity.AttemptAnswer;
import io.gsp26se16.moni.practice.entity.TestSession;
import io.gsp26se16.moni.practice.repository.AttemptAnswerRepository;
import io.gsp26se16.moni.practice.repository.AttemptRepository;
import io.gsp26se16.moni.practice.repository.TestSessionRepository;
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
    private final UsersRepository userRepository;
    private final LearnerMetricRepository learnerMetricRepository;

    @Override
    @Transactional
    public SubmitAttemptResponse submitAttempt(Integer sessionId, Integer attemptId, SubmitAttemptRequest request) {

        Users learner = getCurrentUser();

        // 1. Kiểm tra tồn tại và tính hợp lệ
        TestSession testSession = testSessionRepository.findById(sessionId)
                .orElseThrow(() -> new RuntimeException("Test Session không tồn tại"));

        Attempt attempt = attemptRepository.findById(attemptId)
                .orElseThrow(() -> new RuntimeException("Attempt không tồn tại"));

        if (!attempt.getTestSession().getId().equals(sessionId)) {
            throw new RuntimeException("Attempt này không thuộc về Test Session cung cấp");
        }

        LocalDateTime now = LocalDateTime.now();
        int correctCount = 0;
        List<SubmitAttemptResponse.AnswerResult> results = new ArrayList<>();

        // 2. Chấm điểm từng câu
        for (SubmitAttemptRequest.AnswerRequest answerReq : request.getAnswers()) {
            Question question = questionRepository.findById(answerReq.getQuestionId())
                    .orElseThrow(() -> new RuntimeException("Question không tồn tại"));

            QuestionOption correctOption = findCorrectOption(question);
            boolean isCorrect = false;
            QuestionOption selectedOption = null;

            // Logic chấm điểm giống code cũ của bạn
            if (answerReq.getSelectedOptionId() != null) {
                selectedOption = questionOptionRepository.findById(answerReq.getSelectedOptionId()).orElse(null);
                if (selectedOption != null) {
                    isCorrect = selectedOption.isCorrect(); // Sửa thành getIsCorrect() theo Lombok
                }
            } else if (answerReq.getAnswerText() != null && correctOption != null) {
                isCorrect = answerReq.getAnswerText().trim().equalsIgnoreCase(
                        correctOption.getContent() != null ? correctOption.getContent().trim() : "");
            }

            if (isCorrect) correctCount++;

            // 3. Lưu vào DB (AttemptAnswer)
            AttemptAnswer attemptAnswer = new AttemptAnswer();
            attemptAnswer.setAttempt(attempt);
            attemptAnswer.setQuestion(question);
            attemptAnswer.setSelectedOption(selectedOption); // Sửa tên biến cho đúng Entity
            attemptAnswer.setContent(answerReq.getAnswerText()); // Sửa tên biến cho đúng Entity
            attemptAnswer.setIsCorrect(isCorrect);
            attemptAnswer.setCreatedAt(now);
            attemptAnswer.setUpdatedAt(now);
            attemptAnswer.setChangeCount(0);
            attemptAnswerRepository.save(attemptAnswer);

            // 4. [SMART ROADMAP] Cập nhật Learner Metric ngay lập tức
            updateLearnerMetrics(learner, question, isCorrect);

            results.add(buildAnswerResult(question, answerReq, isCorrect, correctOption));
        }

        // 5. Cập nhật trạng thái Attempt
        attempt.setSubmittedAt(now);
        // Lưu thời gian làm bài (nếu entity chưa có cột này thì bạn có thể bỏ qua hoặc thêm vào)
        attempt.setResult(correctCount + "/" + request.getAnswers().size());
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
        Attempt attempt = attemptRepository.findById(attemptId)
                .orElseThrow(() -> new RuntimeException("Attempt không tồn tại"));

        Users learner = getCurrentUser();

        // Xác thực người xem phải là chủ sở hữu
        if (!attempt.getUser().equals(learner)) {
            throw new RuntimeException("Bạn không có quyền xem kết quả này");
        }

        List<AttemptAnswer> answers = attemptAnswerRepository.findAllByAttemptId(attempt.getId()); // Cần tạo hàm này trong repo
        List<SubmitAttemptResponse.AnswerResult> results = new ArrayList<>();

        for (AttemptAnswer aa : answers) {
            Question question = aa.getQuestion();
            QuestionOption correctOption = findCorrectOption(question);

            SubmitAttemptRequest.AnswerRequest answerReq = SubmitAttemptRequest.AnswerRequest.builder()
                    .questionId(question.getId())
                    .selectedOptionId(aa.getSelectedOption() != null ? aa.getSelectedOption().getId() : null)
                    .answerText(aa.getContent())
                    .build();

            results.add(buildAnswerResult(question, answerReq, aa.getIsCorrect(), correctOption));
        }

        int elapsedSeconds = 0;
        if (attempt.getStartedAt() != null && attempt.getSubmittedAt() != null) {
            elapsedSeconds = (int) java.time.Duration.between(attempt.getStartedAt(), attempt.getSubmittedAt()).getSeconds();
        }

        // Tự động tính số câu đúng từ List results
        int score = (int) results.stream().filter(SubmitAttemptResponse.AnswerResult::isCorrect).count();

        return SubmitAttemptResponse.builder()
                .attemptId(attempt.getId())
                .testSessionId(attempt.getTestSession().getId())
                .score(score)
                .totalQuestions(results.size())
                .elapsedSeconds(elapsedSeconds)
                .results(results)
                .build();
    }

    // --- Helpers ---

    private void updateLearnerMetrics(Users learner, Question question, boolean isCorrect) {
        for (Tag tag : question.getTags()) {
            LearnerMetric metric = learnerMetricRepository.findByUserAndTag(learner, tag)
                    .orElseGet(() -> {
                        LearnerMetric newMetric = new LearnerMetric();
                        newMetric.setUser(learner);
                        newMetric.setTag(tag);
                        newMetric.setMasteryLevel(0.5);
                        return newMetric;
                    });

            double newMastery = isCorrect
                    ? Math.min(1.0, metric.getMasteryLevel() + 0.1)
                    : Math.max(0.0, metric.getMasteryLevel() - 0.05);

            metric.setMasteryLevel(newMastery);
            metric.setUpdatedAt(LocalDateTime.now());
            learnerMetricRepository.save(metric);
        }
    }

    private QuestionOption findCorrectOption(Question question) {
        if (question.getOptions() == null || question.getOptions().isEmpty()) return null;
        return question.getOptions().stream()
                .filter(QuestionOption::isCorrect)
                .findFirst()
                .orElse(null);
    }

    private SubmitAttemptResponse.AnswerResult buildAnswerResult(
            Question question, SubmitAttemptRequest.AnswerRequest answerReq, boolean isCorrect, QuestionOption correctOption) {

        String explanationText = null;
        String evidenceText = null;
        if (question.getExplanation() != null) {
            Object textObj = question.getExplanation().get("text");
            if (textObj != null) explanationText = textObj.toString();

            Object evidenceObj = question.getExplanation().get("evidence");
            if (evidenceObj != null) evidenceText = evidenceObj.toString();
        }

        return SubmitAttemptResponse.AnswerResult.builder()
                .questionId(question.getId())
                .questionContent(question.getContent())
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

    // --- Helper lấy User từ JWT Token ---
    private Users getCurrentUser() {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new RuntimeException("Chưa xác thực (Unauthenticated)");
        }

        String credentialId = null;
        if (authentication.getPrincipal() instanceof org.springframework.security.oauth2.jwt.Jwt jwt) {
            credentialId = jwt.getClaimAsString("userId"); // Tùy thuộc vào claim bạn config trong token
        }

        if (credentialId == null) {
            throw new RuntimeException("Token không hợp lệ (Không tìm thấy userId)");
        }

        UserCredentials credentials = userCredentialsRepository.findById(credentialId)
                .orElseThrow(() -> new RuntimeException("Tài khoản không tồn tại"));

        if (credentials.getUser() == null) {
            throw new RuntimeException("Lỗi dữ liệu: UserCredentials không gắn với Users nào");
        }
        return credentials.getUser();
    }
}
