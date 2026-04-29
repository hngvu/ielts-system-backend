package io.gsp26se16.moni.expert.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import io.gsp26se16.moni.expert.entity.SessionTranscript;

@Repository
public interface SessionTranscriptRepository extends JpaRepository<SessionTranscript, Long> {

    List<SessionTranscript> findByScoringSession_IdOrderBySpokenAtAsc(Integer scoringSessionId);
}
