package io.gsp26se16.moni.content.dto.response;

import io.gsp26se16.moni.common.enumeration.PublishStatus;
import io.gsp26se16.moni.common.enumeration.Skill;
import io.gsp26se16.moni.common.enumeration.TestMode;
import io.gsp26se16.moni.common.enumeration.TestType;
import lombok.Builder;
import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
@Builder
public class TestDetailResponse {
    private Integer id;
    private String title;
    private String description;
    private Skill skill;
    private Integer duration;
    private TestMode testMode;
    private PublishStatus status;
    private List<Integer> tagIds;

    private List<StimulusDetail> stimuli;

    @Data @Builder public static class StimulusDetail {
        private Integer id;
        private String title;
        private String content;
        private String mediaUrl;
        private Integer section;
        private List<QuestionGroupDetail> questionGroups;
    }

    @Data @Builder public static class QuestionGroupDetail {
        private Integer id;
        private String instruction;
        private List<QuestionDetail> questions;
    }

    @Data @Builder public static class QuestionDetail {
        private Integer id;
        private String content;
        private Integer position;
        private Map<String, Object> explanation;
        private List<Integer> tagIds;
        private List<OptionDetail> options;
    }

    @Data @Builder public static class OptionDetail {
        private Integer id;
        private String label;
        private String content;
        private Boolean isCorrect;
    }
}
