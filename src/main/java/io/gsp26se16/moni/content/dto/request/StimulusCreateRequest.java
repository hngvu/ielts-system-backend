package io.gsp26se16.moni.content.dto.request;

import io.gsp26se16.moni.common.enumeration.Skill;
import io.gsp26se16.moni.common.enumeration.TestType;
import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
public class StimulusCreateRequest {
    private String title;
    private TestType testType;
    private Skill skill;
    private String content;
    private String mediaUrl;
    private String metadata;

    private List<QuestionGroupRequest> questionGroups;

    @Data
    public static class QuestionGroupRequest {
        private String instruction;
        // Giả sử lấy QuestionType bằng code hoặc ID tùy bạn thiết kế
        private List<QuestionRequest> questions;
    }

    @Data
    public static class QuestionRequest {
        private String content;
        private Integer position;
        private Map<String, Object> explanation;
        private List<Integer> tagIds;
        private List<OptionRequest> options;
    }

    @Data
    public static class OptionRequest {
        private String label;
        private String content;
        private Boolean isCorrect;
    }
}
