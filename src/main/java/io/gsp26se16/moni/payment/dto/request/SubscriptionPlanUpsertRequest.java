package io.gsp26se16.moni.payment.dto.request;

/**
 * Admin upsert subscription plan. Dùng chung cho create + update.
 * quotaAi = -1 nghĩa là unlimited (cap mềm ở service layer).
 */
public record SubscriptionPlanUpsertRequest(
        String code,
        String name,
        String description,
        Integer priceVnd,
        Integer durationDays,
        Integer quotaAi,
        Integer quotaExpert,
        Boolean isActive) {}
