package io.gsp26se16.moni.ai.writing.entity;

import java.time.LocalDateTime;
import java.util.Map;

import io.gsp26se16.moni.common.enumeration.Skill;
import jakarta.persistence.*;

import lombok.experimental.FieldDefaults;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import lombok.*;

@Entity
@Table(
        name = "ai_evaluations",
        indexes = {
                @Index(name = "idx_ai_eval_submission", columnList = "submission_id, submission_type")
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class AiEvaluation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    Long id;

    /**
     * ID của WritingSubmission hoặc SpeakingSubmission.
     * Kết hợp với submissionType để xác định bảng cụ thể.
     */
    @Column(name = "submission_id", nullable = false)
    Long submissionId;

    /**
     * WRITING  → submissionId trỏ tới writing_submissions
     * SPEAKING → submissionId trỏ tới speaking_submissions
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "skill", nullable = false, length = 10)
    Skill skill;

    /** Điểm band tổng (đã làm tròn theo chuẩn IELTS) */
    @Column(name = "overall_score")
    Double overallScore;

    /**
     * Nhận xét tổng quát ngắn gọn.
     * Writing : summary feedback
     * Speaking: full transcript
     */
    @Column(name = "overall_comment", columnDefinition = "TEXT")
    String overallComment;

    /**
     * Kết quả phân tích chi tiết từng tiêu chí.
     * Writing : { TR, CC, LR, GRA, final_band, applied_hard_rules, ... }
     * Speaking: { FC, LR, GRA, PR, final_band, applied_hard_rules, ... }
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "analysis_result", columnDefinition = "jsonb")
    Map<String, Object> analysisResult;

    /**
     * Feedback chi tiết dành cho học viên.
     * Writing : { strengths, areas_for_improvement, band_breakdown, ... }
     * Speaking: { summary, strengths, areas_for_improvement, next_steps }
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "feedback_response", columnDefinition = "jsonb")
    Map<String, Object> feedbackResponse;

    @Column(name = "created_at", updatable = false)
    LocalDateTime createdAt;
}
