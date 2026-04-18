package io.gsp26se16.moni.roadmap.repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import io.gsp26se16.moni.authentication.entity.Users;
import io.gsp26se16.moni.roadmap.entity.DailySlot;
import io.gsp26se16.moni.roadmap.entity.WeeklyPlan;

@Repository
public interface DailySlotRepository extends JpaRepository<DailySlot, Integer> {

    List<DailySlot> findByWeeklyPlanOrderByDayOfWeekAscIdAsc(WeeklyPlan plan);

    List<DailySlot> findByWeeklyPlanAndDayOfWeek(WeeklyPlan plan, Integer dayOfWeek);

    List<DailySlot> findByWeeklyPlanAndSlotDate(WeeklyPlan plan, LocalDate date);

    Optional<DailySlot> findByWeeklyPlanAndDayOfWeekAndSkill(
            WeeklyPlan plan, Integer dayOfWeek, io.gsp26se16.moni.common.enumeration.Skill skill);

    long countByWeeklyPlanAndStatus(WeeklyPlan plan, String status);

    long countByWeeklyPlan(WeeklyPlan plan);

    /** Find all stimulus IDs the user has already completed to avoid repeats */
    @Query("SELECT ds.stimulus.id FROM DailySlot ds "
            + "WHERE ds.weeklyPlan.user = :user AND ds.status = 'DONE' AND ds.stimulus IS NOT NULL")
    List<Integer> findDoneStimulusIdsByUser(@Param("user") Users user);

    /** Find slots matching a stimulus + user + date for auto-completion detection */
    @Query("SELECT ds FROM DailySlot ds "
            + "WHERE ds.weeklyPlan.user = :user "
            + "AND ds.stimulus.id = :stimulusId "
            + "AND ds.slotDate = :date "
            + "AND ds.status = 'TODO'")
    List<DailySlot> findMatchingSlots(
            @Param("user") Users user, @Param("stimulusId") Integer stimulusId, @Param("date") LocalDate date);

    int deleteByWeeklyPlan(WeeklyPlan plan);

    Optional<DailySlot> findFirstByWeeklyPlanUserAndSkillAndTaskTypeAndStatusOrderByCompletedAtDesc(
            Users user, io.gsp26se16.moni.common.enumeration.Skill skill, String taskType, String status);
}
