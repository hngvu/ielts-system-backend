package io.gsp26se16.moni.payment.dto.response;

import lombok.Builder;

import java.time.LocalDateTime;

@Builder
public record PaymentResponse(
    Integer id,
    Integer packageId,
    String txnCode,
    Integer amount,
    LocalDateTime updatedAt,
    String status
) {
}
