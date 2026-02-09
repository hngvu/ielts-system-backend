package io.gsp26se16.moni.payment.dto.response;

import lombok.Builder;

import java.time.LocalDateTime;

@Builder
public record PaymentInitResponse(
    Integer id,
    String txnCode,
    Integer amount,
    String qrCodeUrl,
    LocalDateTime expiredAt
) {
}
