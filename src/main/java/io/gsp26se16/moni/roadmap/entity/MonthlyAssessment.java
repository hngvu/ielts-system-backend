package io.gsp26se16.moni.roadmap.entity;

import java.time.LocalDateTime;

import jakarta.persistence.*;

import io.gsp26se16.moni.authentication.entity.Users;
import io.gsp26se16.moni.content.entity.Test;
import lombok.*;

@Entity
@Table(name = "monthly_assessments")
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class MonthlyAssessment {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private Users user;

    @Column(name = "month_cycle", nullable = false)
    private Integer monthCycle;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "full_test_id")
    private Test fullTest; // Test entity with testMode = FULL_TEST

    @Column(nullable = false)
    @Builder.Default
    private String status = "PENDING"; // PENDING / COMPLETED

    @Column(name = "reading_band")
    private Double readingBand;

    @Column(name = "listening_band")
    private Double listeningBand;

    @Column(name = "writing_band")
    private Double writingBand;

    @Column(name = "speaking_band")
    private Double speakingBand;

    @Column(name = "overall_band")
    private Double overallBand;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;
}
