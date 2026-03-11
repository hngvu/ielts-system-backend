package io.gsp26se16.moni.vocab.entity;

import jakarta.persistence.Column;
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
public class Dictionary {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    Integer id;

    @Column(unique = true)
    String word;

    String phonetic;
    String pos; // part of speech
    String definition;
    String example;
    String audioUrl;

    String meaning; // Vietnamese translation
    String collocation;

    @Column(columnDefinition = "TEXT")
    String explanation; // Vietnamese explanation in context

    @Column(columnDefinition = "TEXT")
    String examples; // JSON array string of example sentences
}
