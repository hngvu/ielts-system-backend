package io.gsp26se16.moni.practice.service;

import io.gsp26se16.moni.practice.dto.request.TestSessionCreateRequest;
import io.gsp26se16.moni.practice.dto.response.TestSessionResponse;
import io.gsp26se16.moni.practice.dto.response.TestSessionResultResponse;

public interface TestSessionService {
    TestSessionResponse startTestSession(Integer userId, TestSessionCreateRequest request);
    TestSessionResultResponse submitTestSession(Integer sessionId);
}
