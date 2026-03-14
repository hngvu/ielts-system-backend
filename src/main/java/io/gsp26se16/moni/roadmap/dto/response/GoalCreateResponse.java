package io.gsp26se16.moni.roadmap.dto.response;

import java.time.LocalDate;

import io.gsp26se16.moni.common.enumeration.Skill;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class GoalCreateResponse {
    private Integer goalId;
    private Skill skill;
    private Double startingBand;
    private Double targetBand;
    private LocalDate deadline;
    private String status;

    // Thông tin Roadmap đi kèm
    private Integer roadmapId;
    private Integer roadmapVersion;
    private String message;
}
