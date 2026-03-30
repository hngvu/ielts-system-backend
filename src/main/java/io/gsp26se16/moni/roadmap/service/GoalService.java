package io.gsp26se16.moni.roadmap.service;

import java.time.LocalDate;
import java.util.List;

import io.gsp26se16.moni.authentication.entity.Users;
import io.gsp26se16.moni.roadmap.dto.request.GoalCreateRequest;
import io.gsp26se16.moni.roadmap.dto.request.GoalUpdateRequest;
import io.gsp26se16.moni.roadmap.dto.request.TaskStatusUpdateRequest;
import io.gsp26se16.moni.roadmap.dto.response.GoalCreateResponse;
import io.gsp26se16.moni.roadmap.dto.response.GoalResponse;
import io.gsp26se16.moni.roadmap.dto.response.LearnerRoadmapInsightsResponse;
import io.gsp26se16.moni.roadmap.dto.response.RoadmapDetailResponse;

public interface GoalService {
    GoalCreateResponse createGoal(GoalCreateRequest request);

    List<GoalResponse> getActiveGoals();

    GoalCreateResponse updateGoal(Integer goalId, GoalUpdateRequest request);

    void updateTaskStatus(Integer taskId, TaskStatusUpdateRequest request);

    void createGoalsFromPlacement(
            Users user,
            double readingBand,
            double listeningBand,
            double writingBand,
            double speakingBand,
            Double targetReading,
            Double targetListening,
            Double targetWriting,
            Double targetSpeaking,
            LocalDate examDate);

    List<RoadmapDetailResponse> getRoadmapDetails();

    LearnerRoadmapInsightsResponse getRoadmapInsights();
}
