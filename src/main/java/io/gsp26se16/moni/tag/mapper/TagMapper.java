package io.gsp26se16.moni.tag.mapper;

import io.gsp26se16.moni.tag.dto.request.TagRequest;
import io.gsp26se16.moni.tag.dto.response.TagResponse;
import io.gsp26se16.moni.tag.entity.Tag;
import org.mapstruct.Mapper;
import org.mapstruct.MappingTarget;

@Mapper(componentModel = "spring")
public interface TagMapper {
    Tag toEntity(TagRequest request);

    TagResponse toResponse(Tag tag);

    // Hàm này giúp update các trường từ Request vào Entity có sẵn
    void updateTagFromRequest(TagRequest request, @MappingTarget Tag tag);
}
