package io.gsp26se16.moni.placement.dto.response;

import lombok.*;
import lombok.experimental.FieldDefaults;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class AiRecommendResponse {
    Double recommendedReading;
    Double recommendedListening;
    Double recommendedWriting;
    Double recommendedSpeaking;
    Double recommendedOverall;
    String analysis;
    String studyPlan;
}
