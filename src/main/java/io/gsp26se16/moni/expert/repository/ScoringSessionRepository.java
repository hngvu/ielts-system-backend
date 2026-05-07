package io.gsp26se16.moni.expert.repository;

import java.sql.Timestamp;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import io.gsp26se16.moni.expert.entity.ExpertProfile;
import io.gsp26se16.moni.expert.entity.ScoringSession;
import io.gsp26se16.moni.expert.enumeration.SessionStatus;

@Repository
public interface ScoringSessionRepository extends JpaRepository<ScoringSession, Integer> {
    List<ScoringSession> findByExpertAndStatus(ExpertProfile expert, SessionStatus status);

    int countByExpertAndStatus(ExpertProfile expert, SessionStatus status);

    @Query("SELECT AVG(s.userRating) FROM ScoringSession s WHERE s.expert = ?1 AND s.userRating IS NOT NULL")
    Double averageRatingByExpert(ExpertProfile expert);

    List<ScoringSession> findByExpert_IdAndUserRatingIsNotNullOrderByCreatedAtDesc(Integer expertId);

    List<ScoringSession> findByExpertOrderByCreatedAtDesc(ExpertProfile expert);

    List<ScoringSession> findByUser_IdOrderByCreatedAtDesc(String userId);

    List<ScoringSession> findByStatusAndCreatedAtBefore(SessionStatus status, Timestamp cutoff);

    Optional<ScoringSession> findByWritingSubmissionId(Long writingSubmissionId);
}
