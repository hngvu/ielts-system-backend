package io.gsp26se16.moni.payment.dto.response;

public record SubscriptionPlanResponse(
        Integer id,
        String code,
        String name,
        String description,
        int priceVnd,
        int durationDays,
        int quotaAi,
        int quotaExpert,
        boolean isActive) {}
