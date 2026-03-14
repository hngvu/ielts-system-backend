package io.gsp26se16.moni.placement.dto.request;

import java.time.LocalDate;

import lombok.*;
import lombok.experimental.FieldDefaults;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class AiRecommendRequest {
    Double currentReading;
    Double currentListening;
    Double currentWriting;
    Double currentSpeaking;
    Double currentOverall;
    Double targetReading;
    Double targetListening;
    Double targetWriting;
    Double targetSpeaking;
    Double targetOverall;
    LocalDate examDate;
}
