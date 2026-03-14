package io.gsp26se16.moni.tag.dto.request;

import java.util.List;

import jakarta.validation.constraints.NotEmpty;

import lombok.Data;

@Data
public class TagAssignRequest {
    @NotEmpty(message = "Danh sách Tag không được để trống")
    private List<Integer> tagIds;
}
