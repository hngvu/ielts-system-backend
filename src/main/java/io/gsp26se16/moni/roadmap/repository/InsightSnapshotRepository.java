package io.gsp26se16.moni.roadmap.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import io.gsp26se16.moni.authentication.entity.Users;
import io.gsp26se16.moni.roadmap.entity.InsightSnapshot;

@Repository
public interface InsightSnapshotRepository extends JpaRepository<InsightSnapshot, Integer> {
    Optional<InsightSnapshot> findFirstByUserAndWeekNumberOrderByCreatedAtDesc(Users user, Integer weekNumber);

    List<InsightSnapshot> findByUser(Users user);
}
