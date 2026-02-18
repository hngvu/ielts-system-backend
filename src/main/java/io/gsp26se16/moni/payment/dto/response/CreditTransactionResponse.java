package io.gsp26se16.moni.payment.dto.response;

import lombok.Builder;

import java.time.LocalDateTime;

@Builder
public record CreditTransactionResponse(
    Integer id,
    Integer delta,
    Integer balanceBefore,
    Integer balanceAfter,
    String paymentType,
    LocalDateTime createdAt,
    Integer userId,
    Integer serviceId,
    Integer paymentId
) {
}
