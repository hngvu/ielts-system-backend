package io.gsp26se16.moni.roadmap.dto.response;

import java.util.List;

import lombok.*;
import lombok.experimental.FieldDefaults;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class WeeklyPlanDetailResponse {
    Integer id;
    Integer weekNumber;
    Integer monthCycle;
    Integer weekInMonth;
    String weekStartDate;
    String weekEndDate;
    String status;
    Double difficultyLevel;
    Double weeklyAccuracy;
    Double completionRate;
    String performanceVerdict;
    String previousVerdict;
    List<DailySlotResponse> slots;
    Boolean todayCompleted;
    Boolean suggestVocabulary;
    Boolean monthlyAssessmentPending;
    String simulatedToday;
}
