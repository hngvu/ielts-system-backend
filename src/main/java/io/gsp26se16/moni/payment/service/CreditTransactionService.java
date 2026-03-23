package io.gsp26se16.moni.payment.service;

import java.time.LocalDateTime;
import java.util.List;

import io.gsp26se16.moni.payment.dto.response.CreditTransactionResponse;

public interface CreditTransactionService {
    List<CreditTransactionResponse> searchCreditTransactions(
            String userId, String paymentType, LocalDateTime startDate, LocalDateTime endDate);

    CreditTransactionResponse getCreditTransactionDetail(Integer id);
}
