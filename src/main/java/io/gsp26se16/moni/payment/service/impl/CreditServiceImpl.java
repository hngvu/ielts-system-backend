package io.gsp26se16.moni.payment.service.impl;

import java.time.LocalDate;
import java.time.LocalDateTime;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.gsp26se16.moni.authentication.entity.Users;
import io.gsp26se16.moni.authentication.repository.UserCredentialsRepository;
import io.gsp26se16.moni.authentication.repository.UsersRepository;
import io.gsp26se16.moni.common.exception.AppException;
import io.gsp26se16.moni.common.exception.ErrorCode;
import io.gsp26se16.moni.payment.entity.CreditTransaction;
import io.gsp26se16.moni.payment.entity.ServicePricing;
import io.gsp26se16.moni.payment.entity.UserSubscription;
import io.gsp26se16.moni.payment.enumeration.PaymentType;
import io.gsp26se16.moni.payment.repository.CreditTransactionRepository;
import io.gsp26se16.moni.payment.repository.ServicePricingRepository;
import io.gsp26se16.moni.payment.repository.UserSubscriptionRepository;
import io.gsp26se16.moni.payment.service.CreditService;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class CreditServiceImpl implements CreditService {

    // Soft cap cho plan unlimited AI (quotaAi=-1) — chống abuse
    static final int UNLIMITED_AI_SOFT_CAP = 500;

    UserCredentialsRepository userCredentialsRepository;
    UsersRepository usersRepository;
    ServicePricingRepository servicePricingRepository;
    CreditTransactionRepository creditTransactionRepository;
    UserSubscriptionRepository userSubscriptionRepository;

    @Override
    @Transactional
    public void checkAndDeduct(String credentialId, String serviceCode) {
        var credential = userCredentialsRepository
                .findById(credentialId)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_EXISTED));

        Users user = credential.getUser();

        ServicePricing pricing = servicePricingRepository
                .findByServiceCode(serviceCode)
                .orElseThrow(() -> new AppException(ErrorCode.INVALID_KEY));

        int creditCost = pricing.getCreditCost();
        boolean isAi = "AI_SPEAKING_SCORE".equals(serviceCode) || "AI_WRITING_SCORE".equals(serviceCode);
        boolean isExpert = "EXPERT_WRITING_SCORE".equals(serviceCode) || "EXPERT_SPEAKING_SCORE".equals(serviceCode);

        if (isAi) {
            LocalDateTime startOfDay = LocalDate.now().atStartOfDay();
            LocalDateTime endOfDay = startOfDay.plusDays(1).minusNanos(1);
            long usedToday = creditTransactionRepository.countByUserIdAndServiceCodeAndPaymentTypeAndCreatedAtBetween(
                    user.getId(), PaymentType.CONSUME, serviceCode, startOfDay, endOfDay);
            if (usedToday == 0) {
                creditCost = 0; // Free turn
            }
        }

        // Quota-first: ưu tiên trừ quota sub active. Hết quota → fallback ví VND.
        // Không tạo CreditTransaction khi trừ từ sub vì balance VND không đổi.
        if (creditCost > 0) {
            var activeSubOpt =
                    userSubscriptionRepository.findFirstByUser_IdAndIsActiveTrueAndEndAtAfterOrderByEndAtDesc(
                            user.getId(), LocalDateTime.now());
            if (activeSubOpt.isPresent()) {
                UserSubscription sub = activeSubOpt.get();
                if (isAi && canDeductAiFromSub(sub)) {
                    deductAiFromSub(sub);
                    return;
                }
                if (isExpert && sub.getRemainExpert() > 0) {
                    sub.setRemainExpert(sub.getRemainExpert() - 1);
                    sub.setUsedExpert(sub.getUsedExpert() + 1);
                    userSubscriptionRepository.save(sub);
                    return;
                }
            }
        }

        int balanceBefore = user.getCredit() != null ? user.getCredit().intValue() : 0;

        if (balanceBefore < creditCost) {
            throw new AppException(ErrorCode.INSUFFICIENT_CREDIT);
        }

        int balanceAfter = balanceBefore - creditCost;
        user.setCredit((double) balanceAfter);
        usersRepository.save(user);

        CreditTransaction tx = CreditTransaction.builder()
                .delta(-creditCost)
                .balanceBefore(balanceBefore)
                .balanceAfter(balanceAfter)
                .paymentType(PaymentType.CONSUME)
                .createdAt(LocalDateTime.now())
                .user(user)
                .servicePricing(pricing)
                .build();

        creditTransactionRepository.save(tx);
    }

    /** AI deductible từ sub: nếu unlimited (quotaAi=-1) và chưa vượt soft cap, HOẶC remainAi>0. */
    private boolean canDeductAiFromSub(UserSubscription sub) {
        if (sub.getPlan().getQuotaAi() == -1) {
            return sub.getUsedAi() < UNLIMITED_AI_SOFT_CAP;
        }
        return sub.getRemainAi() > 0;
    }

    private void deductAiFromSub(UserSubscription sub) {
        if (sub.getPlan().getQuotaAi() != -1) {
            sub.setRemainAi(sub.getRemainAi() - 1);
        }
        sub.setUsedAi(sub.getUsedAi() + 1);
        userSubscriptionRepository.save(sub);
    }

    @Override
    @Transactional
    public void refund(String credentialId, String serviceCode) {
        var credential = userCredentialsRepository
                .findById(credentialId)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_EXISTED));

        Users user = credential.getUser();

        ServicePricing pricing = servicePricingRepository
                .findByServiceCode(serviceCode)
                .orElseThrow(() -> new AppException(ErrorCode.INVALID_KEY));

        int creditCost = pricing.getCreditCost();
        int balanceBefore = user.getCredit() != null ? user.getCredit().intValue() : 0;
        int balanceAfter = balanceBefore + creditCost;

        user.setCredit((double) balanceAfter);
        usersRepository.save(user);

        CreditTransaction tx = CreditTransaction.builder()
                .delta(creditCost)
                .balanceBefore(balanceBefore)
                .balanceAfter(balanceAfter)
                .paymentType(PaymentType.REFUND)
                .createdAt(LocalDateTime.now())
                .user(user)
                .servicePricing(pricing)
                .build();

        creditTransactionRepository.save(tx);
    }

    @Override
    public int getBalance(String credentialId) {
        var credential = userCredentialsRepository
                .findById(credentialId)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_EXISTED));

        Users user = credential.getUser();
        return user.getCredit() != null ? user.getCredit().intValue() : 0;
    }
}
