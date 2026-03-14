package io.gsp26se16.moni.roadmap.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import io.gsp26se16.moni.roadmap.entity.Goal;
import io.gsp26se16.moni.roadmap.entity.Roadmap;

@Repository
public interface RoadmapRepository extends JpaRepository<Roadmap, Integer> {
    Optional<Roadmap> findByGoalAndStatus(Goal goal, String status);
}
