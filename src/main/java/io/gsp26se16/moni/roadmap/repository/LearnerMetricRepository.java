package io.gsp26se16.moni.roadmap.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import io.gsp26se16.moni.authentication.entity.Users;
import io.gsp26se16.moni.common.enumeration.Skill;
import io.gsp26se16.moni.roadmap.entity.LearnerMetric;
import io.gsp26se16.moni.tag.entity.Tag;

@Repository
public interface LearnerMetricRepository extends JpaRepository<LearnerMetric, Integer> {
    Optional<LearnerMetric> findByUserAndTagAndSkill(Users user, Tag tag, Skill skill);

    List<LearnerMetric> findTop8ByUserOrderByMasteryLevelAsc(Users user);

    List<LearnerMetric> findTop5ByUserOrderByMasteryLevelDesc(Users user);

    List<LearnerMetric> findByUserOrderByMasteryLevelAsc(Users user);

    List<LearnerMetric> findByUserOrderByMasteryLevelDesc(Users user);

    List<LearnerMetric> findByUserOrderByUpdatedAtDesc(Users user);

    List<LearnerMetric> findByUser(Users user);
}
