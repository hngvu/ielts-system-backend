package io.gsp26se16.moni.vocab.entity;

import java.time.LocalDateTime;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;

import lombok.*;
import lombok.experimental.FieldDefaults;

@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class Vocab {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    Integer id;

    String word;
    String phonetic;
    String pos; // part of speech
    String definition;
    String example;
    String audioUrl;

    int easeFactor;
    LocalDateTime nextReviewAt;
}
