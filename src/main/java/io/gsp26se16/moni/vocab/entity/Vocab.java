package io.gsp26se16.moni.vocab.entity;

import java.time.LocalDateTime;

import jakarta.persistence.*;

import io.gsp26se16.moni.authentication.entity.Users;
import io.gsp26se16.moni.vocab.enumeration.VocabSourceType;
import io.gsp26se16.moni.vocab.enumeration.VocabStatus;
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
        uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "word"}),
        indexes = {
            @Index(name = "idx_vocab_user_status", columnList = "user_id, status"),
            @Index(name = "idx_vocab_user_review", columnList = "user_id, next_review_at, status"),
            @Index(name = "idx_vocab_user_list", columnList = "user_id, vocab_list_id"),
            @Index(name = "idx_vocab_user_source", columnList = "user_id, source_type"),
        })
public class Vocab {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    Integer id;

    String word;
    String phonetic;
    String pos;

    @Column(columnDefinition = "TEXT")
    String definition;

    @Column(columnDefinition = "TEXT")
    String example;

    String audioUrl;

    @Column(columnDefinition = "TEXT")
    String meaning; // Vietnamese translation

    @Enumerated(EnumType.STRING)
    @Builder.Default
    VocabStatus status = VocabStatus.ACTIVE;

    // Source tracking: where was this word saved from?
    @Enumerated(EnumType.STRING)
    @Builder.Default
    VocabSourceType sourceType = VocabSourceType.MANUAL;

    // SM2 Spaced Repetition fields
    @Builder.Default
    double easeFactor = 2.5;

    @Builder.Default
    int repetitions = 0;

    @Builder.Default
    int interval = 0;

    LocalDateTime nextReviewAt;
    LocalDateTime createdAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    Users user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "vocab_list_id")
    VocabList vocabList;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "dictionary_id")
    Dictionary dictionary;

    @PrePersist
    void onCreate() {
        if (createdAt == null) createdAt = LocalDateTime.now();
        if (nextReviewAt == null) nextReviewAt = LocalDateTime.now();
    }
}
