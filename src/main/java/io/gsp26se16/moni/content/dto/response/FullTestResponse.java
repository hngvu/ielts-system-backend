package io.gsp26se16.moni.content.dto.response;

import java.util.List;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class FullTestResponse {
    Integer id;
    String title;
    String skill;
    String testType;
    Integer duration;
    String status; // DRAFT, PUBLISHED
    List<StimulusInfo> stimuli;

    @Data
    @Builder
    public static class StimulusInfo {
        Integer id;
        Integer section;
        String title; // stimulus title or first test title using it
        Integer questionCount;
        Integer testId; // source PRACTICE test containing this stimulus
        String testTitle;
    }
}
