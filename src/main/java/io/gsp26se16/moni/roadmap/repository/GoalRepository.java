package io.gsp26se16.moni.roadmap.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import io.gsp26se16.moni.authentication.entity.Users;
import io.gsp26se16.moni.common.enumeration.Skill;
import io.gsp26se16.moni.roadmap.entity.Goal;

@Repository
public interface GoalRepository extends JpaRepository<Goal, Integer> {
    Optional<Goal> findTopByUserAndSkillAndStatusOrderByIdDesc(Users user, Skill skill, String status);

    List<Goal> findAllByUserAndStatus(Users user, String status);
}
