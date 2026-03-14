package io.gsp26se16.moni.roadmap.repository;

import io.gsp26se16.moni.roadmap.entity.Task;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface TaskRepository extends JpaRepository<Task, Integer> {
    long countByRoadmapIdAndStatusNot(Integer roadmapId, String status);
}
