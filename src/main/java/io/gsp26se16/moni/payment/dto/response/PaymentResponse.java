package io.gsp26se16.moni.payment.dto.response;

import java.time.LocalDateTime;

import lombok.Builder;

@Builder
public record PaymentResponse(
        Integer id, Integer packageId, String txnCode, Integer amount, LocalDateTime updatedAt, String status) {}
