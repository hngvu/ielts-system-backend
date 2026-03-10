package io.gsp26se16.moni.practice.service;

import io.gsp26se16.moni.practice.dto.request.SubmitAttemptRequest;
import io.gsp26se16.moni.practice.dto.response.SubmitAttemptResponse;

public interface PracticeService {
    SubmitAttemptResponse submitAttempt(SubmitAttemptRequest request);

    SubmitAttemptResponse getAttemptResult(Integer attemptId);
}
