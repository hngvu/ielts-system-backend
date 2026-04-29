package io.gsp26se16.moni.expert.service;

import java.util.List;

import io.gsp26se16.moni.expert.dto.SessionTranscriptCreateRequest;
import io.gsp26se16.moni.expert.dto.SessionTranscriptResponse;

public interface SessionTranscriptService {

    /** Caller (learner or expert of the session) appends a batch of finalized captions. */
    List<SessionTranscriptResponse> append(
            Integer scoringSessionId, SessionTranscriptCreateRequest request, String credentialId);

    /** Caller (learner of session, expert of session, or any ADMIN) lists all captions. */
    List<SessionTranscriptResponse> list(Integer scoringSessionId, String credentialId, boolean isAdmin);
}
