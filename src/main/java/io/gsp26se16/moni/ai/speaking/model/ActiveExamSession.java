package io.gsp26se16.moni.ai.speaking.model;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CopyOnWriteArrayList;

import org.springframework.web.socket.WebSocketSession;

import io.gsp26se16.moni.common.enumeration.ExamState;
import io.gsp26se16.moni.content.entity.Question;
import lombok.Getter;
import lombok.Setter;

/**
 * Trạng thái in-memory của một buổi thi Speaking.
 * Tồn tại suốt từ start_exam → completed, sau đó bị xóa khỏi ExamSessionManager.
 *
 * <p>Thread-safety: queues và transcript lists dùng concurrent collections
 * vì WS messages có thể đến trong khi server đang xử lý câu trước.
 */
@Getter
@Setter
public class ActiveExamSession {

    private final String sessionId;
    private final String userId;
    private final Integer testId;
    private final WebSocketSession wsSession;

    /** ID của SpeakingSession entity đã persist trong DB */
    private String speakingSessionId;

    // ── State machine ─────────────────────────────────────────────────────────
    private volatile ExamState state = ExamState.IDLE;

    // ── Question queues (thread-safe) ─────────────────────────────────────────
    private Queue<Question> part1Queue = new ConcurrentLinkedQueue<>();
    private Queue<Question> followUpQueue = new ConcurrentLinkedQueue<>();
    private Question part2Question;
    private Queue<Question> part3Queue = new ConcurrentLinkedQueue<>();
    private Question currentQuestion;

    // ── Pre-cached follow-up questions (loaded during @Transactional) ─────────
    /** Key = mainQuestion.id, Value = ordered queue of follow-up questions */
    private final Map<Integer, Queue<Question>> preloadedFollowUps = new HashMap<>();

    // ── Transition scripts ────────────────────────────────────────────────────
    /** "Now I'm going to give you a topic..." */
    private Question part2TransitionScript;
    /** "We've been talking about... let's discuss..." */
    private Question part3TransitionScript;

    // ── Transcripts tích lũy theo part (thread-safe) ─────────────────────────
    private final List<TranscriptEntry> part1Transcripts = new CopyOnWriteArrayList<>();
    private volatile String part2Transcript = "";
    private volatile String part2AudioUrl = "";
    private final List<TranscriptEntry> part3Transcripts = new CopyOnWriteArrayList<>();

    public ActiveExamSession(String sessionId, String userId, Integer testId, WebSocketSession wsSession) {
        this.sessionId = sessionId;
        this.userId = userId;
        this.testId = testId;
        this.wsSession = wsSession;
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    public void addPart1Transcript(Integer questionId, String text, String audioUrl) {
        part1Transcripts.add(new TranscriptEntry(questionId, text, audioUrl));
    }

    public void addPart3Transcript(Integer questionId, String text, String audioUrl) {
        part3Transcripts.add(new TranscriptEntry(questionId, text, audioUrl));
    }

    /** Gộp toàn bộ transcript 3 parts để chuyển cho ConversationEngine chấm */
    public String getFullTranscript() {
        StringBuilder sb = new StringBuilder();

        sb.append("=== PART 1 ===\n");
        for (TranscriptEntry e : part1Transcripts) {
            sb.append("Q").append(e.questionId()).append(": ").append(e.text()).append("\n");
        }

        sb.append("\n=== PART 2 ===\n").append(part2Transcript).append("\n");

        sb.append("\n=== PART 3 ===\n");
        for (TranscriptEntry e : part3Transcripts) {
            sb.append("Q").append(e.questionId()).append(": ").append(e.text()).append("\n");
        }

        return sb.toString();
    }

    public List<String> getAudioUrls() {
        List<String> urls = new ArrayList<>();

        for (TranscriptEntry e : part1Transcripts) {
            if (e.audioUrl() != null && !e.audioUrl().isBlank()) urls.add(e.audioUrl());
        }

        if (part2AudioUrl != null && !part2AudioUrl.isBlank()) urls.add(part2AudioUrl);

        for (TranscriptEntry e : part3Transcripts) {
            if (e.audioUrl() != null && !e.audioUrl().isBlank()) urls.add(e.audioUrl());
        }

        return urls;
    }

    public boolean isOpen() {
        return wsSession != null && wsSession.isOpen();
    }

    public record TranscriptEntry(Integer questionId, String text, String audioUrl) {}
}
