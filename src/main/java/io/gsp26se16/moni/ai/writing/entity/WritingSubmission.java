package io.gsp26se16.moni.ai.writing.entity;

import java.time.LocalDateTime;

import jakarta.persistence.*;

import org.hibernate.annotations.CreationTimestamp;

import io.gsp26se16.moni.authentication.entity.Users;
import io.gsp26se16.moni.common.enumeration.EvaluationStatus;
import io.gsp26se16.moni.common.enumeration.WritingTaskType;
import io.gsp26se16.moni.content.entity.QuestionGroup;
import io.gsp26se16.moni.practice.entity.TestSession;
import lombok.*;
import lombok.experimental.FieldDefaults;

@Entity
@Table(name = "writing_submissions")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class WritingSubmission {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "session_id", nullable = false)
    TestSession testSession;

    /** Người dùng nộp bài */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    Users user;

    /** Nhóm câu hỏi (chứa đề bài + stimulus nếu có) */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "question_group_id")
    QuestionGroup questionGroup;

    /** Task 1 (biểu đồ/thư) hay Task 2 (essay) */
    @Enumerated(EnumType.STRING)
    @Column(name = "task_type", nullable = false, length = 10)
    WritingTaskType taskType;

    /** Toàn bộ nội dung bài viết */
    @Column(name = "essay_content", columnDefinition = "TEXT", nullable = false)
    String essayContent;

    /** Số từ hệ thống đếm được */
    @Column(name = "word_count")
    Integer wordCount;

    /** Trạng thái đánh giá hiện tại */
    @Enumerated(EnumType.STRING)
    @Column(name = "evaluation_status", nullable = false, length = 20)
    @Builder.Default
    EvaluationStatus evaluationStatus = EvaluationStatus.PENDING;

    @CreationTimestamp
    @Column(name = "submitted_at", updatable = false)
    LocalDateTime submittedAt;
}
