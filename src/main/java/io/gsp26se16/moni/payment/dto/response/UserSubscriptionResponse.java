package io.gsp26se16.moni.payment.dto.response;

import java.time.LocalDateTime;

public record UserSubscriptionResponse(
        Integer id,
        Integer planId,
        String planCode,
        String planName,
        LocalDateTime startAt,
        LocalDateTime endAt,
        int remainAi,
        int remainExpert,
        int usedAi,
        int usedExpert,
        boolean isActive) {}
