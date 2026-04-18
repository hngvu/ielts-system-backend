package io.gsp26se16.moni.practice.service.impl;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.gsp26se16.moni.authentication.entity.UserCredentials;
import io.gsp26se16.moni.authentication.entity.Users;
import io.gsp26se16.moni.authentication.repository.UserCredentialsRepository;
import io.gsp26se16.moni.common.enumeration.Skill;
import io.gsp26se16.moni.common.enumeration.TestSessionStatus;
import io.gsp26se16.moni.common.exception.AppException;
import io.gsp26se16.moni.common.exception.ErrorCode;
import io.gsp26se16.moni.content.entity.Question;
import io.gsp26se16.moni.content.entity.QuestionOption;
import io.gsp26se16.moni.content.entity.Stimulus;
import io.gsp26se16.moni.content.entity.Test;
import io.gsp26se16.moni.content.entity.TestStructure;
import io.gsp26se16.moni.content.repository.QuestionOptionRepository;
import io.gsp26se16.moni.content.repository.QuestionRepository;
import io.gsp26se16.moni.content.repository.TestRepository;
import io.gsp26se16.moni.content.repository.TestStructureRepository;
import io.gsp26se16.moni.practice.dto.request.AnswerRequest;
import io.gsp26se16.moni.practice.dto.request.SaveProgressRequest;
import io.gsp26se16.moni.practice.dto.request.StartExamRequest;
import io.gsp26se16.moni.practice.dto.request.SubmitExamRequest;
import io.gsp26se16.moni.practice.dto.response.ExamSessionResponse;
import io.gsp26se16.moni.practice.dto.response.SubmitAttemptResponse;
import io.gsp26se16.moni.practice.entity.Attempt;
import io.gsp26se16.moni.practice.entity.AttemptAnswer;
import io.gsp26se16.moni.practice.entity.TestSession;
import io.gsp26se16.moni.practice.repository.AttemptAnswerRepository;
import io.gsp26se16.moni.practice.repository.AttemptRepository;
import io.gsp26se16.moni.practice.repository.TestSessionRepository;
import io.gsp26se16.moni.practice.service.ExamSessionService;
import io.gsp26se16.moni.roadmap.entity.LearnerMetric;
import io.gsp26se16.moni.roadmap.repository.LearnerMetricRepository;
import io.gsp26se16.moni.tag.entity.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@Transactional
@RequiredArgsConstructor
@Slf4j
public class ExamSessionServiceImpl implements ExamSessionService {

    private final TestSessionRepository testSessionRepository;
    private final AttemptRepository attemptRepository;
    private final AttemptAnswerRepository attemptAnswerRepository;
    private final TestRepository testRepository;
    private final TestStructureRepository testStructureRepository;
    private final QuestionRepository questionRepository;
    private final QuestionOptionRepository questionOptionRepository;
    private final UserCredentialsRepository userCredentialsRepository;
    private final LearnerMetricRepository learnerMetricRepository;

    private static final double EMA_ALPHA = 0.7;

