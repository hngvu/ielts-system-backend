package io.gsp26se16.moni.roadmap.entity;

import java.time.LocalDate;
import java.time.LocalDateTime;

import jakarta.persistence.*;

import io.gsp26se16.moni.common.enumeration.Skill;
import io.gsp26se16.moni.content.entity.Stimulus;
import io.gsp26se16.moni.content.entity.Test;
import lombok.*;

@Entity
@Table(name = "daily_slots")
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class DailySlot {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "weekly_plan_id", nullable = false)
    private WeeklyPlan weeklyPlan;

    @Column(name = "day_of_week", nullable = false)
    private Integer dayOfWeek; // 1=T2, 2=T3, ..., 7=CN

    @Column(name = "slot_date", nullable = false)
    private LocalDate slotDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Skill skill;

    @Column(name = "task_type", nullable = false)
    @Builder.Default
    private String taskType = "PRACTICE"; // PRACTICE / ASSESSMENT

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "stimulus_id")
    private Stimulus stimulus;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "test_id")
    private Test test;

    @Column(nullable = false)
    @Builder.Default
    private String status = "TODO"; // TODO / DONE

    private Integer score;

    @Column(name = "total_questions")
    private Integer totalQuestions;

    @Column(name = "reference_metadata", length = 500)
    private String referenceMetadata; // used for storing topic config for vocabulary tasks

    @Column(name = "completed_at")
    private LocalDateTime completedAt;
}
