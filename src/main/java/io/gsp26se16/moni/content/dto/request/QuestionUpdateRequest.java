package io.gsp26se16.moni.content.dto.request;

import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
public class QuestionUpdateRequest {
    private String content;
    private Map<String, Object> explanation;
    private Integer position;

    private List<Integer> tagIds;
}
