package io.gsp26se16.moni.roadmap.service;

import java.util.List;

import io.gsp26se16.moni.authentication.entity.Users;
import io.gsp26se16.moni.roadmap.dto.response.MonthlyAssessmentResponse;
import io.gsp26se16.moni.roadmap.dto.response.WeeklyPlanDetailResponse;
import io.gsp26se16.moni.roadmap.dto.response.WeeklyPlanSummaryResponse;
import io.gsp26se16.moni.vocab.dto.QuizResponse;
import io.gsp26se16.moni.vocab.dto.VocabResponse;

public interface WeeklyPlanService {

    /** Generate the first or next weekly plan for a user */
    void generateWeeklyPlan(Users user);

    /** Get the currently active weekly plan with all slots */
    WeeklyPlanDetailResponse getCurrentPlan();

    /** Get only today's slots */
    WeeklyPlanDetailResponse getTodaySlots();

    /** Mark a specific slot as completed */
    void completeSlot(Integer slotId, Integer score, Integer totalQuestions);

    /** Auto-detect and complete slot based on stimulus + user + date */
    void autoCompleteSlot(Users user, Integer stimulusId, Integer score, Integer totalQuestions);

    /** Evaluate the current week's performance, close it, and generate next */
    WeeklyPlanDetailResponse evaluateWeekAndGenerateNext();

    /** Get history of past weekly plans */
    List<WeeklyPlanSummaryResponse> getHistory();

    /** Get any pending monthly assessment */
    MonthlyAssessmentResponse getPendingMonthlyAssessment();

    /** Get the latest assessment-based band for a specific skill */
    double getCurrentBandForSkill(
            io.gsp26se16.moni.authentication.entity.Users user,
            io.gsp26se16.moni.common.enumeration.Skill skill,
            io.gsp26se16.moni.roadmap.entity.WeeklyPlan previousPlan);

    /** Initialize a vocabulary learning slot and fetch words */
    List<VocabResponse> startVocabLearning(Integer slotId);

    /** Generate a quiz for a vocabulary test slot */
    QuizResponse getVocabQuiz(Integer slotId);
}
