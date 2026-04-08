package io.gsp26se16.moni.vocab.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;

import lombok.*;
import lombok.experimental.FieldDefaults;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class VocabDetailResponse {
    Integer id;
    String word;
    String phonetic;
    String pos;
    String definition;
    String example;
    String audioUrl;
    String meaning;
    String status;
    String collectionName;

    // Rich fields from Dictionary
    String collocation;
    String explanation;
    List<String> examples;
}
