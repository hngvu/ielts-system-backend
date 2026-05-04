package io.gsp26se16.moni.payment.dto.response;

import java.time.LocalDateTime;

import lombok.Builder;

@Builder
public record PaymentResponse(
        Integer id,
        Integer packageId,
        String packageName,
        Integer subscriptionPlanId,
        String subscriptionPlanName,
        String txnCode,
        Integer amount,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        String status,
        String userId,
        String userEmail,
        String userFullName,
        LocalDateTime reviewedAt,
        String reviewedBy) {}
