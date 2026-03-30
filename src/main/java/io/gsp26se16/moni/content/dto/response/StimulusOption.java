package io.gsp26se16.moni.content.dto.response;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class StimulusOption {
    Integer stimulusId;
    Integer testId;
    String title; // from the practice test that uses this stimulus
    Integer questionCount;
}
