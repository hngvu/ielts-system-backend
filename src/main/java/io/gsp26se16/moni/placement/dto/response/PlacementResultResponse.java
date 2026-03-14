package io.gsp26se16.moni.placement.dto.response;

import java.time.LocalDateTime;

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
}
