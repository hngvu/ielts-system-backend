package io.gsp26se16.moni.roadmap.dto.response;

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
public class TaskResponse {
    Integer id;
    Integer order;
    String taskType;
    String status;
    Integer testId;
    Integer stimulusId;
    String stimulusTitle;
    Integer questionCount;
}
