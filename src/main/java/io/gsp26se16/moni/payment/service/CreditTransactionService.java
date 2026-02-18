package io.gsp26se16.moni.payment.service;

import io.gsp26se16.moni.payment.dto.response.CreditTransactionResponse;

import java.time.LocalDateTime;
import java.util.List;

public interface CreditTransactionService {
    List<CreditTransactionResponse> searchCreditTransactions(Integer userId, String paymentType, LocalDateTime startDate, LocalDateTime endDate);
    CreditTransactionResponse getCreditTransactionDetail(Integer id);
}
