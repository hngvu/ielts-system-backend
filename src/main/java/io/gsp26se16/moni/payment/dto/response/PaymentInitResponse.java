package io.gsp26se16.moni.payment.dto.response;

import java.time.LocalDateTime;

import lombok.Builder;

@Builder
public record PaymentInitResponse(
        Integer id, String txnCode, Integer amount, String qrCodeUrl, LocalDateTime expiredAt) {}
