package io.gsp26se16.moni.vocab.entity;

import jakarta.persistence.*;

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
            @Index(name = "idx_curated_word", columnList = "word"),
            @Index(name = "idx_curated_band", columnList = "band"),
            @Index(name = "idx_curated_topic", columnList = "topic"),
        })
public class CuratedWord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    Integer id;

    @Column(nullable = false)
    String word;

    String phonetic;
    String pos;

    @Column(columnDefinition = "TEXT")
    String definition;

    @Column(columnDefinition = "TEXT")
    String example;

    String meaning; // Vietnamese translation (populated via AI later)

    String band; // IELTS band range: "4-5", "5.5-6.5", "7-8", "8.5-9"
    String cefrLevel; // B1, B2, C1, C2

    String topic; // nullable: "environment", "technology", etc.
    String audioUrl;
}
