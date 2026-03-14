package io.gsp26se16.moni.tag.service;

import java.util.List;

import io.gsp26se16.moni.tag.dto.request.TagAssignRequest;
import io.gsp26se16.moni.tag.dto.request.TagRequest;
import io.gsp26se16.moni.tag.dto.response.TagResponse;
import io.gsp26se16.moni.tag.entity.TagType;

public interface TagService {
    TagResponse createTag(TagRequest request);

    List<TagResponse> getTags(TagType type, String keyword);

    TagResponse getTagById(Integer id);

    TagResponse updateTag(Integer id, TagRequest request);

    void deleteTag(Integer id);

    void assignTagsToQuestion(Integer questionId, TagAssignRequest request);
    void assignTagsToStimulus(Integer stimulusId, TagAssignRequest request);
    void assignTagsToTest(Integer testId, TagAssignRequest request);
}
