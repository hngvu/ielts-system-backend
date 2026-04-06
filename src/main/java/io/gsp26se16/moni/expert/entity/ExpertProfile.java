package io.gsp26se16.moni.expert.entity;

import java.sql.Timestamp;

import jakarta.persistence.*;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import io.gsp26se16.moni.authentication.entity.Users;
import io.gsp26se16.moni.expert.enumeration.ExpertSpecialization;
import io.gsp26se16.moni.expert.enumeration.ExpertStatus;
import lombok.*;
import lombok.experimental.FieldDefaults;

@Entity
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class ExpertProfile {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    Integer id;

    @OneToOne
    @JoinColumn(name = "user_id")
    Users user;

    String displayName;
    String avatarUrl;
    Double bandScore; // overall
    Double bandReading;
    Double bandListening;
    Double bandWriting;
    Double bandSpeaking;
    Integer yearsExperience;

    @Enumerated(EnumType.STRING)
    ExpertSpecialization specialization;

    @Column(columnDefinition = "TEXT")
    String bio;

    @Column(columnDefinition = "TEXT")
    String certificates; // JSON array of certificate image URLs, e.g. ["url1","url2"]

    @Enumerated(EnumType.STRING)
    @Builder.Default
    ExpertStatus status = ExpertStatus.AVAILABLE;

    @Builder.Default
    Double rating = 0.0;

    @Builder.Default
    Integer totalSessions = 0;

    java.time.LocalDateTime lastActiveAt;

    @CreationTimestamp
    Timestamp createdAt;

    @UpdateTimestamp
    Timestamp updatedAt;
}
