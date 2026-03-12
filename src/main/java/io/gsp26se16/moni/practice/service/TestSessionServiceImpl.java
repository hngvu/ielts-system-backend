package io.gsp26se16.moni.practice.service;

import io.gsp26se16.moni.authentication.entity.UserCredentials;
import io.gsp26se16.moni.authentication.entity.Users;
import io.gsp26se16.moni.authentication.repository.UserCredentialsRepository;
import io.gsp26se16.moni.authentication.repository.UsersRepository;
import io.gsp26se16.moni.content.entity.Test;
import io.gsp26se16.moni.content.entity.TestStructure;
import io.gsp26se16.moni.content.repository.QuestionOptionRepository;
import io.gsp26se16.moni.content.repository.QuestionRepository;
import io.gsp26se16.moni.content.repository.TestRepository;
import io.gsp26se16.moni.content.repository.TestStructureRepository;
import io.gsp26se16.moni.practice.dto.request.TestSessionCreateRequest;
import io.gsp26se16.moni.practice.dto.response.TestSessionResponse;
import io.gsp26se16.moni.practice.dto.response.TestSessionResultResponse;
import io.gsp26se16.moni.practice.entity.Attempt;
import io.gsp26se16.moni.practice.entity.AttemptAnswer;
import io.gsp26se16.moni.practice.entity.TestSession;
import io.gsp26se16.moni.practice.repository.AttemptAnswerRepository;
import io.gsp26se16.moni.practice.repository.AttemptRepository;
import io.gsp26se16.moni.practice.repository.TestSessionRepository;
import io.gsp26se16.moni.roadmap.entity.LearnerMetric;
import io.gsp26se16.moni.roadmap.repository.LearnerMetricRepository;
import io.gsp26se16.moni.tag.entity.Tag;
import io.gsp26se16.moni.tag.repository.TagRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Service
@RequiredArgsConstructor
public class TestSessionServiceImpl implements TestSessionService {

    private final TestSessionRepository testSessionRepository;
    private final AttemptRepository attemptRepository;
    private final TestRepository testRepository;
    private final TestStructureRepository testStructureRepository;
    private final UsersRepository userRepository;
    private final LearnerMetricRepository learnerMetricRepository;
    private final AttemptAnswerRepository attemptAnswerRepository;
    private final UserCredentialsRepository userCredentialsRepository;

    @Override
    @Transactional
    public TestSessionResponse startTestSession(Integer userId, TestSessionCreateRequest request) {
        Test test = testRepository.findById(request.getTestId())
                .orElseThrow(() -> new RuntimeException("Test not found"));

        Users learner = getCurrentUser();

        // Tạo Test Session
        TestSession session = new TestSession();
        session.setTest(test);
        session.setUser(learner);
        session.setStartedAt(LocalDateTime.now());
        TestSession savedSession = testSessionRepository.save(session);

        // Lấy cấu trúc đề để tạo Attempt cho TẤT CẢ kỹ năng (Dùng chung cho cả team)
        List<TestStructure> structures = testStructureRepository.findByTestId(test.getId());
        structures.sort(Comparator.comparingInt(TestStructure::getSection));

        List<TestSessionResponse.AttemptDetail> attemptDetails = new ArrayList<>();

        for (TestStructure ts : structures) {
            Attempt attempt = new Attempt();
            attempt.setTestSession(savedSession);
            attempt.setUser(learner);
            attempt.setStimulus(ts.getStimulus());
            attempt.setStartedAt(LocalDateTime.now());

            Attempt savedAttempt = attemptRepository.save(attempt);

            attemptDetails.add(TestSessionResponse.AttemptDetail.builder()
                    .attemptId(savedAttempt.getId())
                    .stimulusId(ts.getStimulus().getId())
                    .section(ts.getSection())
                    .skill(ts.getStimulus().getSkill())
                    .build());
        }

        return TestSessionResponse.builder()
                .sessionId(savedSession.getId())
                .testId(test.getId())
                .testTitle(test.getTitle())
                .startedAt(savedSession.getStartedAt())
                .attempts(attemptDetails)
                .build();
    }

    @Override
    @Transactional
    public TestSessionResultResponse submitTestSession( Integer sessionId) {
        TestSession session = testSessionRepository.findById(sessionId)
                .orElseThrow(() -> new RuntimeException("Test session not found"));

        if (session.getEndedAt() != null) {
            throw new RuntimeException("Bài thi này đã được nộp trước đó!");
        }

        List<Attempt> attempts = attemptRepository.findByTestSessionId(sessionId);

        int totalCorrect = 0;
        int totalQuestions = 0;

        // 1. Quét toàn bộ bài làm để gom điểm và cập nhật Roadmap
        for (Attempt attempt : attempts) {
            List<AttemptAnswer> answers = attemptAnswerRepository.findAllByAttemptId(attempt.getId()); // Cần tạo thêm hàm này trong repo nếu chưa có

            for (AttemptAnswer ans : answers) {
                totalQuestions++;
                boolean isCorrect = Boolean.TRUE.equals(ans.getIsCorrect());
                if (isCorrect) totalCorrect++;
            }
        }

        // 2. Quy đổi câu đúng sang IELTS Band Score (Chuẩn IELTS Reading)
        Double bandScore = calculateIeltsBand(totalCorrect, totalQuestions);

        session.setBand(bandScore);
        session.setEndedAt(LocalDateTime.now());
        testSessionRepository.save(session);

        return TestSessionResultResponse.builder()
                .sessionId(session.getId())
                .overallBand(bandScore)
                .totalCorrect(totalCorrect)
                .totalQuestions(totalQuestions)
                .endedAt(session.getEndedAt())
                .message("Đã nộp bài, tính điểm và cập nhật lộ trình thành công!")
                .build();
    }

    // Hàm tiện ích quy đổi điểm IELTS
    private Double calculateIeltsBand(int correct, int total) {
        if (total == 0) return 0.0;
        double percentage = (double) correct / total;

        if (percentage >= 0.97) return 9.0;
        if (percentage >= 0.92) return 8.5;
        if (percentage >= 0.87) return 8.0;
        if (percentage >= 0.82) return 7.5;
        if (percentage >= 0.75) return 7.0;
        if (percentage >= 0.67) return 6.5;
        if (percentage >= 0.57) return 6.0;
        if (percentage >= 0.47) return 5.5;
        if (percentage >= 0.37) return 5.0;
        if (percentage >= 0.32) return 4.5;
        if (percentage >= 0.25) return 4.0;
        return 0.0;
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
