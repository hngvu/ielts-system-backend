package io.gsp26se16.moni.payment.dto.request;

import java.time.LocalDateTime;

public record SePayWebhookRequest(
    Long id,
    String gateway,
    LocalDateTime transactionDate,
    String accountNumber,
    String code,
    String content,
    Long transferAmount,
    Long accumulated,
    String subAccount,
    String referenceCode,
    String description
) {
}
