package io.gsp26se16.moni.payment.dto.response;

import java.time.LocalDateTime;

import lombok.Builder;

@Builder
public record CreditTransactionResponse(
        Integer id,
        Integer delta,
        Integer balanceBefore,
        Integer balanceAfter,
        String paymentType,
        String serviceName,
        String packageName,
        LocalDateTime createdAt,
        String userId,
        Integer serviceId,
        Integer paymentId) {}
