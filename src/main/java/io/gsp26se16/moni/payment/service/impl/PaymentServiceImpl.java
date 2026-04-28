package io.gsp26se16.moni.payment.service.impl;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import jakarta.persistence.criteria.Predicate;

import org.apache.commons.lang3.RandomStringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.gsp26se16.moni.authentication.entity.UserCredentials;
import io.gsp26se16.moni.authentication.entity.Users;
import io.gsp26se16.moni.authentication.repository.UserCredentialsRepository;
import io.gsp26se16.moni.authentication.repository.UsersRepository;
import io.gsp26se16.moni.common.exception.AppException;
import io.gsp26se16.moni.common.exception.ErrorCode;
import io.gsp26se16.moni.payment.dto.request.PaymentInitRequest;
import io.gsp26se16.moni.payment.dto.request.SePayWebhookRequest;
import io.gsp26se16.moni.payment.dto.response.PaymentInitResponse;
import io.gsp26se16.moni.payment.dto.response.PaymentResponse;
import io.gsp26se16.moni.payment.entity.CreditTransaction;
import io.gsp26se16.moni.payment.entity.Payment;
import io.gsp26se16.moni.payment.enumeration.PaymentStatus;
import io.gsp26se16.moni.payment.enumeration.PaymentType;
import io.gsp26se16.moni.payment.repository.CreditTransactionRepository;
import io.gsp26se16.moni.payment.repository.PackagePricingRepository;
import io.gsp26se16.moni.payment.repository.PaymentRepository;
import io.gsp26se16.moni.payment.service.PaymentNotificationService;
import io.gsp26se16.moni.payment.service.PaymentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentServiceImpl implements PaymentService {
    private final PaymentRepository paymentRepository;
    private final PackagePricingRepository packagePricingRepository;
    private final io.gsp26se16.moni.payment.repository.SubscriptionPlanRepository subscriptionPlanRepository;
    private final CreditTransactionRepository creditTransactionRepository;
    private final UsersRepository usersRepository;
    private final UserCredentialsRepository userCredentialsRepository;
    private final PaymentNotificationService notificationService;
    private final io.gsp26se16.moni.payment.service.SubscriptionService subscriptionService;
    private final String txnCodePrefix = "MN";
    private final String txnCodeCharset = "23456789ABCDEFGHJKMNPQRSTUVWXYZ";
    private final int txnCodeLength = 6 - txnCodePrefix.length();

    @Value("${sepay.acc}")
    private String sepayAcc;

    @Value("${sepay.bank}")
    private String sepayBank;

    @Override
    @Transactional
    public PaymentInitResponse initPayment(PaymentInitRequest paymentInitRequest) {
        if (paymentInitRequest == null || paymentInitRequest.amount() == null || paymentInitRequest.amount() <= 0) {
            throw new AppException(ErrorCode.INVALID_KEY);
        }

        // Exactly-one: hoặc nạp VND (packageId) hoặc mua sub (subscriptionPlanId). Không cả 2, không thiếu.
        boolean hasPackage = paymentInitRequest.packageId() != null;
        boolean hasSubscription = paymentInitRequest.subscriptionPlanId() != null;
        if (hasPackage == hasSubscription) {
            throw new AppException(ErrorCode.INVALID_KEY);
        }

        io.gsp26se16.moni.payment.entity.PackagePricing packagePricing = null;
        io.gsp26se16.moni.payment.entity.SubscriptionPlan subscriptionPlan = null;
        if (hasPackage) {
            packagePricing = packagePricingRepository
                    .findById(paymentInitRequest.packageId())
                    .orElseThrow(() -> new AppException(ErrorCode.PACKAGE_PRICING_NOT_FOUND));
            if (packagePricing.getPrice() != paymentInitRequest.amount()) {
                throw new AppException(ErrorCode.INVALID_KEY);
            }
        } else {
            subscriptionPlan = subscriptionPlanRepository
                    .findById(paymentInitRequest.subscriptionPlanId())
                    .orElseThrow(() -> new AppException(ErrorCode.PACKAGE_PRICING_NOT_FOUND));
            if (!subscriptionPlan.isActive() || subscriptionPlan.getPriceVnd() != paymentInitRequest.amount()) {
                throw new AppException(ErrorCode.INVALID_KEY);
            }
        }

        String txnCode;
        int attempts = 0;
        do {
            txnCode = generateTxnCode();
            if (attempts++ > 10) {
                throw new AppException(ErrorCode.UNCATEGORIZED_EXCEPTION);
            }
        } while (paymentRepository.existsByTxnCode(txnCode));

        Users currentUser = getCurrentUser();
        log.info(
                "Init payment: userId={}, type={}, targetId={}, amount={}",
                currentUser.getId(),
                hasPackage ? "PACKAGE" : "SUBSCRIPTION",
                hasPackage ? packagePricing.getId() : subscriptionPlan.getId(),
                paymentInitRequest.amount());

        LocalDateTime nowUtc = LocalDateTime.now(ZoneOffset.UTC);
        var payment = paymentRepository.save(Payment.builder()
                .packagePricing(packagePricing)
                .subscriptionPlan(subscriptionPlan)
                .amount(paymentInitRequest.amount())
                .txnCode(txnCode)
                .createdAt(nowUtc)
                .updatedAt(nowUtc)
                .expiredAt(nowUtc.plusMinutes(15))
                .status(PaymentStatus.PENDING)
                .user(currentUser)
                .build());

        return PaymentInitResponse.builder()
                .id(payment.getId())
                .amount(payment.getAmount())
                .txnCode(payment.getTxnCode())
                .qrCodeUrl("https://qr.sepay.vn/img?" + "acc=" + sepayAcc + "&bank=" + sepayBank + "&amount="
                        + payment.getAmount() + "&des=" + payment.getTxnCode())
                .expiredAt(payment.getExpiredAt())
                .build();
    }

    @Override
    @Transactional
    public PaymentResponse handleSePayCallback(SePayWebhookRequest sePayWebhookRequest) {
        log.info("Handling SEpay callback: {}", sePayWebhookRequest);

        if (sePayWebhookRequest == null || sePayWebhookRequest.content() == null) {
            log.error("Invalid webhook request: content is null");
            throw new AppException(ErrorCode.INVALID_KEY);
        }

        Pattern pattern =
                Pattern.compile(Pattern.quote(txnCodePrefix) + "[" + txnCodeCharset + "]{" + txnCodeLength + "}");
        Matcher matcher = pattern.matcher(sePayWebhookRequest.content());
        String txnCode = matcher.find() ? matcher.group() : null;
        log.info("Extracted txnCode from content: {}", txnCode);

        if (txnCode == null) {
            log.error("Transaction code not found in webhook content: {}", sePayWebhookRequest.content());
            throw new AppException(ErrorCode.INVALID_KEY);
        }

        var payment = paymentRepository
                .findByTxnCode(txnCode)
                .orElseThrow(() -> new AppException(ErrorCode.CREDIT_TRANSACTION_NOT_FOUND));

        log.info(
                "Found payment: id={}, userId={}, status={}",
                payment.getId(),
                payment.getUser() != null ? payment.getUser().getId() : "null",
                payment.getStatus());

        // Already finalized by a previous webhook (SUCCESS, FAILED, LATE_PAYMENT)
        if (payment.getStatus() == PaymentStatus.SUCCESS
                || payment.getStatus() == PaymentStatus.FAILED
                || payment.getStatus() == PaymentStatus.LATE_PAYMENT) {
            log.warn(
                    "Duplicate webhook received for already-finalized payment: id={}, status={}",
                    payment.getId(),
                    payment.getStatus());
            return toResponse(payment);
        }

        if (payment.getGatewayTxnId() != null && payment.getGatewayTxnId().equals(sePayWebhookRequest.code())) {
            log.warn("Duplicate webhook with same gatewayTxnId: {}", payment.getGatewayTxnId());
            return toResponse(payment);
        }

        // Amount mismatch → FAILED regardless of timing
        if (payment.getAmount() != sePayWebhookRequest.transferAmount().intValue()) {
            log.warn(
                    "Amount mismatch: expected={}, received={}, paymentId={}",
                    payment.getAmount(),
                    sePayWebhookRequest.transferAmount(),
                    payment.getId());
            payment.setStatus(PaymentStatus.FAILED);
            payment.setGatewayTxnId(sePayWebhookRequest.code());
            payment.setWebhookResponse(sePayWebhookRequest.toString());
            payment.setUpdatedAt(LocalDateTime.now(ZoneOffset.UTC));
            paymentRepository.save(payment);
            return toResponse(payment);
        }

        // Late payment: status is EXPIRED (set by scheduler) OR expiredAt has passed but scheduler hasn't run yet
        boolean isExpiredByScheduler = payment.getStatus() == PaymentStatus.EXPIRED;
        boolean isExpiredByTime = payment.getExpiredAt() != null
                && LocalDateTime.now(ZoneOffset.UTC).isAfter(payment.getExpiredAt());

        if (isExpiredByScheduler || isExpiredByTime) {
            log.warn(
                    "Late payment received: txnCode={}, paymentId={}, wasExpiredByScheduler={}",
                    txnCode,
                    payment.getId(),
                    isExpiredByScheduler);
            payment.setStatus(PaymentStatus.LATE_PAYMENT);
            payment.setGatewayTxnId(sePayWebhookRequest.code());
            payment.setWebhookResponse(sePayWebhookRequest.toString());
            payment.setUpdatedAt(LocalDateTime.now(ZoneOffset.UTC));
            paymentRepository.save(payment);

            createCreditTransaction(payment, true);

            if (payment.getUser() != null) {
                notificationService.notifyPaymentSuccess(payment.getUser().getId(), payment.getId(), 0);
            }

            return toResponse(payment);
        }

        // SUCCESS (PENDING + within expiry window + amount matches)
        payment.setGatewayTxnId(sePayWebhookRequest.code());
        payment.setWebhookResponse(sePayWebhookRequest.toString());
        payment.setStatus(PaymentStatus.SUCCESS);
        payment.setUpdatedAt(LocalDateTime.now(ZoneOffset.UTC));
        paymentRepository.save(payment);

        createCreditTransaction(payment, false);

        // Push realtime notification to frontend
        if (payment.getUser() != null) {
            notificationService.notifyPaymentSuccess(payment.getUser().getId(), payment.getId(), 0);
        }

        return toResponse(payment);
    }

    @Override
    public List<PaymentResponse> searchPayments(
            Integer userId, String status, LocalDateTime startDate, LocalDateTime endDate) {

        String currentUserId = getCurrentUserIdFromJwt();

        Specification<Payment> spec = (root, query, criteriaBuilder) -> {
            Predicate predicate = criteriaBuilder.conjunction();

            if (currentUserId != null) {
                predicate = criteriaBuilder.and(
                        predicate, criteriaBuilder.equal(root.get("user").get("id"), currentUserId));
            }

            if (status != null) {
                try {
                    PaymentStatus paymentStatus = PaymentStatus.valueOf(status.toUpperCase());
                    predicate =
                            criteriaBuilder.and(predicate, criteriaBuilder.equal(root.get("status"), paymentStatus));
                } catch (IllegalArgumentException e) {
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

        return paymentRepository.findAll(spec).stream().map(this::toResponse).collect(Collectors.toList());
    }

    private String generateTxnCode() {
        return txnCodePrefix + RandomStringUtils.random(txnCodeLength, txnCodeCharset);
    }

    private void createCreditTransaction(Payment payment, boolean isLatePayment) {
        if (payment.getUser() == null) {
            log.error("Cannot process payment: user is null, paymentId={}", payment.getId());
            return;
        }

        // Subscription purchase → kích hoạt gói + log transaction để hiện trong /transactions.
        // delta=0 vì balance VND không đổi, nhưng user vẫn cần thấy giao dịch này trong history.
        if (payment.getSubscriptionPlan() != null) {
            Users subUser = payment.getUser();
            subscriptionService.activateSubscription(
                    subUser.getId(), payment.getSubscriptionPlan().getId());

            CreditTransaction subTx = CreditTransaction.builder()
                    .delta(0)
                    .balanceBefore(0)
                    .balanceAfter(0)
                    .paymentType(PaymentType.SUBSCRIPTION_PURCHASE)
                    .createdAt(LocalDateTime.now(ZoneOffset.UTC))
                    .user(subUser)
                    .payment(payment)
                    .remark("Mua " + payment.getSubscriptionPlan().getName() + " · "
                            + String.format("%,d", payment.getAmount()) + "đ")
                    .build();
            creditTransactionRepository.save(subTx);

            log.info(
                    "Subscription activated + tx logged: user={}, plan={}, amount={}, late={}",
                    subUser.getId(),
                    payment.getSubscriptionPlan().getCode(),
                    payment.getAmount(),
                    isLatePayment);
            return;
        }

        if (payment.getPackagePricing() == null) {
            log.error(
                    "Cannot create credit transaction: neither package nor subscription set, paymentId={}",
                    payment.getId());
            return;
        }

        int creditAmount = payment.getPackagePricing().getCreditAmount();
        Users user = payment.getUser();

        log.info(
                "Processing package payment: userId={}, creditAmount={}, isLate={}",
                user.getId(),
                creditAmount,
                isLatePayment);

        CreditTransaction creditTransaction = CreditTransaction.builder()
                .delta(creditAmount)
                .balanceBefore(0)
                .balanceAfter(0)
                .paymentType(isLatePayment ? PaymentType.LATE_PAYMENT_TOPUP : PaymentType.TOPUP)
                .createdAt(LocalDateTime.now(ZoneOffset.UTC))
                .user(user)
                .payment(payment)
                .remark(
                        isLatePayment
                                ? "Late payment after expiry - admin review may be needed. txnCode: "
                                        + payment.getTxnCode()
                                : null)
                .build();

        creditTransactionRepository.save(creditTransaction);
        log.info(
                "Credit topped up: user={}, amount=+{}, newBalance={}, late={}",
                user.getId(),
                creditAmount,
                newBalance,
                isLatePayment);
    }

    private Users getCurrentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof Jwt jwt) {
            String credentialId = jwt.getClaimAsString("userId");
            if (credentialId != null) {
                UserCredentials cred = userCredentialsRepository
                        .findById(credentialId)
                        .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_EXISTED));
                return cred.getUser();
            }
        }
        throw new AppException(ErrorCode.UNAUTHENTICATED);
    }

    private String getCurrentUserIdFromJwt() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof Jwt jwt) {
            String credentialId = jwt.getClaimAsString("userId");
            if (credentialId != null) {
                return userCredentialsRepository
                        .findById(credentialId)
                        .map(cred -> cred.getUser().getId())
                        .orElse(null);
            }
        }
        return null;
    }

    private PaymentResponse toResponse(Payment payment) {
        return PaymentResponse.builder()
                .id(payment.getId())
                .packageId(
                        payment.getPackagePricing() != null
                                ? payment.getPackagePricing().getId()
                                : null)
                .txnCode(payment.getTxnCode())
                .amount(payment.getAmount())
                .updatedAt(payment.getUpdatedAt())
                .status(payment.getStatus().toString())
                .build();
    }
}
