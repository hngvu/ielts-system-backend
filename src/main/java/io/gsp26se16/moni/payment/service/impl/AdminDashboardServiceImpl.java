package io.gsp26se16.moni.payment.service.impl;

import java.time.LocalDateTime;

import org.springframework.stereotype.Service;

import io.gsp26se16.moni.payment.dto.response.AdminRevenueDashboardResponse;
import io.gsp26se16.moni.payment.enumeration.PaymentStatus;
import io.gsp26se16.moni.payment.enumeration.PaymentType;
import io.gsp26se16.moni.payment.repository.CreditTransactionRepository;
import io.gsp26se16.moni.payment.repository.PaymentRepository;
import io.gsp26se16.moni.payment.service.AdminDashboardService;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class AdminDashboardServiceImpl implements AdminDashboardService {

    static final String EXPERT_WRITING_SERVICE_CODE = "EXPERT_WRITING_SCORE";
    static final String EXPERT_SPEAKING_SERVICE_CODE = "EXPERT_SPEAKING_SCORE";

    PaymentRepository paymentRepository;
    CreditTransactionRepository creditTransactionRepository;

    @Override
    public AdminRevenueDashboardResponse getRevenueDashboard(LocalDateTime startDate, LocalDateTime endDate) {
        LocalDateTime safeStart =
                startDate != null ? startDate : LocalDateTime.now().minusDays(30);
        LocalDateTime safeEnd = endDate != null ? endDate : LocalDateTime.now();

        long topupRevenue =
                paymentRepository.sumAmountByStatusAndCreatedAtBetween(PaymentStatus.SUCCESS, safeStart, safeEnd);
        long topupCount = paymentRepository.countByStatusAndCreatedAtBetween(PaymentStatus.SUCCESS, safeStart, safeEnd);

        long expertWritingCredits = creditTransactionRepository.sumConsumedCreditsByServiceCodeAndCreatedAtBetween(
                PaymentType.CONSUME, EXPERT_WRITING_SERVICE_CODE, safeStart, safeEnd);
        long expertWritingJobs = creditTransactionRepository.countByServiceCodeAndPaymentTypeAndCreatedAtBetween(
                PaymentType.CONSUME, EXPERT_WRITING_SERVICE_CODE, safeStart, safeEnd);

        long expertSpeakingCredits = creditTransactionRepository.sumConsumedCreditsByServiceCodeAndCreatedAtBetween(
                PaymentType.CONSUME, EXPERT_SPEAKING_SERVICE_CODE, safeStart, safeEnd);
        long expertSpeakingJobs = creditTransactionRepository.countByServiceCodeAndPaymentTypeAndCreatedAtBetween(
                PaymentType.CONSUME, EXPERT_SPEAKING_SERVICE_CODE, safeStart, safeEnd);

        return new AdminRevenueDashboardResponse(
                safeStart,
                safeEnd,
                topupRevenue,
                topupCount,
                expertWritingCredits,
                expertWritingJobs,
                expertSpeakingCredits,
                expertSpeakingJobs,
                expertWritingCredits + expertSpeakingCredits,
                expertWritingJobs + expertSpeakingJobs);
    }
}
