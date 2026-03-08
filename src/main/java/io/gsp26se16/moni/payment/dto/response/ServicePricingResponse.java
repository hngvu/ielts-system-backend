package io.gsp26se16.moni.payment.dto.response;

public record ServicePricingResponse(
        Integer id, String serviceCode, String name, String description, Integer creditCost) {}
