package io.gsp26se16.moni.tag.dto.response;

import io.gsp26se16.moni.tag.entity.TagType;
import lombok.Data;

@Data
public class TagResponse {
    private Long id;
    private String name;
    private String code;
    private TagType type;
    private String description;
}
