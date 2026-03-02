package io.gsp26se16.moni.content.service;

import io.gsp26se16.moni.content.dto.request.QuestionUpdateRequest;

public interface QuestionService {
    public void updateQuestion(Integer id, QuestionUpdateRequest request);
}