    @Override
    public ExamSessionResponse startExam(StartExamRequest request) {
        Users user = getCurrentUser();

        Test test = testRepository
                .findById(request.getTestId())
                .orElseThrow(() -> new AppException(ErrorCode.TEST_NOT_FOUND));

        if (test.getDuration() == null || test.getDuration() <= 0) {
            throw new AppException(ErrorCode.TEST_NO_DURATION);
        }

        // Check no active session exists
        testSessionRepository
                .findByUserAndTestAndStatus(user, test, TestSessionStatus.IN_PROGRESS)
                .ifPresent(s -> {
                    throw new AppException(ErrorCode.EXAM_SESSION_ALREADY_EXISTS);
                });

        // Get first stimulus via TestStructure
        List<TestStructure> structures = testStructureRepository.findByTestId(test.getId());
        if (structures.isEmpty()) {
            throw new AppException(ErrorCode.STIMULUS_NOT_FOUND);
        }
        Stimulus stimulus = structures.get(0).getStimulus();

        LocalDateTime now = LocalDateTime.now();
        int durationSeconds = test.getDuration() * 60;

        // Create session
        TestSession session = new TestSession();
        session.setTest(test);
        session.setUser(user);
        session.setStartedAt(now);
        session.setStatus(TestSessionStatus.IN_PROGRESS);
        session.setDurationSeconds(durationSeconds);
        session.setBandScore(0.0);
        session = testSessionRepository.save(session);

        // Create attempt
        Attempt attempt = new Attempt();
        attempt.setTestSession(session);
        attempt.setStimulus(stimulus);
        attempt.setUser(user);
        attempt.setStartedAt(now);
        attempt.setScore(0);
        attempt.setTotalQuestions(0);
        attempt = attemptRepository.save(attempt);

        return ExamSessionResponse.builder()
                .sessionId(session.getId())
                .testId(test.getId())
                .status(TestSessionStatus.IN_PROGRESS)
                .startedAt(now)
                .durationSeconds(durationSeconds)
                .remainingSeconds(durationSeconds)
                .attemptId(attempt.getId())
                .savedAnswers(Collections.emptyList())
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public ExamSessionResponse getActiveSession(Integer testId) {
        Users user = getCurrentUser();

        Test test = testRepository.findById(testId).orElseThrow(() -> new AppException(ErrorCode.TEST_NOT_FOUND));

        TestSession session = testSessionRepository
                .findByUserAndTestAndStatus(user, test, TestSessionStatus.IN_PROGRESS)
                .orElse(null);

        if (session == null) {
            return null;
        }

        // Check expiry
        long elapsedSecs = ChronoUnit.SECONDS.between(session.getStartedAt(), LocalDateTime.now());
        int remaining = session.getDurationSeconds() - (int) elapsedSecs;

        if (remaining <= 0) {
            // Auto-expire (needs write transaction)
            expireSession(session);
            return ExamSessionResponse.builder()
                    .sessionId(session.getId())
                    .testId(testId)
                    .status(TestSessionStatus.EXPIRED)
                    .startedAt(session.getStartedAt())
                    .durationSeconds(session.getDurationSeconds())
                    .remainingSeconds(0)
                    .attemptId(null)
                    .savedAnswers(Collections.emptyList())
                    .build();
        }

        Attempt attempt = attemptRepository.findByTestSession(session).orElse(null);

        List<ExamSessionResponse.SavedAnswer> savedAnswers = Collections.emptyList();
        if (attempt != null) {
            List<AttemptAnswer> answers = attemptAnswerRepository.findAllByAttempt(attempt);
            savedAnswers = answers.stream()
                    .map(aa -> ExamSessionResponse.SavedAnswer.builder()
                            .questionId(aa.getQuestion().getId())
                            .selectedOptionId(
                                    aa.getQuestionOption() != null
                                            ? aa.getQuestionOption().getId()
                                            : null)
                            .answerText(aa.getAnswerText())
                            .build())
                    .toList();
        }

        return ExamSessionResponse.builder()
                .sessionId(session.getId())
                .testId(testId)
                .status(TestSessionStatus.IN_PROGRESS)
                .startedAt(session.getStartedAt())
                .durationSeconds(session.getDurationSeconds())
                .remainingSeconds(remaining)
                .attemptId(attempt != null ? attempt.getId() : null)
                .savedAnswers(savedAnswers)
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public List<ExamSessionResponse> getAllActiveSessions() {
        Users user = getCurrentUser();
        List<TestSession> sessions = testSessionRepository.findAllByUserAndStatus(user, TestSessionStatus.IN_PROGRESS);

        LocalDateTime now = LocalDateTime.now();
        return sessions.stream()
                .map(session -> {
                    long elapsedSecs = ChronoUnit.SECONDS.between(session.getStartedAt(), now);
                    int remaining =
                            session.getDurationSeconds() != null ? session.getDurationSeconds() - (int) elapsedSecs : 0;
                    if (remaining <= 0) return null; // expired

                    return ExamSessionResponse.builder()
                            .sessionId(session.getId())
                            .testId(session.getTest().getId())
                            .status(TestSessionStatus.IN_PROGRESS)
                            .startedAt(session.getStartedAt())
                            .durationSeconds(session.getDurationSeconds())
                            .remainingSeconds(remaining)
                            .attemptId(null)
                            .savedAnswers(Collections.emptyList())
                            .build();
                })
                .filter(java.util.Objects::nonNull)
                .toList();
    }

    @Override
    public void saveProgress(SaveProgressRequest request) {
        TestSession session = testSessionRepository
                .findById(request.getSessionId())
                .orElseThrow(() -> new AppException(ErrorCode.EXAM_SESSION_NOT_FOUND));

        if (session.getStatus() != TestSessionStatus.IN_PROGRESS) {
            throw new AppException(ErrorCode.EXAM_SESSION_ALREADY_SUBMITTED);
        }

        // Check expiry
        long elapsedSecs = ChronoUnit.SECONDS.between(session.getStartedAt(), LocalDateTime.now());
        if (elapsedSecs >= session.getDurationSeconds()) {
            throw new AppException(ErrorCode.EXAM_SESSION_EXPIRED);
        }

        Attempt attempt = attemptRepository
                .findByTestSession(session)
                .orElseThrow(() -> new AppException(ErrorCode.ATTEMPT_NOT_FOUND));

        LocalDateTime now = LocalDateTime.now();

        for (AnswerRequest answerReq : request.getAnswers()) {
            Question question = questionRepository
                    .findById(answerReq.getQuestionId())
                    .orElseThrow(() -> new AppException(ErrorCode.QUESTION_NOT_FOUND));

            QuestionOption selectedOption = null;
            if (answerReq.getSelectedOptionId() != null) {
                selectedOption = questionOptionRepository
                        .findById(answerReq.getSelectedOptionId())
                        .orElse(null);
            }

            AttemptAnswer existing = attemptAnswerRepository
                    .findByAttemptAndQuestion(attempt, question)
                    .orElse(null);

            if (existing != null) {
                existing.setQuestionOption(selectedOption);
                existing.setAnswerText(answerReq.getAnswerText());
                existing.setChangeCount(existing.getChangeCount() + 1);
                existing.setSubmittedAt(now);
                attemptAnswerRepository.save(existing);
            } else {
                AttemptAnswer newAnswer = new AttemptAnswer();
                newAnswer.setAttempt(attempt);
                newAnswer.setQuestion(question);
                newAnswer.setQuestionOption(selectedOption);
                newAnswer.setAnswerText(answerReq.getAnswerText());
                newAnswer.setCorrect(false);
                newAnswer.setCreatedAt(now);
                newAnswer.setSubmittedAt(now);
                newAnswer.setChangeCount(0);
                attemptAnswerRepository.save(newAnswer);
            }
        }
    }

    @Override
    public SubmitAttemptResponse submitExam(SubmitExamRequest request) {
        TestSession session = testSessionRepository
                .findById(request.getSessionId())
                .orElseThrow(() -> new AppException(ErrorCode.EXAM_SESSION_NOT_FOUND));

        if (session.getStatus() != TestSessionStatus.IN_PROGRESS) {
            throw new AppException(ErrorCode.EXAM_SESSION_ALREADY_SUBMITTED);
        }

        return gradeAndFinalize(session, TestSessionStatus.SUBMITTED);
    }

    @Override
    public void expireSession(TestSession session) {
        if (session.getStatus() != TestSessionStatus.IN_PROGRESS) {
            return; // Already finalized
        }

        Attempt attempt = attemptRepository.findByTestSession(session).orElse(null);
        if (attempt == null) {
            log.warn(
                    "Expiring TestSession {} but no Attempt was found. Marking as EXPIRED to avoid loop.",
                    session.getId());
            session.setStatus(TestSessionStatus.EXPIRED);
            session.setEndedAt(LocalDateTime.now());
            testSessionRepository.save(session);
            return;
        }

        gradeAndFinalize(session, TestSessionStatus.EXPIRED);
        log.info(
                "Expired exam session {} for test {}",
                session.getId(),
                session.getTest().getId());
    }

    // --- Shared grading + finalization ---

    private SubmitAttemptResponse gradeAndFinalize(TestSession session, TestSessionStatus finalStatus) {
        LocalDateTime now = LocalDateTime.now();

        Attempt attempt = attemptRepository
                .findByTestSession(session)
                .orElseThrow(() -> new AppException(ErrorCode.ATTEMPT_NOT_FOUND));

        List<AttemptAnswer> answers = attemptAnswerRepository.findAllByAttempt(attempt);

        int correctCount = 0;
        List<SubmitAttemptResponse.AnswerResult> results = new ArrayList<>();

        for (AttemptAnswer aa : answers) {
            Question question = aa.getQuestion();
            QuestionOption correctOption = findCorrectOption(question);
            boolean isCorrect = gradeAnswer(aa, correctOption);

            aa.setCorrect(isCorrect);
            aa.setSubmittedAt(now);
            attemptAnswerRepository.save(aa);

            if (isCorrect) correctCount++;

            // EMA mastery update
            Skill testSkill = session.getTest() != null ? session.getTest().getSkill() : null;
            updateMastery(session.getUser(), testSkill, question, isCorrect);

            results.add(buildAnswerResult(question, aa, correctOption, isCorrect));
        }

        // Finalize attempt
        attempt.setScore(correctCount);
        attempt.setTotalQuestions(answers.size());
        attempt.setSubmittedAt(now);
        attemptRepository.save(attempt);

        // Finalize session
        session.setStatus(finalStatus);
        session.setEndedAt(now);
        testSessionRepository.save(session);

        int elapsedSeconds = (int) ChronoUnit.SECONDS.between(session.getStartedAt(), now);

        return SubmitAttemptResponse.builder()
                .attemptId(attempt.getId())
                .testSessionId(session.getId())
                .score(correctCount)
                .totalQuestions(answers.size())
                .elapsedSeconds(elapsedSeconds)
                .results(results)
                .build();
    }

    private boolean gradeAnswer(AttemptAnswer aa, QuestionOption correctOption) {
        if (aa.getQuestionOption() != null) {
            return aa.getQuestionOption().isCorrect();
        }
        if (aa.getAnswerText() != null && correctOption != null && correctOption.getContent() != null) {
            return aa.getAnswerText()
                    .trim()
                    .equalsIgnoreCase(correctOption.getContent().trim());
        }
        return false;
    }

    private QuestionOption findCorrectOption(Question question) {
        if (question.getOptions() == null) return null;
        return question.getOptions().stream()
                .filter(QuestionOption::isCorrect)
                .findFirst()
                .orElse(null);
    }

    private void updateMastery(Users user, Skill skill, Question question, boolean isCorrect) {
        double score = isCorrect ? 1.0 : 0.0;
        Set<Tag> tags = question.getTags();
        if (tags == null || tags.isEmpty()) return;

        for (Tag tag : tags) {
            LearnerMetric metric = learnerMetricRepository
                    .findByUserAndTagAndSkill(user, tag, skill)
                    .orElseGet(() -> {
                        LearnerMetric m = new LearnerMetric();
                        m.setUser(user);
                        m.setTag(tag);
                        m.setSkill(skill);
                        m.setMasteryLevel(0.5);
                        m.setConfidenceScore(0.0);
                        return m;
                    });

            double newMastery =
                    Math.max(0.0, Math.min(1.0, (metric.getMasteryLevel() * EMA_ALPHA) + (score * (1.0 - EMA_ALPHA))));
            metric.setMasteryLevel(newMastery);
            metric.setConfidenceScore(Math.min(1.0, metric.getConfidenceScore() + 0.05));
            learnerMetricRepository.save(metric);
        }
    }

    private SubmitAttemptResponse.AnswerResult buildAnswerResult(
            Question question, AttemptAnswer aa, QuestionOption correctOption, boolean isCorrect) {
        String explanationText = null;
        String evidenceText = null;
        if (question.getExplanation() != null) {
            Object t = question.getExplanation().get("text");
            if (t != null) explanationText = t.toString();
            Object e = question.getExplanation().get("evidence");
            if (e != null) evidenceText = e.toString();
        }

        return SubmitAttemptResponse.AnswerResult.builder()
                .questionId(question.getId())
                .selectedOptionId(
                        aa.getQuestionOption() != null ? aa.getQuestionOption().getId() : null)
                .answerText(aa.getAnswerText())
                .isCorrect(isCorrect)
                .correctOptionId(correctOption != null ? correctOption.getId() : null)
                .correctOptionLabel(correctOption != null ? correctOption.getLabel() : null)
                .correctOptionContent(correctOption != null ? correctOption.getContent() : null)
                .explanation(explanationText)
                .evidence(evidenceText)
                .build();
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
}
