package io.gsp26se16.moni.payment.service.impl;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

import jakarta.persistence.criteria.Predicate;

import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

import io.gsp26se16.moni.common.exception.AppException;
import io.gsp26se16.moni.common.exception.ErrorCode;
import io.gsp26se16.moni.payment.dto.response.CreditTransactionResponse;
import io.gsp26se16.moni.payment.entity.CreditTransaction;
import io.gsp26se16.moni.payment.enumeration.PaymentType;
import io.gsp26se16.moni.payment.repository.CreditTransactionRepository;
import io.gsp26se16.moni.payment.service.CreditTransactionService;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class CreditTransactionServiceImpl implements CreditTransactionService {
    private final CreditTransactionRepository creditTransactionRepository;

    @Override
    public List<CreditTransactionResponse> searchCreditTransactions(
            String userId, String paymentType, LocalDateTime startDate, LocalDateTime endDate) {
        Specification<CreditTransaction> spec = (root, query, criteriaBuilder) -> {
            Predicate predicate = criteriaBuilder.conjunction();

            if (userId != null) {
                predicate = criteriaBuilder.and(
                        predicate, criteriaBuilder.equal(root.get("user").get("id"), userId));
            }

            if (paymentType != null) {
                try {
                    PaymentType type = PaymentType.valueOf(paymentType.toUpperCase());
                    predicate = criteriaBuilder.and(predicate, criteriaBuilder.equal(root.get("paymentType"), type));
                } catch (IllegalArgumentException e) {
                    // Invalid payment type, return empty list
                    return criteriaBuilder.disjunction();
                }
            }

            if (startDate != null) {
                predicate = criteriaBuilder.and(
                        predicate, criteriaBuilder.greaterThanOrEqualTo(root.get("createdAt"), startDate));
            }

            if (endDate != null) {
                predicate = criteriaBuilder.and(
                        predicate, criteriaBuilder.lessThanOrEqualTo(root.get("createdAt"), endDate));
            }

            return predicate;
        };

        List<CreditTransaction> creditTransactions = creditTransactionRepository.findAll(spec);

        return creditTransactions.stream()
                .map(this::mapToCreditTransactionResponse)
                .collect(Collectors.toList());
    }

    @Override
    public CreditTransactionResponse getCreditTransactionDetail(Integer id) {
        CreditTransaction creditTransaction = creditTransactionRepository
                .findById(id)
                .orElseThrow(() -> new AppException(ErrorCode.CREDIT_TRANSACTION_NOT_FOUND));

        return mapToCreditTransactionResponse(creditTransaction);
    }

    private CreditTransactionResponse mapToCreditTransactionResponse(CreditTransaction creditTransaction) {
        return CreditTransactionResponse.builder()
                .id(creditTransaction.getId())
                .delta(creditTransaction.getDelta())
                .balanceBefore(creditTransaction.getBalanceBefore())
                .balanceAfter(creditTransaction.getBalanceAfter())
                .paymentType(creditTransaction.getPaymentType().toString())
                .serviceName(
                        creditTransaction.getServicePricing() != null
                                ? creditTransaction.getServicePricing().getName()
                                : null)
                .createdAt(creditTransaction.getCreatedAt())
                .userId(
                        creditTransaction.getUser() != null
                                ? creditTransaction.getUser().getId()
                                : null)
                .serviceId(
                        creditTransaction.getServicePricing() != null
                                ? creditTransaction.getServicePricing().getId()
                                : null)
                .paymentId(
                        creditTransaction.getPayment() != null
                                ? creditTransaction.getPayment().getId()
                                : null)
                .build();
    }
}
