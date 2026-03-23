package io.gsp26se16.moni.payment.dto.request;

import java.time.LocalDateTime;

import com.fasterxml.jackson.annotation.JsonFormat;

public record SePayWebhookRequest(
        Long id,
        String gateway,
        @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime transactionDate,
        String accountNumber,
        String code,
        String content,
        Long transferAmount,
        Long accumulated,
        String subAccount,
        String referenceCode,
        String description) {}
