package io.gsp26se16.moni.content.dto.request;

import java.util.List;
import java.util.Map;

import lombok.Data;

@Data
public class QuestionUpdateRequest {
    private String content;
    private Map<String, Object> explanation;
    private Integer position;

    private List<Integer> tagIds;
}
