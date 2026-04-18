package io.gsp26se16.moni.roadmap.entity;

import java.time.LocalDateTime;

import jakarta.persistence.*;

import io.gsp26se16.moni.authentication.entity.Users;
import io.gsp26se16.moni.common.enumeration.Skill;
import io.gsp26se16.moni.tag.entity.Tag;
import lombok.*;

@Entity
@Table(name = "learner_metrics")
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class LearnerMetric {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private Users user; // [cite: 58]

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tag_id")
    private Tag tag; // [cite: 59]

    @Enumerated(EnumType.STRING)
    @Column(name = "skill")
    private Skill skill;

    @Column(name = "mastery_level")
    private Double masteryLevel; // [cite: 60]

    @Column(name = "confidence_score")
    private Double confidenceScore; // [cite: 61]

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "p_transit")
    private Double pTransit;

    @Column(name = "p_guess")
    private Double pGuess;

    @Column(name = "p_slip")
    private Double pSlip;

    @Column(name = "attempt_count", columnDefinition = "integer default 0")
    private Integer attemptCount;
}
