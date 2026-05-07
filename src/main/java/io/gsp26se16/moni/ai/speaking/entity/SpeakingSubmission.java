package io.gsp26se16.moni.ai.speaking.entity;

import java.time.LocalDateTime;

import jakarta.persistence.*;

import org.hibernate.annotations.CreationTimestamp;

import io.gsp26se16.moni.authentication.entity.Users;
import io.gsp26se16.moni.common.enumeration.EvaluationStatus;
import io.gsp26se16.moni.content.entity.Test;
import io.gsp26se16.moni.practice.entity.TestSession;
import lombok.*;
import lombok.experimental.FieldDefaults;

/**
 * Lưu một lần nộp bài Speaking của học viên.
 *
 * Quan hệ:
 *   TestSession      1 ──── N  SpeakingSubmission
 *   SpeakingSession  1 ──── 1  SpeakingSubmission  (WebSocket session)
 *   SpeakingSubmission 1 ──── 1  AiEvaluation  (submission_type = SPEAKING)
 */
@Entity
@Table(name = "speaking_submissions")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class SpeakingSubmission {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    Long id;

    /** Session thi hoặc luyện tập mà bài nộp này thuộc về */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "session_id")
    TestSession testSession;

    /** Người dùng nộp bài */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    Users user;

    /**
     * WebSocket speaking session đã tạo audio này.
     * Nullable — cho phép luyện nói tự do không gắn với TestSession.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "speaking_session_id")
    SpeakingSession speakingSession;

    /** Đề thi Speaking mà bài nộp này thuộc về */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "test_id")
    Test test;

    /**
     * Part 1, 2, hoặc 3 của IELTS Speaking.
     * Null nếu là bài luyện tập tự do.
     */
    @Column(name = "part_number")
    Integer partNumber;

    /** URL file audio đã upload lên Cloudinary */
    @Column(name = "audio_url", columnDefinition = "TEXT")
    String audioUrl;

    /** Văn bản transcript sau khi qua STT */
    @Column(name = "audio_transcript", columnDefinition = "TEXT")
    String audioTranscript;

    /**
     * JSON array thời gian nói (ms) của từng câu trả lời, song song với {@link #audioUrl}.
     * Dùng để tính tổng thời gian user nói khi mở lại trang kết quả.
     */
    @Column(name = "audio_durations_ms", columnDefinition = "TEXT")
    String audioDurationsMs;

    /** Trạng thái đánh giá hiện tại */
    @Enumerated(EnumType.STRING)
    @Column(name = "evaluation_status", nullable = false, length = 20)
    @Builder.Default
    EvaluationStatus evaluationStatus = EvaluationStatus.PENDING;

    /** Thời điểm AI bắt đầu xử lý (dùng tính latency) */
    @Column(name = "processing_started_at")
    LocalDateTime processingStartedAt;

    @CreationTimestamp
    @Column(name = "submitted_at", updatable = false)
    LocalDateTime submittedAt;
}
