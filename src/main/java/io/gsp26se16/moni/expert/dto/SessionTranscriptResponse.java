package io.gsp26se16.moni.expert.dto;

import java.time.LocalDateTime;

import lombok.*;
import lombok.experimental.FieldDefaults;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class SessionTranscriptResponse {
    Long id;
    Integer scoringSessionId;
    String speakerName;
    String speakerRole;
    String text;
    String language;
    LocalDateTime spokenAt;
}
