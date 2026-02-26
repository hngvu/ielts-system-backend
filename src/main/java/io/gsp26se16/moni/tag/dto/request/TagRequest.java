package io.gsp26se16.moni.tag.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import io.gsp26se16.moni.tag.entity.TagType;
import lombok.Data;

@Data
public class TagRequest {
    @NotBlank(message = "Tag name must not be blank")
    private String name;

    @NotBlank(message = "Tag code must not be blank")
    private String code;

    @NotNull(message = "Tag type is required")
    private TagType type;

    private String description;
}
