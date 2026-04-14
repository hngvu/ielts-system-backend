package io.gsp26se16.moni.roadmap.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import io.gsp26se16.moni.authentication.entity.Users;
import io.gsp26se16.moni.roadmap.entity.WeeklyPlan;

@Repository
public interface WeeklyPlanRepository extends JpaRepository<WeeklyPlan, Integer> {

    Optional<WeeklyPlan> findFirstByUserAndStatusOrderByWeekNumberDesc(Users user, String status);

    Optional<WeeklyPlan> findFirstByUserAndWeekNumber(Users user, Integer weekNumber);

    Optional<WeeklyPlan> findTopByUserOrderByWeekNumberDesc(Users user);

    List<WeeklyPlan> findTop4ByUserOrderByWeekNumberDesc(Users user);

    List<WeeklyPlan> findByUserOrderByWeekNumberDesc(Users user);
}
