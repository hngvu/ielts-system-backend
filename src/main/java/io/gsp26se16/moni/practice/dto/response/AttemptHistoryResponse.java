package io.gsp26se16.moni.practice.dto.response;

import java.time.LocalDateTime;

import lombok.*;
import lombok.experimental.FieldDefaults;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class AttemptHistoryResponse {
    Integer attemptId;
    Integer stimulusId;
    String stimulusTitle;
    String skill;
    int score;
    int totalQuestions;
    int elapsedSeconds;
    LocalDateTime submittedAt;
}
