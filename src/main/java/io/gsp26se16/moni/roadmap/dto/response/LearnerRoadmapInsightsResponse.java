package io.gsp26se16.moni.roadmap.dto.response;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldDefaults;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class LearnerRoadmapInsightsResponse {
    LocalDate examDate;
    Integer daysToExam;
    Integer recommendedDailyStudyMinutes;

    // From user's profile targets (may be null)
    Double targetOverall;
    Double targetReading;
    Double targetListening;
    Double targetWriting;
    Double targetSpeaking;

    // Latest placement result
    Boolean placementSelfAssessed;
    LocalDateTime placementCompletedAt;
    Double placementOverall;
    Double placementReading;
    Double placementListening;
    Double placementWriting;
    Double placementSpeaking;

    // Calibrated (auto-adjusted) level
    Double calibratedOverall;
    Double calibratedReading;
    Double calibratedListening;
    Double calibratedWriting;
    Double calibratedSpeaking;
    String calibrationNote;

    // High level indices derived from mastery/confidence metrics
    Double masteryIndex; // 0..1
    Double confidenceIndex; // 0..1
    LocalDateTime lastMetricUpdatedAt;

    // Study hints
    Double achievableOverallByExam;
    Boolean targetOverAmbitious;
    String targetWarning;

    List<TagMetricResponse> weakestTags;
    List<TagMetricResponse> strongestTags;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @FieldDefaults(level = AccessLevel.PRIVATE)
    public static class TagMetricResponse {
        Integer tagId;
        String tagName;
        String tagCode;
        String tagType;
        String skill;
        Double masteryLevel;
        Double confidenceScore;
        LocalDateTime updatedAt;
        private Integer attemptCount;
        private Double pTransit;
        private Double pGuess;
        private Double pSlip;
    }
}
