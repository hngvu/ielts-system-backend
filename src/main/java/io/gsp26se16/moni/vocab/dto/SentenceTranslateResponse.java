package io.gsp26se16.moni.vocab.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SentenceTranslateResponse {
    private String translation;
    private String explanation;
    private String keyPoints;
}
