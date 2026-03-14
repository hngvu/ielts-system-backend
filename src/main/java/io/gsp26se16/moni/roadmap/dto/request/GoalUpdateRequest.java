package io.gsp26se16.moni.roadmap.dto.request;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDate;

@Data
public class GoalUpdateRequest {
    @NotNull(message = "Điểm mục tiêu không được để trống")
    @DecimalMin(value = "0.5", message = "Điểm mục tiêu thấp nhất là 0.5")
    @DecimalMax(value = "9.0", message = "Điểm IELTS cao nhất là 9.0")
    private Double targetBand;

    @NotNull(message = "Deadline không được để trống")
    @Future(message = "Deadline phải là một ngày trong tương lai")
    private LocalDate deadline;
}
