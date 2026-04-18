package io.gsp26se16.moni.roadmap.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import io.gsp26se16.moni.authentication.entity.Users;
import io.gsp26se16.moni.roadmap.entity.MonthlyAssessment;

@Repository
public interface MonthlyAssessmentRepository extends JpaRepository<MonthlyAssessment, Integer> {

    Optional<MonthlyAssessment> findByUserAndStatus(Users user, String status);

    Optional<MonthlyAssessment> findTopByUserAndStatusOrderByIdDesc(Users user, String status);

    Optional<MonthlyAssessment> findTopByUserOrderByMonthCycleDesc(Users user);

    List<MonthlyAssessment> findByUser(Users user);

    Optional<MonthlyAssessment> findTopByUserAndStatusOrderByCompletedAtDesc(Users user, String status);
}
