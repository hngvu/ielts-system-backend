package io.gsp26se16.moni.roadmap.dto.response;

import io.gsp26se16.moni.common.enumeration.Skill;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDate;

@Data
@Builder
public class GoalResponse {
    private Integer goalId;
    private Skill skill;
    private Double startingBand;
    private Double targetBand;
    private LocalDate deadline;
    private String status;

    private Integer activeRoadmapId;
    private Integer activeRoadmapVersion;
}
