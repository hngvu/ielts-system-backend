package io.gsp26se16.moni.payment.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record ServicePricingCreateRequest(
    @NotBlank(message = "Service code is required") String serviceCode,
    
    @NotBlank(message = "Name is required") String name,
    
    String description,
    
    @NotNull(message = "Credit cost is required")
    @Positive(message = "Credit cost must be positive") Integer creditCost
) {}
