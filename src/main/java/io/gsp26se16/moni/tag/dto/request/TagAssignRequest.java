package io.gsp26se16.moni.tag.dto.request;

import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;

@Data
public class TagAssignRequest {
    @NotEmpty(message = "Danh sách Tag không được để trống")
    private List<Integer> tagIds;
}
