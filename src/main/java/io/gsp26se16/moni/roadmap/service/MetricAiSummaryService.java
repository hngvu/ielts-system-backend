package io.gsp26se16.moni.roadmap.service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import io.gsp26se16.moni.ai.writing.service.PromptLoader;
import io.gsp26se16.moni.common.exception.AppException;
import io.gsp26se16.moni.common.exception.ErrorCode;
import io.gsp26se16.moni.roadmap.dto.response.LearnerRoadmapInsightsResponse;
import io.gsp26se16.moni.roadmap.dto.response.LearnerRoadmapInsightsResponse.TagMetricResponse;
import io.gsp26se16.moni.roadmap.dto.response.MetricSummaryResponse;
import io.gsp26se16.moni.roadmap.entity.MetricAiSummaryCache;
import io.gsp26se16.moni.roadmap.repository.MetricAiSummaryCacheRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class MetricAiSummaryService {

    private final ChatClient.Builder chatClientBuilder;
    private final GoalService goalService;
    private final PromptLoader promptLoader;
    private final MetricAiSummaryCacheRepository metricAiSummaryCacheRepository;

    public MetricSummaryResponse generateMetricSummary() {
        String userId = getCurrentUserId();
        LearnerRoadmapInsightsResponse insights = goalService.getRoadmapInsights();

        Optional<MetricAiSummaryCache> cacheOpt = metricAiSummaryCacheRepository.findById(userId);
        LocalDateTime lastMetricUpdate = insights.getLastMetricUpdatedAt();

        if (cacheOpt.isPresent()) {
            MetricAiSummaryCache cache = cacheOpt.get();
            boolean isCacheValid = false;
            if (lastMetricUpdate != null) {
                // Buffer by 1 minute to avoid edge cases where metric is updated in the same transaction
                isCacheValid = cache.getGeneratedAt().plusMinutes(1).isAfter(lastMetricUpdate)
                        || cache.getGeneratedAt().isEqual(lastMetricUpdate);
            } else {
                isCacheValid =
                        cache.getGeneratedAt().isAfter(LocalDateTime.now().minusDays(7));
            }

            if (isCacheValid) {
                log.info("Returning cached AI summary for user {}", userId);
                return MetricSummaryResponse.builder()
                        .summary(cache.getSummary())
                        .generatedAt(cache.getGeneratedAt())
                        .build();
            }
        }

        String prompt = buildPrompt(insights);

        try {
            ChatClient chatClient = chatClientBuilder.build();
            String response = chatClient.prompt().user(prompt).call().content();
            log.info("Metric AI summary generated successfully for user {}", userId);

            if (response == null || response.isBlank()) {
                log.error("AI returned empty response for metric summary");
                return fallbackResponse();
            }

            MetricAiSummaryCache newCache = MetricAiSummaryCache.builder()
                    .userId(userId)
                    .summary(response.trim())
                    .generatedAt(LocalDateTime.now())
                    .build();
            metricAiSummaryCacheRepository.save(newCache);

            return MetricSummaryResponse.builder()
                    .summary(response.trim())
                    .generatedAt(newCache.getGeneratedAt())
                    .build();
        } catch (Exception e) {
            log.error("AI metric summary call failed", e);
            return fallbackResponse();
        }
    }

    private MetricSummaryResponse fallbackResponse() {
        return MetricSummaryResponse.builder()
                .summary("Không thể tạo phân tích AI lúc này. Vui lòng thử lại sau.")
                .generatedAt(LocalDateTime.now())
                .build();
    }

    private String buildPrompt(LearnerRoadmapInsightsResponse insights) {
        String learnerData = buildLearnerData(insights);
        return promptLoader.loadPrompt("roadmap/metric_summary.txt", Map.of("LEARNER_DATA", learnerData));
    }

    private String buildLearnerData(LearnerRoadmapInsightsResponse insights) {
        StringBuilder sb = new StringBuilder();

        // Calibrated bands
        sb.append(String.format(
                "- Band hiệu chỉnh: Overall %.1f | Reading %.1f | Listening %.1f | Writing %.1f | Speaking %.1f\n",
                safe(insights.getCalibratedOverall()),
                safe(insights.getCalibratedReading()),
                safe(insights.getCalibratedListening()),
                safe(insights.getCalibratedWriting()),
                safe(insights.getCalibratedSpeaking())));

        // Target bands
        sb.append(String.format(
                "- Mục tiêu: Overall %.1f | Reading %.1f | Listening %.1f | Writing %.1f | Speaking %.1f\n",
                safe(insights.getTargetOverall()),
                safe(insights.getTargetReading()),
                safe(insights.getTargetListening()),
                safe(insights.getTargetWriting()),
                safe(insights.getTargetSpeaking())));

        // Indices
        sb.append(String.format("- Chỉ số Mastery (mức thành thạo): %.0f%%\n", safe(insights.getMasteryIndex()) * 100));
        sb.append(
                String.format("- Chỉ số Confidence (độ tự tin): %.0f%%\n", safe(insights.getConfidenceIndex()) * 100));

        // Days to exam
        if (insights.getDaysToExam() != null) {
            sb.append(String.format("- Số ngày còn lại đến kỳ thi: %d ngày\n", insights.getDaysToExam()));
        }

        // Weak tags
        List<TagMetricResponse> weakTags = insights.getWeakestTags();
        if (weakTags != null && !weakTags.isEmpty()) {
            sb.append("\n### Các dạng bài/kỹ năng YẾU (mastery < 50%):\n");
            sb.append(weakTags.stream()
                    .limit(8)
                    .map(t -> String.format(
                            "- %s (%s): mastery %.0f%%, confidence %.0f%%, %d lần luyện",
                            t.getTagName(),
                            t.getSkill(),
                            safe(t.getMasteryLevel()) * 100,
                            safe(t.getConfidenceScore()) * 100,
                            t.getAttemptCount() != null ? t.getAttemptCount() : 0))
                    .collect(Collectors.joining("\n")));
            sb.append("\n");
        }

        // Strong tags
        List<TagMetricResponse> strongTags = insights.getStrongestTags();
        if (strongTags != null && !strongTags.isEmpty()) {
            sb.append("\n### Các dạng bài/kỹ năng MẠNH (mastery >= 50%):\n");
            sb.append(strongTags.stream()
                    .limit(5)
                    .map(t -> String.format(
                            "- %s (%s): mastery %.0f%%, confidence %.0f%%",
                            t.getTagName(),
                            t.getSkill(),
                            safe(t.getMasteryLevel()) * 100,
                            safe(t.getConfidenceScore()) * 100))
                    .collect(Collectors.joining("\n")));
            sb.append("\n");
        }

        // Warning
        if (insights.getTargetWarning() != null) {
            sb.append("\n### Cảnh báo hệ thống:\n");
            sb.append(insights.getTargetWarning());
            sb.append("\n");
        }

        return sb.toString();
    }

    private double safe(Double val) {
        return val != null ? val : 0.0;
    }

    private String getCurrentUserId() {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new AppException(ErrorCode.UNAUTHENTICATED);
        }
        if (authentication.getPrincipal() instanceof org.springframework.security.oauth2.jwt.Jwt jwt) {
            return jwt.getClaimAsString("userId");
        }
        throw new AppException(ErrorCode.UNAUTHENTICATED);
    }
}
