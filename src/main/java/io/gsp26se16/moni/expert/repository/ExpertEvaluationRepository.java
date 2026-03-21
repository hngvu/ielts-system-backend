package io.gsp26se16.moni.expert.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import io.gsp26se16.moni.expert.entity.ExpertEvaluation;

@Repository
public interface ExpertEvaluationRepository extends JpaRepository<ExpertEvaluation, Integer> {}
