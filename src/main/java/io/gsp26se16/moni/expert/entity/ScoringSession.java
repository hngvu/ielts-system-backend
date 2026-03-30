package io.gsp26se16.moni.expert.entity;

import java.sql.Timestamp;
import java.time.LocalDateTime;

import jakarta.persistence.*;

import org.hibernate.annotations.CreationTimestamp;

import io.gsp26se16.moni.authentication.entity.Users;
import io.gsp26se16.moni.expert.enumeration.SessionStatus;
import lombok.*;
import lombok.experimental.FieldDefaults;

@Entity
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class ScoringSession {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    Integer id;

    @ManyToOne
    @JoinColumn(name = "user_id")
    Users user;

    @ManyToOne
    @JoinColumn(name = "expert_id")
    ExpertProfile expert;

    String skill;

    @Enumerated(EnumType.STRING)
    @Builder.Default
    SessionStatus status = SessionStatus.QUEUED;

    String roomUrl;
    String roomName;

    @Column(columnDefinition = "TEXT")
    String content;

    Integer queuePosition;

    @CreationTimestamp
    Timestamp createdAt;

    LocalDateTime startedAt;
    LocalDateTime endedAt;

    Integer userRating;
    String userComment;

    Integer testId; // The speaking test ID so expert can see the questions

    Long writingSubmissionId; // Link to WritingSubmission for writing skill

    String recordingUrl; // User's mic recording URL
    String expertRecordingUrl; // Expert's mic recording URL
}
