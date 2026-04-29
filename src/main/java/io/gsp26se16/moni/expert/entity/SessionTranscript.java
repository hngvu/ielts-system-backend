package io.gsp26se16.moni.expert.entity;

import java.time.LocalDateTime;

import jakarta.persistence.*;

import org.hibernate.annotations.CreationTimestamp;

import io.gsp26se16.moni.authentication.entity.Users;
import lombok.*;
import lombok.experimental.FieldDefaults;

/**
 * Persisted live caption from a scoring session video call. One row per finalized utterance.
 * Used for: learner self-review, expert quote/feedback search, admin audit.
 * NOTE: Auto-generated text (Web Speech API) — accuracy ~85%, NOT authoritative for grading.
 */
@Entity
@Table(
        name = "session_transcript",
        indexes = {@Index(name = "idx_transcript_session_ts", columnList = "scoring_session_id, spokenAt")})
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class SessionTranscript {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "scoring_session_id", nullable = false)
    ScoringSession scoringSession;

    /** User who spoke this caption (learner or expert). Null only if speaker untracked. */
    @ManyToOne
    @JoinColumn(name = "speaker_user_id")
    Users speaker;

    /** "EXPERT" or "LEARNER" — denormalized for fast filtering / admin views. */
    @Column(length = 16)
    String speakerRole;

    /** Display name at time of speaking — denormalized for stable historical view. */
    @Column(length = 200)
    String speakerName;

    @Column(columnDefinition = "TEXT", nullable = false)
    String text;

    /** Recognition language code, e.g. "en-US". */
    @Column(length = 16)
    String language;

    /** Client-side timestamp of utterance (ms since epoch source preserved as datetime). */
    LocalDateTime spokenAt;

    @CreationTimestamp
    LocalDateTime createdAt;
}
