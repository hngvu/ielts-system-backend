package io.gsp26se16.moni.roadmap.dto.response;

import java.util.List;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldDefaults;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class RoadmapDetailResponse {
    Integer goalId;
    String skill;
    Double startingBand;
    Double targetBand;
    String deadline;
    Integer roadmapId;
    Integer roadmapVersion;
    List<TaskResponse> tasks;
    Double progress; // percentage 0-100
}
