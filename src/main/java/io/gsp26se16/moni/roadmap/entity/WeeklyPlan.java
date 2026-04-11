package io.gsp26se16.moni.roadmap.entity;

import java.time.LocalDate;
import java.time.LocalDateTime;

import jakarta.persistence.*;

import io.gsp26se16.moni.authentication.entity.Users;
import lombok.*;

@Entity
@Table(name = "weekly_plans")
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class WeeklyPlan {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private Users user;

    @Column(name = "week_number", nullable = false)
    private Integer weekNumber;

    @Column(name = "month_cycle", nullable = false)
    @Builder.Default
    private Integer monthCycle = 1;

    @Column(name = "week_in_month", nullable = false)
    private Integer weekInMonth; // 1-4

    @Column(name = "week_start_date", nullable = false)
    private LocalDate weekStartDate;

    @Column(name = "week_end_date", nullable = false)
    private LocalDate weekEndDate;

    @Column(nullable = false)
    @Builder.Default
    private String status = "ACTIVE"; // ACTIVE / COMPLETED

    @Column(name = "difficulty_level")
    @Builder.Default
    private Double difficultyLevel = 0.5;

    @Column(name = "weekly_accuracy")
    private Double weeklyAccuracy;

    @Column(name = "completion_rate")
    private Double completionRate;

    @Column(name = "performance_verdict")
    private String performanceVerdict; // IMPROVED / STABLE / DECLINED

    @Column(name = "created_at")
    private LocalDateTime createdAt;
}
