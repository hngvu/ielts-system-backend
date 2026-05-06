package io.gsp26se16.moni.ai.writing.repository;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import io.gsp26se16.moni.ai.writing.entity.WritingSubmission;
import io.gsp26se16.moni.common.enumeration.EvaluationStatus;

@Repository
public interface WritingSubmissionRepository extends JpaRepository<WritingSubmission, Long> {

    /** Lấy tất cả bài Writing của một user */
    List<WritingSubmission> findByUserId(String userId);

    /** Lấy tất cả bài Writing của một user, sắp xếp theo thời gian nộp mới nhất */
    List<WritingSubmission> findByUserIdOrderBySubmittedAtDesc(String userId);

    /** Lấy tất cả bài Writing trong một TestSession */
    List<WritingSubmission> findByTestSessionId(Integer sessionId);

    // ── AI Health Monitoring queries ──────────────────────────────────────

    long countByEvaluationStatus(EvaluationStatus status);

    long countByEvaluationStatusAndSubmittedAtBetween(EvaluationStatus status, LocalDateTime start, LocalDateTime end);

    long countBySubmittedAtBetween(LocalDateTime start, LocalDateTime end);
}
