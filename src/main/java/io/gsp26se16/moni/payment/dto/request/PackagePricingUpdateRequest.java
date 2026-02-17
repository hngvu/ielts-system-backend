package io.gsp26se16.moni.payment.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record PackagePricingUpdateRequest(
    @NotBlank(message = "Name is required") String name,
    
    @NotNull(message = "Price is required")
    @Positive(message = "Price must be positive") Integer price,
    
    @NotNull(message = "Credit amount is required")
    @Positive(message = "Credit amount must be positive") Integer creditAmount,
    
    Boolean isActive
) {}
