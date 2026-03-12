package io.gsp26se16.moni.practice.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class TestSessionCreateRequest {
    @NotNull(message = "Test ID không được để trống")
    private Integer testId;
}
