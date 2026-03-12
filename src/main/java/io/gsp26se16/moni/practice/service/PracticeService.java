package io.gsp26se16.moni.practice.service;

import io.gsp26se16.moni.practice.dto.request.SubmitAttemptRequest;
import io.gsp26se16.moni.practice.dto.response.SubmitAttemptResponse;

public interface PracticeService {
    public SubmitAttemptResponse submitAttempt(Integer sessionId, Integer attemptId, SubmitAttemptRequest request);

    public SubmitAttemptResponse getAttemptResult(Integer attemptId);
}
