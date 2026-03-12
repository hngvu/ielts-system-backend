package io.gsp26se16.moni.content.dto.request;

import java.util.List;
import java.util.Map;

import lombok.Data;

@Data
public class QuestionCreateRequest {
    private String content;
    private Integer position;
    private Map<String, Object> explanation;
    private List<Integer> tagIds;
    private List<OptionCreateRequest> options;

    @Data
    public static class OptionCreateRequest {
        private String label;
        private String content;
        private Boolean isCorrect;
    }
}
