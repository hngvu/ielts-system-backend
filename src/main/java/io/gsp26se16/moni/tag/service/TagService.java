package io.gsp26se16.moni.tag.service;

import io.gsp26se16.moni.tag.dto.request.TagRequest;
import io.gsp26se16.moni.tag.dto.response.TagResponse;
import io.gsp26se16.moni.tag.entity.TagType;

import java.util.List;

public interface TagService {
    TagResponse createTag(TagRequest request);
    List<TagResponse> getTags(TagType type, String keyword);
    TagResponse getTagById(Long id);
    TagResponse updateTag(Long id, TagRequest request);
    void deleteTag(Long id);
}
