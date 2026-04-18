package io.gsp26se16.moni.roadmap.service;

import java.time.LocalDate;
import java.util.List;

import io.gsp26se16.moni.authentication.entity.Users;
import io.gsp26se16.moni.roadmap.dto.response.GoalResponse;
import io.gsp26se16.moni.roadmap.dto.response.LearnerRoadmapInsightsResponse;

public interface GoalService {

    List<GoalResponse> getActiveGoals();

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

    LearnerRoadmapInsightsResponse getRoadmapInsights();

    LearnerRoadmapInsightsResponse getRoadmapInsightsByWeek(Integer weekNumber);

    void snapshotInsightsForWeek(Users user, Integer weekNumber);
}
