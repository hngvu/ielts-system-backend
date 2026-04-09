package io.gsp26se16.moni.roadmap.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import io.gsp26se16.moni.roadmap.entity.Roadmap;
import io.gsp26se16.moni.roadmap.entity.Task;

@Repository
public interface TaskRepository extends JpaRepository<Task, Integer> {
    long countByRoadmapIdAndStatusNot(Integer roadmapId, String status);

    List<Task> findAllByRoadmapOrderByOrderAsc(Roadmap roadmap);

    List<Task> findAllByRoadmapAndTaskType(Roadmap roadmap, String taskType);

    double countByRoadmapId(Integer id);

    List<Task> findAllByRoadmap(Roadmap currentRoadmap);
}
