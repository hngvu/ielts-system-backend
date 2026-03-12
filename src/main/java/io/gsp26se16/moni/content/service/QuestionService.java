package io.gsp26se16.moni.content.service;

import java.util.Map;

import io.gsp26se16.moni.content.dto.request.QuestionCreateRequest;
import io.gsp26se16.moni.content.dto.request.QuestionUpdateRequest;

public interface QuestionService {
    void updateQuestion(Integer id, QuestionUpdateRequest request);

    void batchUpdateQuestions(Map<Integer, QuestionUpdateRequest> updates);

    Integer createForGroup(Integer groupId, QuestionCreateRequest request);

    void deleteQuestion(Integer id);
}
