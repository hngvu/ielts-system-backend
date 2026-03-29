package io.gsp26se16.moni.expert.dto;

import lombok.*;
import lombok.experimental.FieldDefaults;

@Data
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class CreateSessionRequest {
    Integer expertId;
    String skill;
    String content;
    Integer testId;
    Long writingSubmissionId;
}
