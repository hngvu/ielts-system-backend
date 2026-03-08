package io.gsp26se16.moni.ai.writing.repository;

import java.util.List;

import io.gsp26se16.moni.ai.writing.entity.WritingSubmission;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface WritingSubmissionRepository extends JpaRepository<WritingSubmission, Long> {

    /** Lấy tất cả bài Writing của một user */
    List<WritingSubmission> findByUserId(String userId);

    /** Lấy tất cả bài Writing trong một TestSession */
    List<WritingSubmission> findByTestSessionId(Integer sessionId);
}
