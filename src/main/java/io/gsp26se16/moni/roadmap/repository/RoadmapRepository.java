package io.gsp26se16.moni.roadmap.repository;

import io.gsp26se16.moni.roadmap.entity.Goal;
import io.gsp26se16.moni.roadmap.entity.Roadmap;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface RoadmapRepository extends JpaRepository<Roadmap, Integer> {
    Optional<Roadmap> findByGoalAndStatus(Goal goal, String status);
}
