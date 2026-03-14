package io.gsp26se16.moni.roadmap.dto.request;

import java.time.LocalDate;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotNull;

import io.gsp26se16.moni.common.enumeration.Skill;
import lombok.Data;

@Data
public class GoalCreateRequest {
    @NotNull(message = "Kỹ năng không được để trống")
    private Skill skill;

    @NotNull(message = "Điểm hiện tại không được để trống")
    @DecimalMin(value = "0.0", message = "Điểm IELTS thấp nhất là 0.0")
    @DecimalMax(value = "9.0", message = "Điểm IELTS cao nhất là 9.0")
    private Double startingBand;

    @NotNull(message = "Điểm mục tiêu không được để trống")
    @DecimalMin(value = "0.5", message = "Điểm mục tiêu thấp nhất là 0.5")
    @DecimalMax(value = "9.0", message = "Điểm IELTS cao nhất là 9.0")
    private Double targetBand;

    @NotNull(message = "Deadline không được để trống")
    @Future(message = "Deadline phải là một ngày trong tương lai")
    private LocalDate deadline;
}
