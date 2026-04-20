package io.gsp26se16.moni.expert.service.impl;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.gsp26se16.moni.ai.writing.entity.WritingSubmission;
import io.gsp26se16.moni.ai.writing.repository.WritingSubmissionRepository;
import io.gsp26se16.moni.authentication.repository.UserCredentialsRepository;
import io.gsp26se16.moni.common.exception.AppException;
import io.gsp26se16.moni.common.exception.ErrorCode;
import io.gsp26se16.moni.content.entity.Test;
import io.gsp26se16.moni.content.repository.TestRepository;
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
    WritingSubmissionRepository writingSubmissionRepository;
    DailyCoService dailyCoService;
    TestRepository testRepository;

    @Override
    @Transactional
    public ScoringSessionResponse createSession(
            String credentialId,
            Integer expertId,
            String skill,
            String content,
            Integer testId,
            Long writingSubmissionId) {
        var credential = userCredentialsRepository
                .findById(credentialId)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_EXISTED));

        ExpertProfile expert = expertProfileRepository
                .findById(expertId)
                .orElseThrow(() -> new AppException(ErrorCode.EXPERT_NOT_FOUND));

        if (expert.getStatus() == io.gsp26se16.moni.expert.enumeration.ExpertStatus.OFFLINE) {
            throw new AppException(ErrorCode.EXPERT_NOT_AVAILABLE);
        }

        String serviceCode = "SPEAKING".equalsIgnoreCase(skill) ? "EXPERT_SPEAKING_SCORE" : "EXPERT_WRITING_SCORE";
        creditService.checkAndDeduct(credentialId, serviceCode);

        int queuePos = sessionRepository.countByExpertAndStatus(expert, SessionStatus.QUEUED) + 1;

        ScoringSession session = ScoringSession.builder()
                .user(credential.getUser())
                .expert(expert)
                .skill(skill)
                .content(content)
                .testId(testId)
                .writingSubmissionId(writingSubmissionId)
                .status(SessionStatus.QUEUED)
                .queuePosition(queuePos)
                .build();

        ScoringSession saved = sessionRepository.save(session);

        // Update WritingSubmission status to PROCESSING (sent to expert)
        if (writingSubmissionId != null) {
            writingSubmissionRepository.findById(writingSubmissionId).ifPresent(sub -> {
                sub.setEvaluationStatus(io.gsp26se16.moni.common.enumeration.EvaluationStatus.PROCESSING);
                writingSubmissionRepository.save(sub);
            });
        }

        return toResponse(saved);
    }

    @Override
    @Transactional
    public ScoringSessionResponse cancelSession(Integer sessionId, String credentialId) {
        var credential = userCredentialsRepository
                .findById(credentialId)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_EXISTED));

        ScoringSession session = sessionRepository
                .findById(sessionId)
                .orElseThrow(() -> new AppException(ErrorCode.SCORING_SESSION_NOT_FOUND));

        // Validate that the caller owns this session
        if (session.getUser() == null
                || !session.getUser().getId().equals(credential.getUser().getId())) {
            throw new AppException(ErrorCode.UNAUTHORIZED);
        }

        if (session.getStatus() != SessionStatus.QUEUED) {
            throw new AppException(ErrorCode.SESSION_NOT_CANCELLABLE);
        }

        session.setStatus(SessionStatus.CANCELLED);

        // Reset linked WritingSubmission back to PENDING — nếu không, /scoring-history
        // vẫn hiện "Đang chấm..." dù session đã huỷ (vì FE đọc evaluationStatus=PROCESSING).
        if (session.getWritingSubmissionId() != null) {
            writingSubmissionRepository
                    .findById(session.getWritingSubmissionId())
                    .ifPresent(sub -> {
                        sub.setEvaluationStatus(io.gsp26se16.moni.common.enumeration.EvaluationStatus.PENDING);
                        writingSubmissionRepository.save(sub);
                    });
        }

        // Hoàn credit cho người dùng đã tạo session
        String serviceCode =
                "SPEAKING".equalsIgnoreCase(session.getSkill()) ? "EXPERT_SPEAKING_SCORE" : "EXPERT_WRITING_SCORE";
        creditService.refund(credentialId, serviceCode);

        return toResponse(sessionRepository.save(session));
    }

    @Override
    public int getQueuePosition(Integer sessionId) {
        ScoringSession session = sessionRepository
                .findById(sessionId)
                .orElseThrow(() -> new AppException(ErrorCode.SCORING_SESSION_NOT_FOUND));
        return session.getQueuePosition() != null ? session.getQueuePosition() : 0;
    }

    @Override
    public ScoringSessionResponse getSessionById(Integer sessionId, String credentialId) {
        var credential = userCredentialsRepository
                .findById(credentialId)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_EXISTED));

        ScoringSession session = sessionRepository
                .findById(sessionId)
                .orElseThrow(() -> new AppException(ErrorCode.SCORING_SESSION_NOT_FOUND));

        // Validate ownership: caller must be either the session owner (user) or the assigned expert
        boolean isSessionOwner = session.getUser() != null
                && session.getUser().getId().equals(credential.getUser().getId());
        boolean isAssignedExpert = session.getExpert() != null
                && session.getExpert().getUser() != null
                && session.getExpert()
                        .getUser()
                        .getId()
                        .equals(credential.getUser().getId());

        if (!isSessionOwner && !isAssignedExpert) {
            throw new AppException(ErrorCode.UNAUTHORIZED);
        }

        return toResponse(session);
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
    public ScoringSessionResponse startSession(Integer sessionId, String credentialId) {
        var credential = userCredentialsRepository
                .findById(credentialId)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_EXISTED));

        ScoringSession session = sessionRepository
                .findById(sessionId)
                .orElseThrow(() -> new AppException(ErrorCode.SCORING_SESSION_NOT_FOUND));

        // Validate that the caller is the assigned expert for this session
        if (session.getExpert() == null
                || session.getExpert().getUser() == null
                || !session.getExpert()
                        .getUser()
                        .getId()
                        .equals(credential.getUser().getId())) {
            throw new AppException(ErrorCode.UNAUTHORIZED);
        }

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
    public ScoringSessionResponse completeSession(Integer sessionId, SubmitEvaluationRequest req, String credentialId) {
        var credential = userCredentialsRepository
                .findById(credentialId)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_EXISTED));

        ScoringSession session = sessionRepository
                .findById(sessionId)
                .orElseThrow(() -> new AppException(ErrorCode.SCORING_SESSION_NOT_FOUND));

        // Validate that the caller is the assigned expert for this session
        if (session.getExpert() == null
                || session.getExpert().getUser() == null
                || !session.getExpert()
                        .getUser()
                        .getId()
                        .equals(credential.getUser().getId())) {
            throw new AppException(ErrorCode.UNAUTHORIZED);
        }

        // Auto-calculate overall from criteria scores
        double overall = 0;
        int count = 0;
        if ("SPEAKING".equalsIgnoreCase(session.getSkill())) {
            Double[] scores = {req.getFluency(), req.getVocabulary(), req.getGrammar(), req.getPronunciation()};
            for (Double s : scores) {
                if (s != null) {
                    overall += s;
                    count++;
                }
            }
        } else {
            Double[] scores = {
                req.getTaskResponse(), req.getCoherence(), req.getLexicalResource(), req.getGrammaticalRange()
            };
            for (Double s : scores) {
                if (s != null) {
                    overall += s;
                    count++;
                }
            }
        }
        double overallScore = count > 0 ? Math.round((overall / count) * 2) / 2.0 : 0;

        ExpertEvaluation eval = ExpertEvaluation.builder()
                .scoringSession(session)
                .expertProfile(session.getExpert())
                .skill(session.getSkill())
                .overallScore(overallScore)
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

        ScoringSession saved = sessionRepository.save(session);
        evaluationRepository.save(eval);

        // Update linked WritingSubmission status to COMPLETED
        if (saved.getWritingSubmissionId() != null) {
            writingSubmissionRepository.findById(saved.getWritingSubmissionId()).ifPresent(sub -> {
                sub.setEvaluationStatus(io.gsp26se16.moni.common.enumeration.EvaluationStatus.COMPLETED);
                writingSubmissionRepository.save(sub);
            });
        }

        return toResponse(saved);
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

    @Override
    public List<ScoringSessionResponse> getAllSessionsForExpert(String credentialId) {
        var credential = userCredentialsRepository
                .findById(credentialId)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_EXISTED));

        ExpertProfile expert = expertProfileRepository
                .findByUser_Id(credential.getUser().getId())
                .orElseThrow(() -> new AppException(ErrorCode.EXPERT_NOT_FOUND));

        return sessionRepository.findByExpertOrderByCreatedAtDesc(expert).stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public ScoringSessionResponse saveExpertRecording(
            Integer sessionId, String expertRecordingUrl, String credentialId) {
        var credential = userCredentialsRepository
                .findById(credentialId)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_EXISTED));

        ScoringSession session = sessionRepository
                .findById(sessionId)
                .orElseThrow(() -> new AppException(ErrorCode.SCORING_SESSION_NOT_FOUND));

        // Validate that the caller is the assigned expert for this session
        if (session.getExpert() == null
                || session.getExpert().getUser() == null
                || !session.getExpert()
                        .getUser()
                        .getId()
                        .equals(credential.getUser().getId())) {
            throw new AppException(ErrorCode.UNAUTHORIZED);
        }

        if (expertRecordingUrl != null && !expertRecordingUrl.isBlank()) {
            session.setExpertRecordingUrl(expertRecordingUrl);
        }
        return toResponse(sessionRepository.save(session));
    }

    @Override
    @Transactional
    public ScoringSessionResponse rateSession(
            Integer sessionId, int rating, String comment, String recordingUrl, String credentialId) {
        var credential = userCredentialsRepository
                .findById(credentialId)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_EXISTED));

        ScoringSession session = sessionRepository
                .findById(sessionId)
                .orElseThrow(() -> new AppException(ErrorCode.SCORING_SESSION_NOT_FOUND));

        // Validate that the caller owns this session (only the learner who booked can rate)
        if (session.getUser() == null
                || !session.getUser().getId().equals(credential.getUser().getId())) {
            throw new AppException(ErrorCode.UNAUTHORIZED);
        }

        // rating=0 means user skipped rating, only save recordingUrl
        if (rating > 0) {
            session.setUserRating(rating);
            session.setUserComment(comment);
        }
        if (recordingUrl != null && !recordingUrl.isBlank()) {
            session.setRecordingUrl(recordingUrl);
        }
        ScoringSession saved = sessionRepository.save(session);

        // Update expert average rating (only from sessions that have a rating)
        if (rating > 0 && session.getExpert() != null) {
            ExpertProfile expert = session.getExpert();
            Double avg = sessionRepository.averageRatingByExpert(expert);
            expert.setRating(avg != null ? Math.round(avg * 10) / 10.0 : 0.0);
            expertProfileRepository.save(expert);
        }

        return toResponse(saved);
    }

    @Override
    @Transactional
    public ScoringSessionResponse attachUserRecording(Integer sessionId, String recordingUrl, String credentialId) {
        if (recordingUrl == null || recordingUrl.isBlank()) {
            throw new AppException(ErrorCode.INVALID_KEY);
        }
        var credential = userCredentialsRepository
                .findById(credentialId)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_EXISTED));

        ScoringSession session = sessionRepository
                .findById(sessionId)
                .orElseThrow(() -> new AppException(ErrorCode.SCORING_SESSION_NOT_FOUND));

        // Only the learner who booked can attach their recording
        if (session.getUser() == null
                || !session.getUser().getId().equals(credential.getUser().getId())) {
            throw new AppException(ErrorCode.UNAUTHORIZED);
        }

        session.setRecordingUrl(recordingUrl);
        return toResponse(sessionRepository.save(session));
    }

    @Override
    public java.util.List<java.util.Map<String, Object>> getExpertReviews(Integer expertId) {
        return sessionRepository.findByExpert_IdAndUserRatingIsNotNullOrderByCreatedAtDesc(expertId).stream()
                .limit(10)
                .map(s -> {
                    java.util.Map<String, Object> map = new java.util.LinkedHashMap<>();
                    map.put("rating", s.getUserRating());
                    map.put("comment", s.getUserComment());
                    map.put("createdAt", s.getCreatedAt());
                    map.put("userName", s.getUser() != null ? s.getUser().getFull_name() : null);
                    return map;
                })
                .collect(Collectors.toList());
    }

    @Override
    public List<ScoringSessionResponse> getUserSessions(String credentialId) {
        var credential = userCredentialsRepository
                .findById(credentialId)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_EXISTED));
        return sessionRepository
                .findByUser_IdOrderByCreatedAtDesc(credential.getUser().getId())
                .stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    @Override
    public java.util.Map<String, Object> getEvaluation(Integer sessionId, String credentialId) {
        var credential = userCredentialsRepository
                .findById(credentialId)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_EXISTED));

        ScoringSession session = sessionRepository
                .findById(sessionId)
                .orElseThrow(() -> new AppException(ErrorCode.SCORING_SESSION_NOT_FOUND));

        // Validate ownership: caller must be either the session owner or the assigned expert
        boolean isSessionOwner = session.getUser() != null
                && session.getUser().getId().equals(credential.getUser().getId());
        boolean isAssignedExpert = session.getExpert() != null
                && session.getExpert().getUser() != null
                && session.getExpert()
                        .getUser()
                        .getId()
                        .equals(credential.getUser().getId());

        if (!isSessionOwner && !isAssignedExpert) {
            throw new AppException(ErrorCode.UNAUTHORIZED);
        }

        var eval = evaluationRepository
                .findByScoringSession_Id(sessionId)
                .orElseThrow(() -> new AppException(ErrorCode.SCORING_SESSION_NOT_FOUND));

        java.util.Map<String, Object> result = new java.util.LinkedHashMap<>();
        result.put("id", eval.getId());
        result.put("skill", eval.getSkill());
        result.put("overallScore", eval.getOverallScore());
        result.put("fluency", eval.getFluency());
        result.put("vocabulary", eval.getVocabulary());
        result.put("grammar", eval.getGrammar());
        result.put("pronunciation", eval.getPronunciation());
        result.put("taskResponse", eval.getTaskResponse());
        result.put("coherence", eval.getCoherence());
        result.put("lexicalResource", eval.getLexicalResource());
        result.put("grammaticalRange", eval.getGrammaticalRange());
        result.put("feedback", eval.getFeedback());
        result.put("strengths", eval.getStrengths());
        result.put("areasForImprovement", eval.getAreasForImprovement());
        result.put(
                "expertName",
                eval.getExpertProfile() != null ? eval.getExpertProfile().getDisplayName() : null);
        result.put("createdAt", eval.getCreatedAt());
        return result;
    }

    private ScoringSessionResponse toResponse(ScoringSession s) {
        LocalDateTime submittedAt = null;
        if (s.getWritingSubmissionId() != null) {
            submittedAt = writingSubmissionRepository
                    .findById(s.getWritingSubmissionId())
                    .map(WritingSubmission::getSubmittedAt)
                    .orElse(null);
        }

        String testTitle = null;
        String stimulusTitle = null;

        // Get test title if available
        if (s.getTestId() != null) {
            Test test = testRepository.findById(s.getTestId()).orElse(null);
            if (test != null) {
                testTitle = test.getTitle();
            }
        }

        return ScoringSessionResponse.builder()
                .id(s.getId())
                .expertId(s.getExpert() != null ? s.getExpert().getId() : null)
                .expertDisplayName(s.getExpert() != null ? s.getExpert().getDisplayName() : null)
                .userDisplayName(s.getUser() != null ? s.getUser().getFull_name() : null)
                .skill(s.getSkill())
                .status(s.getStatus())
                .roomUrl(s.getRoomUrl())
                .roomName(s.getRoomName())
                .queuePosition(s.getQueuePosition())
                .createdAt(s.getCreatedAt())
                .startedAt(s.getStartedAt())
                .endedAt(s.getEndedAt())
                .testId(s.getTestId())
                .testTitle(testTitle)
                .stimulusTitle(stimulusTitle)
                .writingSubmissionId(s.getWritingSubmissionId())
                .submittedAt(submittedAt)
                .content(s.getContent())
                .recordingUrl(s.getRecordingUrl())
                .expertRecordingUrl(s.getExpertRecordingUrl())
                .userRating(s.getUserRating())
                .userComment(s.getUserComment())
                .build();
    }

    @Override
    public List<ScoringSessionResponse> getAllSessions() {
        return sessionRepository.findAll(Sort.by(Sort.Direction.DESC, "id")).stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }
}
