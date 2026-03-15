package io.gsp26se16.moni.vocab.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class WordMatchPair {
    String word;
    String definition;
    String meaning;
}
