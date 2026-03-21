package io.gsp26se16.moni.expert.service;

import java.util.List;

import io.gsp26se16.moni.expert.dto.ScoringSessionResponse;
import io.gsp26se16.moni.expert.dto.SubmitEvaluationRequest;

public interface ScoringSessionService {
    ScoringSessionResponse createSession(String credentialId, Integer expertId, String skill, String content);

    void cancelSession(Integer sessionId, String credentialId);

    int getQueuePosition(Integer sessionId);

    ScoringSessionResponse startSession(Integer sessionId);

    void completeSession(Integer sessionId, SubmitEvaluationRequest evaluation);

    List<ScoringSessionResponse> getSessionsForExpert(String credentialId);
}
