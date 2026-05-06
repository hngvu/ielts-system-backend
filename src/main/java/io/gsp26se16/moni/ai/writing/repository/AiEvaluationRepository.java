package io.gsp26se16.moni.ai.writing.repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import io.gsp26se16.moni.ai.writing.entity.AiEvaluation;
import io.gsp26se16.moni.common.enumeration.Skill;

@Repository
public interface AiEvaluationRepository extends JpaRepository<AiEvaluation, Long> {
    Optional<AiEvaluation> findBySubmissionIdAndSkill(Long submissionId, Skill skill);

    List<AiEvaluation> findBySubmissionId(Long submissionId);

    Optional<AiEvaluation> findFirstBySkillOrderByCreatedAtDesc(Skill skill);

    // ── AI Health Monitoring queries ──────────────────────────────────────

    long countBySkill(Skill skill);

    long countByCreatedAtBetween(LocalDateTime start, LocalDateTime end);

    @Query(
            value = "SELECT AVG(EXTRACT(EPOCH FROM (ae.created_at - ws.processing_started_at))) "
                    + "FROM ai_evaluations ae JOIN writing_submissions ws ON ae.submission_id = ws.id "
                    + "WHERE ae.skill = 'WRITING' AND ws.processing_started_at IS NOT NULL "
                    + "AND ae.created_at BETWEEN :start AND :end",
            nativeQuery = true)
    Double avgWritingLatencySeconds(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

    @Query(
            value = "SELECT AVG(EXTRACT(EPOCH FROM (ae.created_at - ss.processing_started_at))) "
                    + "FROM ai_evaluations ae JOIN speaking_submissions ss ON ae.submission_id = ss.id "
                    + "WHERE ae.skill = 'SPEAKING' AND ss.processing_started_at IS NOT NULL "
                    + "AND ae.created_at BETWEEN :start AND :end",
            nativeQuery = true)
    Double avgSpeakingLatencySeconds(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);
}
