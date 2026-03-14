package io.gsp26se16.moni.roadmap.entity;

import io.gsp26se16.moni.authentication.entity.Users;
import io.gsp26se16.moni.tag.entity.Tag;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

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

    @Column(name = "mastery_level")
    private Double masteryLevel; // [cite: 60]

    @Column(name = "confidence_score")
    private Double confidenceScore; // [cite: 61]

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
