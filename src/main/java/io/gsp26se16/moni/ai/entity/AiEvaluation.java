package io.gsp26se16.moni.ai.entity;

import java.util.Map;

import jakarta.persistence.*;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import lombok.*;

@Entity
@Table(name = "ai_evaluations")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AiEvaluation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "submission_id")
    private SkillSubmission skillSubmission;

    @Column(name = "overall_score")
    private Double overallScore;

    @Column(name = "overall_comment", columnDefinition = "TEXT")
    private String overallComment;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "analysis_result", columnDefinition = "jsonb")
    private Map<String, Object> analysisResult;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "feedback_response", columnDefinition = "jsonb")
    private Map<String, Object> feedbackResponse;

    @Column(name = "created_at")
    private java.time.LocalDateTime createdAt;
}
