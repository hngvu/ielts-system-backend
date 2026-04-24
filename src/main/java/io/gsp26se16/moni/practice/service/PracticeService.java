package io.gsp26se16.moni.practice.service;

import java.util.List;

import io.gsp26se16.moni.practice.dto.request.SubmitAttemptRequest;
import io.gsp26se16.moni.practice.dto.response.AttemptHistoryResponse;
import io.gsp26se16.moni.practice.dto.response.SubmitAttemptResponse;

public interface PracticeService {
    SubmitAttemptResponse submitAttempt(SubmitAttemptRequest request);

    SubmitAttemptResponse getAttemptResult(Integer attemptId);

    List<AttemptHistoryResponse> getAttemptHistory();

    /** Admin: lấy lịch sử làm bài của 1 học viên cụ thể qua credentialId. */
    List<AttemptHistoryResponse> getAttemptHistoryByCredentialId(String credentialId);
}
