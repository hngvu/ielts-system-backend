package io.gsp26se16.moni.ai.speaking.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import io.gsp26se16.moni.ai.speaking.entity.SpeakingSession;

@Repository
public interface SpeakingSessionRepository extends JpaRepository<SpeakingSession, String> {}
