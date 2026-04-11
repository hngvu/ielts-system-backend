package io.gsp26se16.moni.roadmap.dto.response;

import lombok.*;
import lombok.experimental.FieldDefaults;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class WeeklyPlanSummaryResponse {
    Integer weekNumber;
    Integer monthCycle;
    Integer weekInMonth;
    String weekStartDate;
    String weekEndDate;
    Double weeklyAccuracy;
    Double completionRate;
    String performanceVerdict;
}
