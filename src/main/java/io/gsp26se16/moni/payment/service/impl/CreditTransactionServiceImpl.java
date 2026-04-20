package io.gsp26se16.moni.payment.service.impl;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

import jakarta.persistence.criteria.Predicate;

import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.transaction.annotation.Transactional;

import io.gsp26se16.moni.authentication.entity.Users;
import io.gsp26se16.moni.authentication.repository.UsersRepository;
import io.gsp26se16.moni.common.exception.AppException;
import io.gsp26se16.moni.common.exception.ErrorCode;
import io.gsp26se16.moni.payment.dto.request.CreditAdjustmentRequest;
import io.gsp26se16.moni.payment.dto.response.CreditTransactionResponse;
import io.gsp26se16.moni.payment.entity.CreditTransaction;
import io.gsp26se16.moni.payment.enumeration.PaymentType;
import io.gsp26se16.moni.payment.repository.CreditTransactionRepository;
import io.gsp26se16.moni.payment.service.CreditTransactionService;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class CreditTransactionServiceImpl implements CreditTransactionService {
    private final CreditTransactionRepository creditTransactionRepository;
    private final UsersRepository usersRepository;

    @Override
    public List<CreditTransactionResponse> searchCreditTransactions(
            String userId, String paymentType, LocalDateTime startDate, LocalDateTime endDate) {
        Specification<CreditTransaction> spec = (root, query, criteriaBuilder) -> {
            Predicate predicate = criteriaBuilder.conjunction();

            if (userId != null && !userId.isBlank()) {
                String rawKeyword = userId.trim();
                String keyword = "%" + rawKeyword.toLowerCase() + "%";
                Predicate byUserId = criteriaBuilder.like(
                        criteriaBuilder.lower(root.get("user").get("id")), keyword);
                Predicate byEmail = criteriaBuilder.like(
                        criteriaBuilder.lower(root.get("user").get("credential").get("email")), keyword);
                Predicate byFullName = criteriaBuilder.like(
                        criteriaBuilder.lower(root.get("user").get("full_name")), keyword);
                predicate = criteriaBuilder.and(predicate, criteriaBuilder.or(byUserId, byEmail, byFullName));
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

        List<CreditTransaction> creditTransactions =
                creditTransactionRepository.findAll(spec, Sort.by(Sort.Direction.DESC, "createdAt"));

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

    @Override
    @Transactional
    public CreditTransactionResponse adjustCredit(String userId, CreditAdjustmentRequest request) {
        Users user = usersRepository.findById(userId).orElseThrow(() -> new AppException(ErrorCode.USER_NOT_EXISTED));

        int currentBalance = user.getCredit() != null ? user.getCredit().intValue() : 0;
        int adjustmentAmount = request.amount();
        int newBalance = currentBalance + adjustmentAmount;

        user.setCredit((double) newBalance);
        usersRepository.save(user);

        CreditTransaction creditTransaction = CreditTransaction.builder()
                .delta(adjustmentAmount)
                .balanceBefore(currentBalance)
                .balanceAfter(newBalance)
                .paymentType(PaymentType.CREDIT_ADJUSTMENT)
                .createdAt(LocalDateTime.now())
                .user(user)
                .remark("Admin adjustment: " + request.reason())
                .build();

        creditTransactionRepository.save(creditTransaction);

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
                .packageName(
                        creditTransaction.getPayment() != null
                                        && creditTransaction.getPayment().getPackagePricing() != null
                                ? creditTransaction
                                        .getPayment()
                                        .getPackagePricing()
                                        .getName()
                                : null)
                .createdAt(creditTransaction.getCreatedAt())
                .userId(
                        creditTransaction.getUser() != null
                                ? creditTransaction.getUser().getId()
                                : null)
                .userEmail(
                        creditTransaction.getUser() != null
                                        && creditTransaction.getUser().getCredential() != null
                                ? creditTransaction.getUser().getCredential().getEmail()
                                : null)
                .userFullName(
                        creditTransaction.getUser() != null
                                ? creditTransaction.getUser().getFull_name()
                                : null)
                .serviceId(
                        creditTransaction.getServicePricing() != null
                                ? creditTransaction.getServicePricing().getId()
                                : null)
                .paymentId(
                        creditTransaction.getPayment() != null
                                ? creditTransaction.getPayment().getId()
                                : null)
                .remark(creditTransaction.getRemark())
                .quotaType(creditTransaction.getQuotaType())
                .quotaBefore(creditTransaction.getQuotaBefore())
                .quotaAfter(creditTransaction.getQuotaAfter())
                .build();
    }
}
