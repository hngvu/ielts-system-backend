package io.gsp26se16.moni.vocab.dto;

import java.time.LocalDateTime;

import io.gsp26se16.moni.vocab.enumeration.VocabStatus;
import lombok.*;
import lombok.experimental.FieldDefaults;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class VocabResponse {
    Integer id;
    String word;
    String phonetic;
    String pos;
    String definition;
    String example;
    String meaning;
    String audioUrl;
    VocabStatus status;
    String collectionName;
    LocalDateTime nextReviewAt;
    LocalDateTime createdAt;
}
