package io.gsp26se16.moni.roadmap.repository;

import io.gsp26se16.moni.roadmap.entity.LearnerMetric;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface LearnerMetricRepository extends JpaRepository<LearnerMetric, Integer> {
}
