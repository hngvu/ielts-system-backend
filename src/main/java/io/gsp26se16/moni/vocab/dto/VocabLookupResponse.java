package io.gsp26se16.moni.vocab.dto;

import java.util.List;

import lombok.*;
import lombok.experimental.FieldDefaults;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class VocabLookupResponse {
    String word;
    String phonetic;
    String pos;
    String meaning;
    String explanation;
    String collocation;
    List<String> examples;
    String audioUrl;
}
