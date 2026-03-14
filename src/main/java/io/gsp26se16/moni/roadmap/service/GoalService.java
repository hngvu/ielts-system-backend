package io.gsp26se16.moni.roadmap.service;

import io.gsp26se16.moni.roadmap.dto.request.GoalCreateRequest;
import io.gsp26se16.moni.roadmap.dto.request.GoalUpdateRequest;
import io.gsp26se16.moni.roadmap.dto.response.GoalCreateResponse;
import io.gsp26se16.moni.roadmap.dto.response.GoalResponse;

import java.util.List;

public interface GoalService {
    GoalCreateResponse createGoal(GoalCreateRequest request);
    List<GoalResponse> getActiveGoals();
    GoalCreateResponse updateGoal(Integer goalId, GoalUpdateRequest request);
}
