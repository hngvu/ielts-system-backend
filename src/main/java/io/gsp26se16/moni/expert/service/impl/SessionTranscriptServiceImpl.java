package io.gsp26se16.moni.expert.service.impl;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.gsp26se16.moni.authentication.entity.UserCredentials;
import io.gsp26se16.moni.authentication.entity.Users;
import io.gsp26se16.moni.authentication.repository.UserCredentialsRepository;
import io.gsp26se16.moni.common.exception.AppException;
import io.gsp26se16.moni.common.exception.ErrorCode;
import io.gsp26se16.moni.expert.dto.SessionTranscriptCreateRequest;
import io.gsp26se16.moni.expert.dto.SessionTranscriptResponse;
import io.gsp26se16.moni.expert.entity.ScoringSession;
import io.gsp26se16.moni.expert.entity.SessionTranscript;
import io.gsp26se16.moni.expert.repository.ScoringSessionRepository;
import io.gsp26se16.moni.expert.repository.SessionTranscriptRepository;
import io.gsp26se16.moni.expert.service.SessionTranscriptService;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class SessionTranscriptServiceImpl implements SessionTranscriptService {

    SessionTranscriptRepository transcriptRepository;
    ScoringSessionRepository sessionRepository;
    UserCredentialsRepository userCredentialsRepository;

    @Override
    @Transactional
    public List<SessionTranscriptResponse> append(
            Integer scoringSessionId, SessionTranscriptCreateRequest request, String credentialId) {

        if (request == null
                || request.getEntries() == null
                || request.getEntries().isEmpty()) {
            return List.of();
        }

        ScoringSession session = sessionRepository
                .findById(scoringSessionId)
                .orElseThrow(() -> new AppException(ErrorCode.SCORING_SESSION_NOT_FOUND));

        UserCredentials credential = userCredentialsRepository
                .findById(credentialId)
                .orElseThrow(() -> new AppException(ErrorCode.UNAUTHORIZED));

        Users user = credential.getUser();
        Speaker speaker = resolveSpeaker(session, user);

        List<SessionTranscript> rows = new ArrayList<>(request.getEntries().size());
        for (SessionTranscriptCreateRequest.Entry entry : request.getEntries()) {
            if (entry == null || entry.getText() == null || entry.getText().isBlank()) continue;
            rows.add(SessionTranscript.builder()
                    .scoringSession(session)
                    .speaker(user)
                    .speakerRole(speaker.role)
                    .speakerName(speaker.name)
                    .text(entry.getText().trim())
                    .language(entry.getLanguage() != null ? entry.getLanguage() : "en-US")
                    .spokenAt(entry.getSpokenAt() != null ? entry.getSpokenAt() : LocalDateTime.now())
                    .build());
        }

        if (rows.isEmpty()) return List.of();
        List<SessionTranscript> saved = transcriptRepository.saveAll(rows);
        return saved.stream().map(this::toResponse).toList();
    }

    @Override
    public List<SessionTranscriptResponse> list(Integer scoringSessionId, String credentialId, boolean isAdmin) {
        ScoringSession session = sessionRepository
                .findById(scoringSessionId)
                .orElseThrow(() -> new AppException(ErrorCode.SCORING_SESSION_NOT_FOUND));

        if (!isAdmin) {
            UserCredentials credential = userCredentialsRepository
                    .findById(credentialId)
                    .orElseThrow(() -> new AppException(ErrorCode.UNAUTHORIZED));
            String uid = credential.getUser().getId();
            boolean isOwner =
                    session.getUser() != null && uid.equals(session.getUser().getId());
            boolean isExpert = session.getExpert() != null
                    && session.getExpert().getUser() != null
                    && uid.equals(session.getExpert().getUser().getId());
            if (!isOwner && !isExpert) {
                throw new AppException(ErrorCode.UNAUTHORIZED);
            }
        }

        return transcriptRepository.findByScoringSession_IdOrderBySpokenAtAsc(scoringSessionId).stream()
                .map(this::toResponse)
                .toList();
    }

    private Speaker resolveSpeaker(ScoringSession session, Users user) {
        boolean isOwner = session.getUser() != null
                && user.getId().equals(session.getUser().getId());
        boolean isExpert = session.getExpert() != null
                && session.getExpert().getUser() != null
                && user.getId().equals(session.getExpert().getUser().getId());
        if (!isOwner && !isExpert) {
            throw new AppException(ErrorCode.UNAUTHORIZED);
        }
        String role = isExpert ? "EXPERT" : "LEARNER";
        String name = user.getFull_name() != null ? user.getFull_name() : (isExpert ? "Expert" : "Learner");
        return new Speaker(role, name);
    }

    private SessionTranscriptResponse toResponse(SessionTranscript t) {
        return SessionTranscriptResponse.builder()
                .id(t.getId())
                .scoringSessionId(
                        t.getScoringSession() != null ? t.getScoringSession().getId() : null)
                .speakerName(t.getSpeakerName())
                .speakerRole(t.getSpeakerRole())
                .text(t.getText())
                .language(t.getLanguage())
                .spokenAt(t.getSpokenAt())
                .build();
    }

    private record Speaker(String role, String name) {}
}
