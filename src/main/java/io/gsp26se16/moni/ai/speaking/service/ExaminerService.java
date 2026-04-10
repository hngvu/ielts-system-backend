package io.gsp26se16.moni.ai.speaking.service;

import java.io.IOException;
import java.util.Comparator;
import java.util.LinkedList;
import java.util.Map;
import java.util.Queue;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.gsp26se16.moni.ai.speaking.model.ActiveExamSession;
import io.gsp26se16.moni.common.enumeration.ExamState;
import io.gsp26se16.moni.common.enumeration.Skill;
import io.gsp26se16.moni.common.exception.AppException;
import io.gsp26se16.moni.common.exception.ErrorCode;
import io.gsp26se16.moni.content.entity.Question;
import io.gsp26se16.moni.content.entity.QuestionGroup;
import io.gsp26se16.moni.content.entity.Test;
import io.gsp26se16.moni.content.entity.TestStructure;
import io.gsp26se16.moni.content.repository.TestRepository;
import io.gsp26se16.moni.content.repository.TestStructureRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Điều phối flow buổi thi Speaking:
 *   - Load đề từ DB → phân loại vào queues (Part 1, 2, 3)
 *   - Quyết định câu tiếp theo: MAIN → FOLLOW_UP → next MAIN
 *   - Trigger chuyển Part (1→2→3)
 *   - Gửi event JSON + TTS về client
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ExaminerService {

    private final TestRepository testRepository;
    private final TestStructureRepository testStructureRepository;
    private final ObjectMapper objectMapper;

    // ─────────────────────────────── Public API ──────────────────────────────

    /**
     * Load toàn bộ đề Speaking từ DB vào session queues.
     * Gọi ngay sau khi nhận "start_exam".
     *
     * <p><b>Fix 1</b>: @Transactional giữ Hibernate session mở khi traverse lazy entities.
     * <p><b>Fix 4</b>: Validate testId tồn tại và skill = SPEAKING.
     */
    @Transactional(readOnly = true)
    public void loadExam(ActiveExamSession session) {
        // ── Fix 4: Validate test ──────────────────────────────────────────
        Test test = testRepository
                .findById(session.getTestId())
                .orElseThrow(() -> new AppException(ErrorCode.TEST_NOT_FOUND));

        if (test.getSkill() != Skill.SPEAKING) {
            throw new IllegalArgumentException(
                    "Test " + session.getTestId() + " is not a SPEAKING test (skill=" + test.getSkill() + ")");
        }

        // ── Load questions vào queues ─────────────────────────────────────
        var structures = testStructureRepository.findByTestId(session.getTestId());

        for (TestStructure ts : structures) {
            int section = ts.getSection();
            if (ts.getStimulus() == null) continue;

            for (QuestionGroup group : ts.getStimulus().getQuestionGroups()) {
                group.getQuestions().stream()
                        .filter(q -> q.getParentQuestion() == null) // chỉ lấy MAIN
                        .sorted(Comparator.comparingInt(Question::getPosition))
                        .forEach(q -> {
                            assignToQueue(session, section, q);
                            // Pre-cache follow-ups while Hibernate session is still open
                            if (q.getFollowUpQuestions() != null
                                    && !q.getFollowUpQuestions().isEmpty()) {
                                Queue<Question> fQueue = new LinkedList<>();
                                q.getFollowUpQuestions().stream()
                                        .sorted(Comparator.comparingInt(Question::getPosition))
                                        .forEach(fQueue::add);
                                session.getPreloadedFollowUps().put(q.getId(), fQueue);
                            }
                        });
            }
        }

        log.info(
                "Loaded exam testId={}: Part1={}, Part2={}, Part3={}",
                session.getTestId(),
                session.getPart1Queue().size(),
                session.getPart2Question() != null ? "OK" : "MISSING",
                session.getPart3Queue().size());
    }

    /** Bắt đầu Part 1 — TTS câu hỏi đầu tiên */
    public void startPart1(ActiveExamSession session) throws IOException {
        session.setState(ExamState.PART1_QUESTIONING);
        askNextQuestion(session);
    }

    /**
     * Nhận transcript từ client, lưu và hỏi câu tiếp theo.
     * Dùng cho Part 1 và Part 3.
     */
    public void handleTranscript(ActiveExamSession session, Integer questionId, String transcript, String audioUrl)
            throws IOException {
        // Get current question content for AI evaluation context
        String questionContent = "";
        if (session.getCurrentQuestion() != null) {
            questionContent = session.getCurrentQuestion().getContent();
        }

        if (session.getState() == ExamState.PART1_QUESTIONING) {
            session.addPart1Transcript(questionId, questionContent, transcript, audioUrl);
        } else if (session.getState() == ExamState.PART3_QUESTIONING) {
            session.addPart3Transcript(questionId, questionContent, transcript, audioUrl);
        }
        askNextQuestion(session);
    }

    /** Client báo sẵn sàng nói Part 2 (sau 60s chuẩn bị) */
    public void startPart2Speaking(ActiveExamSession session) {
        session.setState(ExamState.PART2_SPEAKING);
        log.info("Session {}: Part 2 speaking started", session.getSessionId());
    }

    /**
     * Client báo xong Part 2 (hết 120s hoặc im lặng).
     */
    public void stopPart2Speaking(ActiveExamSession session, String transcript, String audioUrl) throws IOException {
        session.setPart2Transcript(transcript != null ? transcript : "");
        session.setPart2AudioUrl(audioUrl != null ? audioUrl : "");
        // Save Part 2 question content for AI evaluation
        if (session.getPart2Question() != null) {
            session.setPart2QuestionContent(session.getPart2Question().getContent());
        }
        session.setState(ExamState.PART3_QUESTIONING);

        WebSocketSession ws = session.getWsSession();

        // Lấy câu dẫn Part 3
        Question transScript = session.getPart3TransitionScript();
        String introText = transScript != null ? transScript.getContent() : "Now let's move on to Part 3.";

        // Thay vì gọi 2 lần ttsAsync (gây lồng tiếng), ta gộp intro và câu hỏi đầu tiên thành 1 chuỗi để đọc
        if (!session.getPart3Queue().isEmpty()) {
            Question q = session.getPart3Queue().poll();
            session.setCurrentQuestion(q);
            loadFollowUps(session, q);
            sendQuestionEvent(ws, 3, q.getId(), q.getContent(), false);
        } else {
            endExam(session);
        }
    }

    // ─────────────────────────────── Core logic ──────────────────────────────

    private void askNextQuestion(ActiveExamSession session) throws IOException {
        WebSocketSession ws = session.getWsSession();

        if (session.getState() == ExamState.PART1_QUESTIONING) {
            // Còn follow-up của câu hiện tại?
            if (!session.getFollowUpQueue().isEmpty()) {
                Question q = session.getFollowUpQueue().poll();
                session.setCurrentQuestion(q);
                sendQuestionEvent(ws, 1, q.getId(), q.getContent(), true);
                return;
            }
            // Còn MAIN question?
            if (!session.getPart1Queue().isEmpty()) {
                Question q = session.getPart1Queue().poll();
                session.setCurrentQuestion(q);
                loadFollowUps(session, q);
                sendQuestionEvent(ws, 1, q.getId(), q.getContent(), false);
                return;
            }
            // Hết Part 1 → chuyển Part 2
            transitionToPart2(session);
            return;
        }

        if (session.getState() == ExamState.PART3_QUESTIONING) {
            if (!session.getFollowUpQueue().isEmpty()) {
                Question q = session.getFollowUpQueue().poll();
                session.setCurrentQuestion(q);
                sendQuestionEvent(ws, 3, q.getId(), q.getContent(), true);
                return;
            }
            if (!session.getPart3Queue().isEmpty()) {
                Question q = session.getPart3Queue().poll();
                session.setCurrentQuestion(q);
                loadFollowUps(session, q);
                sendQuestionEvent(ws, 3, q.getId(), q.getContent(), false);
                return;
            }
            // Hết Part 3 → kết thúc
            endExam(session);
        }
    }

    private void transitionToPart2(ActiveExamSession session) throws IOException {
        session.setState(ExamState.TRANSITIONING_TO_PART2);
        WebSocketSession ws = session.getWsSession();

        // Bỏ ttsAsync cho Part 2 ở đây vì Frontend đã tự động đọc giới thiệu bằng Browser TTS
        // khi hiện thẻ Cue Card để đồng bộ với timer 1 phút.

        // Gửi show_cue_card về client
        Question part2Q = session.getPart2Question();
        if (part2Q != null) {
            String event = objectMapper.writeValueAsString(Map.of(
                    "type",
                    "show_cue_card",
                    "duration",
                    60,
                    "questionId",
                    part2Q.getId(),
                    "topic",
                    part2Q.getContent()));
            ws.sendMessage(new TextMessage(event));
        }

        session.setState(ExamState.PART2_PREPARATION);
    }

    private void endExam(ActiveExamSession session) throws IOException {
        session.setState(ExamState.EVALUATING);
        session.getWsSession()
                .sendMessage(new TextMessage(objectMapper.writeValueAsString(Map.of("type", "evaluating"))));
        log.info("Session {}: all parts done, ready for evaluation", session.getSessionId());
    }

    // ─────────────────────────────── Helpers ─────────────────────────────────

    /**
     * Phân loại question vào đúng queue.
     * Phân biệt Part 2 cue card vs transition script bằng position:
     *   - position == 0 → transition script
     *   - position > 0  → cue card (Part 2) hoặc câu hỏi (Part 1/3)
     */
    private void assignToQueue(ActiveExamSession session, int section, Question question) {

        switch (section) {
            case 1 -> session.getPart1Queue().add(question);
            case 2 -> {
                // Transition script vs cue card phân biệt qua position
                if (question.getPosition() == 0) {
                    session.setPart2TransitionScript(question);
                } else {
                    session.setPart2Question(question);
                }
            }
            case 3 -> {
                if (question.getPosition() == 0) {
                    session.setPart3TransitionScript(question);
                } else {
                    session.getPart3Queue().add(question);
                }
            }
        }
    }

    /** Load follow-up questions từ pre-cached map (đã load trong @Transactional) */
    private void loadFollowUps(ActiveExamSession session, Question mainQuestion) {
        Queue<Question> cached = session.getPreloadedFollowUps().get(mainQuestion.getId());
        if (cached != null && !cached.isEmpty()) {
            // Copy to avoid mutating the original cache if needed for retry
            session.setFollowUpQueue(new LinkedList<>(cached));
        } else {
            session.setFollowUpQueue(new LinkedList<>());
        }
    }

    private void sendQuestionEvent(WebSocketSession ws, int part, Integer questionId, String text, boolean isFollowUp)
            throws IOException {
        String event = objectMapper.writeValueAsString(Map.of(
                "type", "question",
                "partNumber", part,
                "questionId", questionId,
                "text", text,
                "isFollowUp", isFollowUp));
        ws.sendMessage(new TextMessage(event));
    }
}
