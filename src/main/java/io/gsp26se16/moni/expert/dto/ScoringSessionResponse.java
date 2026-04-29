package io.gsp26se16.moni.expert.dto;

import java.sql.Timestamp;
import java.time.LocalDateTime;

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
    String userDisplayName;
    String skill;
    SessionStatus status;
    String roomUrl;
    String roomName;
    Integer queuePosition;
    Timestamp createdAt;
    LocalDateTime startedAt;
    LocalDateTime endedAt;
    Integer testId;
    String testTitle;
    String stimulusTitle;
    Long writingSubmissionId;
    LocalDateTime submittedAt;
    String content; // Essay content for writing sessions
    String recordingUrl;
    String expertRecordingUrl;
    Integer userRating;
    String userComment;
    /** Overall band from ExpertEvaluation when session is COMPLETED; null otherwise. */
    Double overallBand;
}
