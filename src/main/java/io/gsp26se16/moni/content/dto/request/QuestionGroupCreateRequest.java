package io.gsp26se16.moni.content.dto.request;

import java.util.List;
import java.util.Map;

import jakarta.validation.constraints.NotBlank;

import lombok.Data;

@Data
public class QuestionGroupCreateRequest {
    private String instruction;

    @NotBlank(message = "Question Type Code is required")
    private String questionTypeCode;

    private String groupContent;
    private String imageUrl;
    private List<Map<String, Object>> sharedOptions;
    private List<QuestionCreateRequest> questions;
}
