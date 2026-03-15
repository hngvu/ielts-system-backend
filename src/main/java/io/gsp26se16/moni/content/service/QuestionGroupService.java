package io.gsp26se16.moni.content.service;

import io.gsp26se16.moni.content.dto.request.QuestionGroupCreateRequest;

public interface QuestionGroupService {
    Integer createForStimulus(Integer stimulusId, QuestionGroupCreateRequest request);

    void deleteQuestionGroup(Integer id);

    void updateImageUrl(Integer id, String imageUrl);

    void updateGroupContent(Integer id, String groupContent);
}
