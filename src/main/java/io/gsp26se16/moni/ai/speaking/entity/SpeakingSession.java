package io.gsp26se16.moni.ai.speaking.entity;

import java.time.LocalDateTime;

import jakarta.persistence.*;

import io.gsp26se16.moni.authentication.entity.Users;
import lombok.*;
import lombok.experimental.FieldDefaults;

@Entity
@Table(name = "speaking_sessions")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class SpeakingSession {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    String id;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "user_id", nullable = false)
    Users user;

    @Column(name = "question", columnDefinition = "TEXT")
    String question;

    @Column(name = "status", length = 20)
    @Builder.Default
    String status = "ACTIVE";

    @Column(name = "start_time")
    LocalDateTime startTime;

    @Column(name = "end_time")
    LocalDateTime endTime;
}
