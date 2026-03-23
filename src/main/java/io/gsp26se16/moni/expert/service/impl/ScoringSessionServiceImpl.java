package io.gsp26se16.moni.expert.service.impl;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.gsp26se16.moni.authentication.repository.UserCredentialsRepository;
import io.gsp26se16.moni.common.exception.AppException;
import io.gsp26se16.moni.common.exception.ErrorCode;
import io.gsp26se16.moni.expert.dto.ScoringSessionResponse;
import io.gsp26se16.moni.expert.dto.SubmitEvaluationRequest;
import io.gsp26se16.moni.expert.entity.ExpertEvaluation;
import io.gsp26se16.moni.expert.entity.ExpertProfile;
import io.gsp26se16.moni.expert.entity.ScoringSession;
import io.gsp26se16.moni.expert.enumeration.SessionStatus;
import io.gsp26se16.moni.expert.repository.ExpertEvaluationRepository;
import io.gsp26se16.moni.expert.repository.ExpertProfileRepository;
import io.gsp26se16.moni.expert.repository.ScoringSessionRepository;
import io.gsp26se16.moni.expert.service.DailyCoService;
import io.gsp26se16.moni.expert.service.ScoringSessionService;
import io.gsp26se16.moni.payment.service.CreditService;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class ScoringSessionServiceImpl implements ScoringSessionService {

    ScoringSessionRepository sessionRepository;
    ExpertProfileRepository expertProfileRepository;
    ExpertEvaluationRepository evaluationRepository;
    UserCredentialsRepository userCredentialsRepository;
    CreditService creditService;
    DailyCoService dailyCoService;

    @Override
    @Transactional
    public ScoringSessionResponse createSession(String credentialId, Integer expertId, String skill, String content) {
        var credential = userCredentialsRepository
                .findById(credentialId)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_EXISTED));

        ExpertProfile expert = expertProfileRepository
                .findById(expertId)
                .orElseThrow(() -> new AppException(ErrorCode.EXPERT_NOT_FOUND));

        String serviceCode = "SPEAKING".equalsIgnoreCase(skill) ? "EXPERT_SPEAKING_SCORE" : "EXPERT_WRITING_SCORE";
        creditService.checkAndDeduct(credentialId, serviceCode);

        int queuePos = sessionRepository.countByExpertAndStatus(expert, SessionStatus.QUEUED) + 1;

        ScoringSession session = ScoringSession.builder()
                .user(credential.getUser())
                .expert(expert)
                .skill(skill)
                .content(content)
                .status(SessionStatus.QUEUED)
                .queuePosition(queuePos)
                .build();

        return toResponse(sessionRepository.save(session));
    }

    @Override
    @Transactional
    public void cancelSession(Integer sessionId, String credentialId) {
        ScoringSession session = sessionRepository
                .findById(sessionId)
                .orElseThrow(() -> new AppException(ErrorCode.SCORING_SESSION_NOT_FOUND));

        if (session.getStatus() != SessionStatus.QUEUED) {
            throw new AppException(ErrorCode.SESSION_NOT_CANCELLABLE);
        }

        session.setStatus(SessionStatus.CANCELLED);
        sessionRepository.save(session);
    }

    @Override
    public int getQueuePosition(Integer sessionId) {
        ScoringSession session = sessionRepository
                .findById(sessionId)
                .orElseThrow(() -> new AppException(ErrorCode.SCORING_SESSION_NOT_FOUND));
        return session.getQueuePosition() != null ? session.getQueuePosition() : 0;
    }

    @Override
    public java.util.Map<String, Object> getQueuePositionWithStatus(Integer sessionId) {
        ScoringSession session = sessionRepository
                .findById(sessionId)
                .orElseThrow(() -> new AppException(ErrorCode.SCORING_SESSION_NOT_FOUND));
        return java.util.Map.of(
                "position", session.getQueuePosition() != null ? session.getQueuePosition() : 0,
                "status", session.getStatus().name(),
                "roomUrl", session.getRoomUrl() != null ? session.getRoomUrl() : "");
    }

    @Override
    @Transactional
    public ScoringSessionResponse startSession(Integer sessionId) {
        ScoringSession session = sessionRepository
                .findById(sessionId)
                .orElseThrow(() -> new AppException(ErrorCode.SCORING_SESSION_NOT_FOUND));

        String roomName = "scoring-" + sessionId + "-" + System.currentTimeMillis();
        String roomUrl = dailyCoService.createRoom(roomName);

        session.setStatus(SessionStatus.IN_PROGRESS);
        session.setStartedAt(LocalDateTime.now());
        session.setRoomUrl(roomUrl);
        session.setRoomName(roomName);

        return toResponse(sessionRepository.save(session));
    }

    @Override
    @Transactional
    public void completeSession(Integer sessionId, SubmitEvaluationRequest req) {
        ScoringSession session = sessionRepository
                .findById(sessionId)
                .orElseThrow(() -> new AppException(ErrorCode.SCORING_SESSION_NOT_FOUND));

        ExpertEvaluation eval = ExpertEvaluation.builder()
                .scoringSession(session)
                .expertProfile(session.getExpert())
                .skill(session.getSkill())
                .overallScore(req.getOverallScore())
                .fluency(req.getFluency())
                .vocabulary(req.getVocabulary())
                .grammar(req.getGrammar())
                .pronunciation(req.getPronunciation())
                .taskResponse(req.getTaskResponse())
                .coherence(req.getCoherence())
                .lexicalResource(req.getLexicalResource())
                .grammaticalRange(req.getGrammaticalRange())
                .feedback(req.getFeedback())
                .strengths(req.getStrengths())
                .areasForImprovement(req.getAreasForImprovement())
                .build();

        session.setStatus(SessionStatus.COMPLETED);
        session.setEndedAt(LocalDateTime.now());

        ExpertProfile expert = session.getExpert();
        expert.setTotalSessions(expert.getTotalSessions() + 1);
        expertProfileRepository.save(expert);

        sessionRepository.save(session);
        evaluationRepository.save(eval);
    }

    @Override
    public List<ScoringSessionResponse> getSessionsForExpert(String credentialId) {
        var credential = userCredentialsRepository
                .findById(credentialId)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_EXISTED));

        ExpertProfile expert = expertProfileRepository
                .findByUser_Id(credential.getUser().getId())
                .orElseThrow(() -> new AppException(ErrorCode.EXPERT_NOT_FOUND));

        return sessionRepository.findByExpertAndStatus(expert, SessionStatus.QUEUED).stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    private ScoringSessionResponse toResponse(ScoringSession s) {
        return ScoringSessionResponse.builder()
                .id(s.getId())
                .expertId(s.getExpert() != null ? s.getExpert().getId() : null)
                .expertDisplayName(s.getExpert() != null ? s.getExpert().getDisplayName() : null)
                .skill(s.getSkill())
                .status(s.getStatus())
                .roomUrl(s.getRoomUrl())
                .roomName(s.getRoomName())
                .queuePosition(s.getQueuePosition())
                .createdAt(s.getCreatedAt())
                .build();
    }
}
