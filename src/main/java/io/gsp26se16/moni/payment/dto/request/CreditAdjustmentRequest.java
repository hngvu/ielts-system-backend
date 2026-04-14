package io.gsp26se16.moni.payment.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record CreditAdjustmentRequest(@NotNull Integer amount, @NotBlank String reason) {}
