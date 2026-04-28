package io.gsp26se16.moni.notification.entity;

import java.sql.Timestamp;

import jakarta.persistence.*;

import org.hibernate.annotations.CreationTimestamp;

import io.gsp26se16.moni.notification.enumeration.NotificationType;
import lombok.*;
import lombok.experimental.FieldDefaults;

@Entity
@Table(
        name = "notifications",
        indexes = {
            @Index(name = "idx_notifications_user_created", columnList = "user_id, createdAt"),
            @Index(name = "idx_notifications_user_unread", columnList = "user_id, isRead")
        })
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class Notification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    Long id;

    // Users.id (UUID string) — recipient of the notification
    @Column(name = "user_id", nullable = false)
    String userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 64)
    NotificationType type;

    @Column(nullable = false, length = 255)
    String title;

    @Column(columnDefinition = "TEXT")
    String message;

    // Optional deep-link path (e.g. /scoring-history) for FE to navigate when clicked
    @Column(length = 512)
    String link;

    @Builder.Default
    @Column(nullable = false)
    Boolean isRead = false;

    // Foreign key to ScoringSession (when relevant) — kept as plain id to avoid eager join
    Integer scoringSessionId;

    @CreationTimestamp
    Timestamp createdAt;
}
