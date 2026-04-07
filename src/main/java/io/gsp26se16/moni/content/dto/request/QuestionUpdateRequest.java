package io.gsp26se16.moni.content.dto.request;

import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonAlias;

import lombok.Data;

@Data
public class QuestionUpdateRequest {
    private String content;
    private Map<String, Object> explanation;

    @JsonAlias("orderIndex")
    private Integer position;

    private List<Integer> tagIds;
    private List<OptionUpdateRequest> options;

    @Data
    public static class OptionUpdateRequest {
        private String label;
        private String content;
        private Boolean isCorrect;
    }
}
