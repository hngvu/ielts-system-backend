package io.gsp26se16.moni.vocab.entity;

import java.time.LocalDateTime;

import jakarta.persistence.*;

import io.gsp26se16.moni.authentication.entity.Users;
import io.gsp26se16.moni.vocab.enumeration.VocabReviewStatus;
import lombok.*;
import lombok.experimental.FieldDefaults;

@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
@Table(
        indexes = {
            @Index(name = "idx_vocabreview_user", columnList = "user_id, reviewed_at"),
        })
public class VocabReview {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    Integer id;

    LocalDateTime reviewedAt;

    @Enumerated(EnumType.STRING)
    @Builder.Default
    VocabReviewStatus status = VocabReviewStatus.COMPLETED;

    int quality; // SM-2 quality rating 0-5

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "vocab_id", nullable = false)
    Vocab vocab;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    Users user;

    @PrePersist
    void onCreate() {
        if (reviewedAt == null) reviewedAt = LocalDateTime.now();
    }
}
