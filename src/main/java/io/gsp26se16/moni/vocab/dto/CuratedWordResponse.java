package io.gsp26se16.moni.vocab.dto;

import lombok.AccessLevel;
import lombok.Builder;
import lombok.Data;
import lombok.experimental.FieldDefaults;

@Data
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class CuratedWordResponse {
    Integer id;
    String word;
    String phonetic;
    String pos;
    String definition;
    String example;
    String meaning;
    String band;
    String cefrLevel;
    String topic;
    boolean saved;
}
