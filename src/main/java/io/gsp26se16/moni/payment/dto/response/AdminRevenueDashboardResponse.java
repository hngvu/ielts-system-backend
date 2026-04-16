package io.gsp26se16.moni.payment.dto.response;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public record AdminRevenueDashboardResponse(
        LocalDateTime startDate,
        LocalDateTime endDate,
        long topupRevenue,
        long topupCount,
        long expertWritingCredits,
        long expertWritingJobs,
        long expertSpeakingCredits,
        long expertSpeakingJobs,
        long totalExpertCredits,
        long totalExpertJobs,

        // AI workload metrics
        long aiWritingJobs,
        long aiSpeakingJobs,
        long totalAiJobs,
        long totalAiCredits,

        // Operation metrics
        long totalTests,
        long totalUsers,
        long newUsers,
        List<DailyRevenue> dailyRevenue,
        List<DailyExpertJobs> dailyExpertJobs) {

    public record DailyRevenue(LocalDate date, long amount) {}

    public record DailyExpertJobs(
            LocalDate date, long writingJobs, long speakingJobs, long aiWritingJobs, long aiSpeakingJobs) {}
}
