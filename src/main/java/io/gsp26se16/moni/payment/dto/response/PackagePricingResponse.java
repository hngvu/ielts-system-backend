package io.gsp26se16.moni.payment.dto.response;

public record PackagePricingResponse(
        Integer id, String name, String category, Integer price, Integer creditAmount, Boolean isActive) {}
