package io.gsp26se16.moni.roadmap.entity;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "metric_ai_summary_cache")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MetricAiSummaryCache {

    @Id
    @Column(name = "user_id")
    private String userId;

    @Column(columnDefinition = "TEXT")
    private String summary;

    @Column(name = "generated_at")
    private LocalDateTime generatedAt;
}
