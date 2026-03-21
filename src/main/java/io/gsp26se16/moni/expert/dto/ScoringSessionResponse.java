package io.gsp26se16.moni.expert.dto;

import java.sql.Timestamp;

import io.gsp26se16.moni.expert.enumeration.SessionStatus;
import lombok.*;
import lombok.experimental.FieldDefaults;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class ScoringSessionResponse {
    Integer id;
    Integer expertId;
    String expertDisplayName;
    String skill;
    SessionStatus status;
    String roomUrl;
    String roomName;
    Integer queuePosition;
    Timestamp createdAt;
}
