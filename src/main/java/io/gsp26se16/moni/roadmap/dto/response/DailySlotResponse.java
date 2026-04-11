package io.gsp26se16.moni.roadmap.dto.response;

import lombok.*;
import lombok.experimental.FieldDefaults;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class DailySlotResponse {
    Integer id;
    Integer dayOfWeek;
    String slotDate;
    String skill;
    String taskType; // PRACTICE / ASSESSMENT
    Integer stimulusId;
    String stimulusTitle;
    Integer testId;
    String status; // TODO / DONE
    Integer score;
    Integer totalQuestions;
}
