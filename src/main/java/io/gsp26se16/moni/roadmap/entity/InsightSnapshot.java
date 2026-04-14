package io.gsp26se16.moni.roadmap.entity;

import java.time.LocalDateTime;

import jakarta.persistence.*;

import io.gsp26se16.moni.authentication.entity.Users;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "insight_snapshot")
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class InsightSnapshot {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private Users user;

    @Column(name = "week_number")
    private Integer weekNumber;

    @Column(name = "reading_calibrated")
    private Double readingCalibrated;

    @Column(name = "listening_calibrated")
    private Double listeningCalibrated;

    @Column(name = "writing_calibrated")
    private Double writingCalibrated;

    @Column(name = "speaking_calibrated")
    private Double speakingCalibrated;

    @Column(name = "overall_calibrated")
    private Double overallCalibrated;

    @Column(name = "mastery_index")
    private Double masteryIndex;

    @Column(name = "confidence_index")
    private Double confidenceIndex;

    @Column(name = "created_at")
    private LocalDateTime createdAt;
}
