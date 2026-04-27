package io.gsp26se16.moni.placement.dto.response;

import java.time.LocalDateTime;
import java.util.Map;

import lombok.*;
import lombok.experimental.FieldDefaults;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class PlacementResultResponse {
    Integer id;
    Double readingBand;
    Double listeningBand;
    Double writingBand;
    Double speakingBand;
    Double overallBand;
    Double targetBand;
    Integer readingCorrect;
    Integer listeningCorrect;
    Boolean isSelfAssessed;
    LocalDateTime completedAt;

    // AI evaluation criteria breakdown
    Map<String, Double> writingCriteria; // {TA: 6.5, CC: 7.0, LR: 6.0, GRA: 6.5}
    Map<String, Double> speakingCriteria; // {FC: 6.0, LR: 5.5, GRA: 6.0, PR: 5.0}
    Map<String, Object> writingFeedback; // AI feedback for writing
    Map<String, Object> speakingFeedback; // AI feedback for speaking
}
