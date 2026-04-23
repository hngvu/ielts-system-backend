package io.gsp26se16.moni.payment.dto.request;

public record PackagePricingUpdateRequest(
        String name,
        String category,
        Integer price,
        Integer creditAmount,
        Integer quotaAi,
        Integer quotaExpert,
        Boolean isActive) {}
