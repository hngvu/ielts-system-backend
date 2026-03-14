package io.gsp26se16.moni.roadmap.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import io.gsp26se16.moni.authentication.entity.Users;
import io.gsp26se16.moni.roadmap.entity.LearnerMetric;
import io.gsp26se16.moni.tag.entity.Tag;

@Repository
public interface LearnerMetricRepository extends JpaRepository<LearnerMetric, Integer> {
    Optional<LearnerMetric> findByUserAndTag(Users user, Tag tag);

    List<LearnerMetric> findTop3ByUserOrderByMasteryLevelAsc(Users user);
}
