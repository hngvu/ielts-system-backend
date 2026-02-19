package io.gsp26se16.moni.ai.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import io.gsp26se16.moni.ai.entity.AiEvaluation;

@Repository
public interface AiEvaluationRepository extends JpaRepository<AiEvaluation, Long> {}
