package io.gsp26se16.moni.roadmap.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class TaskStatusUpdateRequest {
    @NotBlank(message = "Trạng thái không được để trống (VD: DONE)")
    private String status;
}
