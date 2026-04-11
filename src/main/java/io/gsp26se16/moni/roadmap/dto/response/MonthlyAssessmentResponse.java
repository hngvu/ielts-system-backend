package io.gsp26se16.moni.roadmap.dto.response;

import lombok.*;
import lombok.experimental.FieldDefaults;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class MonthlyAssessmentResponse {
    Integer id;
    Integer monthCycle;
    Integer fullTestId;
    String status; // PENDING / COMPLETED
    Double readingBand;
    Double listeningBand;
    Double writingBand;
    Double speakingBand;
    Double overallBand;
}
