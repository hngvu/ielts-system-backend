package io.gsp26se16.moni.payment.dto.response;

import java.time.LocalDateTime;

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
        long totalExpertJobs) {}
