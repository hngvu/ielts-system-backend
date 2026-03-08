package io.gsp26se16.moni.ai.writing.repository;

import io.gsp26se16.moni.common.enumeration.Skill;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import io.gsp26se16.moni.ai.writing.entity.AiEvaluation;

import java.util.Optional;

@Repository
public interface AiEvaluationRepository extends JpaRepository<AiEvaluation, Long> {
    Optional<AiEvaluation> findBySubmissionIdAndSkill(
            Long submissionId, Skill skill);
}
