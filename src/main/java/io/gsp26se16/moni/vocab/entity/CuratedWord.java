package io.gsp26se16.moni.vocab.entity;

import java.util.HashSet;
import java.util.Set;

import jakarta.persistence.*;

import io.gsp26se16.moni.tag.entity.Tag;
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

    @Column(columnDefinition = "TEXT")
    String meaning; // Vietnamese translation (populated via AI later)

    @ManyToMany
    @JoinTable(
            name = "curated_word_tag",
            joinColumns = @JoinColumn(name = "curated_word_id"),
            inverseJoinColumns = @JoinColumn(name = "tag_id"))
    Set<Tag> tags = new HashSet<>();

    @Column(columnDefinition = "TEXT")
    String audioUrl;
}
