package io.gsp26se16.moni.ai.speaking.repository;


import java.util.List;
import java.util.Optional;

import io.gsp26se16.moni.ai.speaking.entity.SpeakingSubmission;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface SpeakingSubmissionRepository extends JpaRepository<SpeakingSubmission, Long> {

    /** Lấy tất cả bài Speaking của một user */
    List<SpeakingSubmission> findByUserId(String userId);

    /** Lấy bài Speaking theo WebSocket speakingSession */
    Optional<SpeakingSubmission> findBySpeakingSessionId(String speakingSessionId);

    /** Lấy tất cả bài Speaking trong một TestSession */
    List<SpeakingSubmission> findByTestSessionId(Integer sessionId);
}