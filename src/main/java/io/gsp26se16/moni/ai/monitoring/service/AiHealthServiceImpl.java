package io.gsp26se16.moni.ai.monitoring.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import io.gsp26se16.moni.ai.monitoring.dto.AiHealthResponse;
import io.gsp26se16.moni.ai.speaking.repository.SpeakingSubmissionRepository;
import io.gsp26se16.moni.ai.writing.repository.AiEvaluationRepository;
import io.gsp26se16.moni.ai.writing.repository.WritingSubmissionRepository;
import io.gsp26se16.moni.common.enumeration.EvaluationStatus;
import io.gsp26se16.moni.common.enumeration.Skill;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class AiHealthServiceImpl implements AiHealthService {

    private static final ZoneId ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

    private final WritingSubmissionRepository writingSubmissionRepository;
    private final SpeakingSubmissionRepository speakingSubmissionRepository;
    private final AiEvaluationRepository aiEvaluationRepository;

    @Value("${app.ai-health.failed-threshold:5}")
    private int failedThreshold;

    @Override
    public AiHealthResponse getAiHealthDashboard() {
        // ── Tổng số bài AI (all time) ────────────────────────────────────
        long writingEvaluations = aiEvaluationRepository.countBySkill(Skill.WRITING);
        long speakingEvaluations = aiEvaluationRepository.countBySkill(Skill.SPEAKING);
        long totalAiEvaluations = writingEvaluations + speakingEvaluations;

        // ── Time boundaries ──────────────────────────────────────────────
        LocalDate today = LocalDate.now(ZONE);
        LocalDateTime todayStart = today.atStartOfDay();
        LocalDateTime todayEnd = today.plusDays(1).atStartOfDay();
        LocalDateTime yesterdayStart = today.minusDays(1).atStartOfDay();
        LocalDateTime yesterdayEnd = todayStart;

        // ── Error rate hôm nay ───────────────────────────────────────────
        long failedWritingToday = writingSubmissionRepository.countByEvaluationStatusAndSubmittedAtBetween(
                EvaluationStatus.FAILED, todayStart, todayEnd);
        long failedSpeakingToday = speakingSubmissionRepository.countByEvaluationStatusAndSubmittedAtBetween(
                EvaluationStatus.FAILED, todayStart, todayEnd);
        long failedToday = failedWritingToday + failedSpeakingToday;

        long totalWritingToday = writingSubmissionRepository.countBySubmittedAtBetween(todayStart, todayEnd);
        long totalSpeakingToday = speakingSubmissionRepository.countBySubmittedAtBetween(todayStart, todayEnd);
        long totalToday = totalWritingToday + totalSpeakingToday;

        double errorRateToday = totalToday == 0 ? 0 : (failedToday * 100.0 / totalToday);

        // ── Error rate hôm qua ───────────────────────────────────────────
        long failedWritingYesterday = writingSubmissionRepository.countByEvaluationStatusAndSubmittedAtBetween(
                EvaluationStatus.FAILED, yesterdayStart, yesterdayEnd);
        long failedSpeakingYesterday = speakingSubmissionRepository.countByEvaluationStatusAndSubmittedAtBetween(
                EvaluationStatus.FAILED, yesterdayStart, yesterdayEnd);
        long failedYesterday = failedWritingYesterday + failedSpeakingYesterday;

        long totalWritingYesterday =
                writingSubmissionRepository.countBySubmittedAtBetween(yesterdayStart, yesterdayEnd);
        long totalSpeakingYesterday =
                speakingSubmissionRepository.countBySubmittedAtBetween(yesterdayStart, yesterdayEnd);
        long totalYesterday = totalWritingYesterday + totalSpeakingYesterday;

        double errorRateYesterday = totalYesterday == 0 ? 0 : (failedYesterday * 100.0 / totalYesterday);
        double errorRateChange = errorRateToday - errorRateYesterday;

        // ── Latency trung bình (hôm nay) ────────────────────────────────
        Double avgWritingLatency = aiEvaluationRepository.avgWritingLatencySeconds(todayStart, todayEnd);
        Double avgSpeakingLatency = aiEvaluationRepository.avgSpeakingLatencySeconds(todayStart, todayEnd);
        Double avgLatency = computeAvgLatency(avgWritingLatency, avgSpeakingLatency);

        // ── Alert status ─────────────────────────────────────────────────
        boolean alertActive = failedToday >= failedThreshold;
        String alertMessage = alertActive
                ? "Cảnh báo: " + failedToday + " bài AI thất bại hôm nay (ngưỡng: " + failedThreshold + ")"
                : null;

        return new AiHealthResponse(
                totalAiEvaluations,
                writingEvaluations,
                speakingEvaluations,
                failedToday,
                totalToday,
                Math.round(errorRateToday * 100.0) / 100.0,
                failedYesterday,
                totalYesterday,
                Math.round(errorRateYesterday * 100.0) / 100.0,
                Math.round(errorRateChange * 100.0) / 100.0,
                avgLatency,
                avgWritingLatency,
                avgSpeakingLatency,
                alertActive,
                alertMessage);
    }

    private Double computeAvgLatency(Double writing, Double speaking) {
        if (writing == null && speaking == null) return null;
        if (writing == null) return Math.round(speaking * 100.0) / 100.0;
        if (speaking == null) return Math.round(writing * 100.0) / 100.0;
        double avg = (writing + speaking) / 2.0;
        return Math.round(avg * 100.0) / 100.0;
    }
}
