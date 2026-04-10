package io.gsp26se16.moni.expert.service;

import java.util.List;
import java.util.Map;

import io.gsp26se16.moni.expert.dto.ScoringSessionResponse;
import io.gsp26se16.moni.expert.dto.SubmitEvaluationRequest;

public interface ScoringSessionService {
    ScoringSessionResponse createSession(
            String credentialId,
            Integer expertId,
            String skill,
            String content,
            Integer testId,
            Long writingSubmissionId);

    ScoringSessionResponse cancelSession(Integer sessionId, String credentialId);

    int getQueuePosition(Integer sessionId);

    Map<String, Object> getQueuePositionWithStatus(Integer sessionId);

    ScoringSessionResponse getSessionById(Integer sessionId, String credentialId);

    ScoringSessionResponse startSession(Integer sessionId, String credentialId);

    ScoringSessionResponse completeSession(Integer sessionId, SubmitEvaluationRequest evaluation, String credentialId);

    List<ScoringSessionResponse> getSessionsForExpert(String credentialId);

    ScoringSessionResponse rateSession(
            Integer sessionId, int rating, String comment, String recordingUrl, String credentialId);

    List<Map<String, Object>> getExpertReviews(Integer expertId);

    List<ScoringSessionResponse> getUserSessions(String credentialId);

    Map<String, Object> getEvaluation(Integer sessionId, String credentialId);

    List<ScoringSessionResponse> getAllSessions();

    List<ScoringSessionResponse> getAllSessionsForExpert(String credentialId);

    ScoringSessionResponse saveExpertRecording(Integer sessionId, String expertRecordingUrl, String credentialId);
}
