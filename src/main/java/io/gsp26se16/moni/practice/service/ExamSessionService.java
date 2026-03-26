package io.gsp26se16.moni.practice.service;

import io.gsp26se16.moni.practice.dto.request.SaveProgressRequest;
import io.gsp26se16.moni.practice.dto.request.StartExamRequest;
import io.gsp26se16.moni.practice.dto.request.SubmitExamRequest;
import io.gsp26se16.moni.practice.dto.response.ExamSessionResponse;
import io.gsp26se16.moni.practice.dto.response.SubmitAttemptResponse;
import io.gsp26se16.moni.practice.entity.TestSession;

public interface ExamSessionService {
    ExamSessionResponse startExam(StartExamRequest request);

    ExamSessionResponse getActiveSession(Integer testId);

    void saveProgress(SaveProgressRequest request);

    SubmitAttemptResponse submitExam(SubmitExamRequest request);

    void expireSession(TestSession session);
}
