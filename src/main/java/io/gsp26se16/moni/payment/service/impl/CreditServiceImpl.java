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

        boolean isAi = "AI_SPEAKING_SCORE".equals(serviceCode) || "AI_WRITING_SCORE".equals(serviceCode);
        boolean isExpert = "EXPERT_WRITING_SCORE".equals(serviceCode) || "EXPERT_SPEAKING_SCORE".equals(serviceCode);

        // Free first turn per day for AI
        if (isAi) {
            LocalDateTime startOfDay = LocalDate.now().atStartOfDay();
            LocalDateTime endOfDay = startOfDay.plusDays(1).minusNanos(1);
            long usedToday = creditTransactionRepository.countByUserIdAndServiceCodeAndPaymentTypeAndCreatedAtBetween(
                    user.getId(), PaymentType.CONSUME, serviceCode, startOfDay, endOfDay);
            if (usedToday == 0) {
                // Log free turn transaction
                logFreeTurn(user, pricing);
                return;
            }
        }

        // Deduct from subscription quota
        var activeSubOpt = userSubscriptionRepository.findFirstByUser_IdAndIsActiveTrueAndEndAtAfterOrderByEndAtDesc(
                user.getId(), LocalDateTime.now());
        if (activeSubOpt.isPresent()) {
            UserSubscription sub = activeSubOpt.get();
            if (isAi && canDeductAiFromSub(sub)) {
                int before = sub.getPlan().getQuotaAi() == -1 ? -1 : sub.getRemainAi();
                deductAiFromSub(sub);
                int after = sub.getPlan().getQuotaAi() == -1 ? -1 : sub.getRemainAi();
                logQuotaConsume(
                        user, pricing, "AI", before, after, sub.getPlan().getName());
                return;
            }
            if (isExpert && sub.getRemainExpert() > 0) {
                int before = sub.getRemainExpert();
                sub.setRemainExpert(before - 1);
                sub.setUsedExpert(sub.getUsedExpert() + 1);
                userSubscriptionRepository.save(sub);
                logQuotaConsume(
                        user,
                        pricing,
                        "EXPERT",
                        before,
                        sub.getRemainExpert(),
                        sub.getPlan().getName());
                return;
            }
        }

        // No subscription or quota exhausted
        throw new AppException(ErrorCode.INSUFFICIENT_CREDIT);
    }

    /** Log free turn transaction (first AI use of the day). */
    private void logFreeTurn(Users user, ServicePricing pricing) {
        CreditTransaction tx = CreditTransaction.builder()
                .delta(0)
                .balanceBefore(0)
                .balanceAfter(0)
                .paymentType(PaymentType.CONSUME)
                .createdAt(LocalDateTime.now())
                .user(user)
                .servicePricing(pricing)
                .remark(pricing.getName() + " (miễn phí)")
                .build();
        creditTransactionRepository.save(tx);
    }

    /** Log CreditTransaction cho 1 lần dùng quota sub. */
    private void logQuotaConsume(
            Users user, ServicePricing pricing, String quotaType, int before, int after, String planName) {
        CreditTransaction tx = CreditTransaction.builder()
                .delta(0)
                .balanceBefore(0)
                .balanceAfter(0)
                .paymentType(PaymentType.CONSUME)
                .createdAt(LocalDateTime.now())
                .user(user)
                .servicePricing(pricing)
                .quotaType(quotaType)
                .quotaBefore(before)
                .quotaAfter(after)
                .remark(pricing.getName() + " (gói " + planName + ")")
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

        // Refund to subscription quota only
        var lastConsume = creditTransactionRepository.findLatestByUserAndServiceAndPaymentType(
                user.getId(), serviceCode, PaymentType.CONSUME);

        if (lastConsume.isPresent() && lastConsume.get().getQuotaType() != null) {
            refundToSubscription(user, pricing, lastConsume.get().getQuotaType());
        }
        // No VND refund — credit system removed
    }

    /** Hoàn 1 lượt vào subscription active. */
    private void refundToSubscription(Users user, ServicePricing pricing, String quotaType) {
        var activeSubOpt = userSubscriptionRepository.findFirstByUser_IdAndIsActiveTrueAndEndAtAfterOrderByEndAtDesc(
                user.getId(), LocalDateTime.now());
        if (activeSubOpt.isEmpty()) return;

        UserSubscription sub = activeSubOpt.get();
        int before;
        int after;
        if ("AI".equals(quotaType)) {
            before = sub.getPlan().getQuotaAi() == -1 ? -1 : sub.getRemainAi();
            if (sub.getPlan().getQuotaAi() != -1) {
                sub.setRemainAi(sub.getRemainAi() + 1);
            }
            sub.setUsedAi(Math.max(0, sub.getUsedAi() - 1));
            after = sub.getPlan().getQuotaAi() == -1 ? -1 : sub.getRemainAi();
        } else {
            before = sub.getRemainExpert();
            sub.setRemainExpert(before + 1);
            sub.setUsedExpert(Math.max(0, sub.getUsedExpert() - 1));
            after = sub.getRemainExpert();
        }
        userSubscriptionRepository.save(sub);

        CreditTransaction tx = CreditTransaction.builder()
                .delta(0)
                .balanceBefore(0)
                .balanceAfter(0)
                .paymentType(PaymentType.REFUND)
                .createdAt(LocalDateTime.now())
                .user(user)
                .servicePricing(pricing)
                .quotaType(quotaType)
                .quotaBefore(before)
                .quotaAfter(after)
                .remark("Hoàn " + pricing.getName() + " (gói " + sub.getPlan().getName() + ")")
                .build();
        creditTransactionRepository.save(tx);
    }

    @Override
    public int getBalance(String credentialId) {
        return 0; // Credit system removed
    }
}
