package io.gsp26se16.moni.ai.writing.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import io.gsp26se16.moni.ai.writing.entity.AiEvaluation;
import io.gsp26se16.moni.common.enumeration.Skill;

@Repository
public interface AiEvaluationRepository extends JpaRepository<AiEvaluation, Long> {
    Optional<AiEvaluation> findBySubmissionIdAndSkill(Long submissionId, Skill skill);

    List<AiEvaluation> findBySubmissionId(Long submissionId);

    Optional<AiEvaluation> findFirstBySkillOrderByCreatedAtDesc(Skill skill);
}
