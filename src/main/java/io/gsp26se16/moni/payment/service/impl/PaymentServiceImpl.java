package io.gsp26se16.moni.payment.service.impl;

import java.time.LocalDateTime;
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
    private final CreditTransactionRepository creditTransactionRepository;
    private final UsersRepository usersRepository;
    private final UserCredentialsRepository userCredentialsRepository;
    private final PaymentNotificationService notificationService;
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
        if (paymentInitRequest == null
                || paymentInitRequest.packageId() == null
                || paymentInitRequest.amount() == null) {
            throw new AppException(ErrorCode.INVALID_KEY);
        }

        if (paymentInitRequest.amount() <= 0) {
            throw new AppException(ErrorCode.INVALID_KEY);
        }

        var packagePricing = packagePricingRepository
                .findById(paymentInitRequest.packageId())
                .orElseThrow(() -> new AppException(ErrorCode.PACKAGE_PRICING_NOT_FOUND));

        if (packagePricing.getPrice() != paymentInitRequest.amount()) {
            throw new AppException(ErrorCode.INVALID_KEY);
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
                "Init payment: userId={}, package={}, amount={}",
                currentUser.getId(),
                packagePricing.getId(),
                paymentInitRequest.amount());

        var payment = paymentRepository.save(Payment.builder()
                .packagePricing(packagePricing)
                .amount(paymentInitRequest.amount())
                .txnCode(txnCode)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .expiredAt(LocalDateTime.now().plusMinutes(15))
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

        if (payment.getStatus() != PaymentStatus.PENDING) {
            return toResponse(payment);
        }

        if (payment.getExpiredAt() != null && LocalDateTime.now().isAfter(payment.getExpiredAt())) {
            payment.setStatus(PaymentStatus.CANCELLED);
            payment.setUpdatedAt(LocalDateTime.now());
            paymentRepository.save(payment);
            return toResponse(payment);
        }

        if (payment.getAmount() != sePayWebhookRequest.transferAmount().intValue()) {
            payment.setStatus(PaymentStatus.FAILED);
            payment.setUpdatedAt(LocalDateTime.now());
            paymentRepository.save(payment);
            return toResponse(payment);
        }

        // SUCCESS
        payment.setGatewayTxnId(sePayWebhookRequest.code());
        payment.setWebhookResponse(sePayWebhookRequest.toString());
        payment.setStatus(PaymentStatus.SUCCESS);
        payment.setUpdatedAt(LocalDateTime.now());
        paymentRepository.save(payment);

        createCreditTransaction(payment);

        // Push realtime notification to frontend
        if (payment.getUser() != null) {
            double newBalance =
                    payment.getUser().getCredit() != null ? payment.getUser().getCredit() : 0;
            notificationService.notifyPaymentSuccess(payment.getUser().getId(), payment.getId(), newBalance);
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

    private void createCreditTransaction(Payment payment) {
        if (payment.getPackagePricing() == null || payment.getUser() == null) {
            log.error(
                    "Cannot create credit transaction: packagePricing={}, user={}",
                    payment.getPackagePricing(),
                    payment.getUser());
            return;
        }

        int creditAmount = payment.getPackagePricing().getCreditAmount();
        Users user = payment.getUser();
        int currentBalance = user.getCredit() != null ? user.getCredit().intValue() : 0;
        int newBalance = currentBalance + creditAmount;

        log.info(
                "Updating user credit: userId={}, oldBalance={}, creditAmount={}, newBalance={}",
                user.getId(),
                currentBalance,
                creditAmount,
                newBalance);

        user.setCredit((double) newBalance);
        usersRepository.save(user);

        CreditTransaction creditTransaction = CreditTransaction.builder()
                .delta(creditAmount)
                .balanceBefore(currentBalance)
                .balanceAfter(newBalance)
                .paymentType(PaymentType.TOPUP)
                .createdAt(LocalDateTime.now())
                .user(user)
                .payment(payment)
                .build();

        creditTransactionRepository.save(creditTransaction);
        log.info("Credit topped up: user={}, amount=+{}, newBalance={}", user.getId(), creditAmount, newBalance);
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
