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
    private final String txnCodePrefix = "MN";
    private final String txnCodeCharset = "23456789ABCDEFGHJKMNPQRSTUVWXYZ"; // exclude 0,1,I,L,O
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
            throw new IllegalArgumentException("Invalid payment request");
        }

        if (paymentInitRequest.amount() <= 0) {
            throw new IllegalArgumentException("Amount must be greater than 0");
        }

        var packagePricing = packagePricingRepository
                .findById(paymentInitRequest.packageId())
                .orElseThrow(() -> new RuntimeException("Package pricing not found"));

        if (packagePricing.getPrice() != paymentInitRequest.amount()) {
            throw new RuntimeException("Amount does not match package pricing. Expected: " + packagePricing.getPrice()
                    + ", Provided: " + paymentInitRequest.amount());
        }

        // Generate unique transaction code
        String txnCode;
        int attempts = 0;
        do {
            txnCode = generateTxnCode();
            if (attempts++ > 10) {
                throw new RuntimeException("Failed to generate unique transaction code after multiple attempts");
            }
        } while (paymentRepository.existsByTxnCode(txnCode));

        // Get current user from JWT claims
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        Users currentUser = null;
        
        if (authentication != null && authentication.getPrincipal() instanceof Jwt jwt) {
            String userId = jwt.getClaim("userId");
            log.info("Extracted userId from JWT for payment init: {}", userId);
            
            if (userId != null) {
                currentUser = userCredentialsRepository
                        .findById(userId)
                        .map(UserCredentials::getUser)
                        .orElse(null);
                log.info("Found user for payment: {}", currentUser != null ? currentUser.getId() : "null");
            }
        }

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
            throw new IllegalArgumentException("Invalid webhook request");
        }

        Pattern pattern =
                Pattern.compile(Pattern.quote(txnCodePrefix) + "[" + txnCodeCharset + "]{" + txnCodeLength + "}");
        Matcher matcher = pattern.matcher(sePayWebhookRequest.content());
        String txnCode = matcher.find() ? matcher.group() : null;
        log.info("Extracted txnCode from content: {}", txnCode);

        if (txnCode == null) {
            log.error("Transaction code not found in webhook content: {}", sePayWebhookRequest.content());
            throw new IllegalArgumentException("Transaction code not found in webhook content");
        }

        // Find payment by txnCode with user and packagePricing eagerly fetched
        var payment = paymentRepository
                .findByTxnCode(txnCode)
                .orElseThrow(() -> new RuntimeException("Payment not found for transaction code: " + txnCode));

        log.info("Found payment: id={}, userId={}, status={}", payment.getId(), 
                payment.getUser() != null ? payment.getUser().getId() : "null", payment.getStatus());

        // Check if payment is already processed
        if (payment.getStatus() != PaymentStatus.PENDING) {
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

        // Check if payment is expired
        if (payment.getExpiredAt() != null && LocalDateTime.now().isAfter(payment.getExpiredAt())) {
            payment.setStatus(PaymentStatus.CANCELLED);
            payment.setUpdatedAt(LocalDateTime.now());
            paymentRepository.save(payment);

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

        // Validate amount matches
        if (payment.getAmount() != sePayWebhookRequest.transferAmount().intValue()) {
            payment.setStatus(PaymentStatus.FAILED);
            payment.setUpdatedAt(LocalDateTime.now());
            paymentRepository.save(payment);

            // Create refund credit transaction for failed payment
            createRefundCreditTransaction(payment);

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

        // Update payment status
        payment.setGatewayTxnId(sePayWebhookRequest.code());
        payment.setWebhookResponse(sePayWebhookRequest.toString());
        payment.setStatus(PaymentStatus.SUCCESS);
        payment.setUpdatedAt(LocalDateTime.now());
        paymentRepository.save(payment);

        // Create credit transaction for successful payment
        createCreditTransaction(payment);

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

    @Override
    public List<PaymentResponse> searchPayments(
            Integer userId, String status, LocalDateTime startDate, LocalDateTime endDate) {
        Specification<Payment> spec = (root, query, criteriaBuilder) -> {
            Predicate predicate = criteriaBuilder.conjunction();

            if (userId != null) {
                predicate = criteriaBuilder.and(
                        predicate, criteriaBuilder.equal(root.get("user").get("id"), userId));
            }

            if (status != null) {
                try {
                    PaymentStatus paymentStatus = PaymentStatus.valueOf(status.toUpperCase());
                    predicate =
                            criteriaBuilder.and(predicate, criteriaBuilder.equal(root.get("status"), paymentStatus));
                } catch (IllegalArgumentException e) {
                    // Invalid status, return empty list
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

        List<Payment> payments = paymentRepository.findAll(spec);

        return payments.stream()
                .map(payment -> PaymentResponse.builder()
                        .id(payment.getId())
                        .packageId(
                                payment.getPackagePricing() != null
                                        ? payment.getPackagePricing().getId()
                                        : null)
                        .txnCode(payment.getTxnCode())
                        .amount(payment.getAmount())
                        .updatedAt(payment.getUpdatedAt())
                        .status(payment.getStatus().toString())
                        .build())
                .collect(Collectors.toList());
    }

    private String generateTxnCode() {
        return txnCodePrefix + RandomStringUtils.random(txnCodeLength, txnCodeCharset);
    }

    private void createCreditTransaction(Payment payment) {
        if (payment.getPackagePricing() == null || payment.getUser() == null) {
            log.error("Cannot create credit transaction: packagePricing={}, user={}", 
                    payment.getPackagePricing(), payment.getUser());
            return;
        }

        // Calculate credits based on package pricing
        int creditAmount = payment.getPackagePricing().getCreditAmount();

        Users user = payment.getUser();
        // Get actual current balance from user entity
        int currentBalance = user.getCredit() != null ? user.getCredit().intValue() : 0;
        int newBalance = currentBalance + creditAmount;

        log.info("Updating user credit: userId={}, oldBalance={}, creditAmount={}, newBalance={}", 
                user.getId(), currentBalance, creditAmount, newBalance);

        // Update user's credit balance
        user.setCredit((double) newBalance);
        usersRepository.save(user);

        // Create credit transaction
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
    }

    private void createRefundCreditTransaction(Payment payment) {
        if (payment.getPackagePricing() == null || payment.getUser() == null) {
            return; // Skip if package or user is null
        }

        Users user = payment.getUser();
        // Get actual current balance from user entity
        int currentBalance = user.getCredit() != null ? user.getCredit().intValue() : 0;

        // For failed payments, create a zero-delta transaction for tracking
        // (no credit change, just record the failed attempt)
        CreditTransaction creditTransaction = CreditTransaction.builder()
                .delta(0)
                .balanceBefore(currentBalance)
                .balanceAfter(currentBalance)
                .paymentType(PaymentType.REFUND)
                .createdAt(LocalDateTime.now())
                .user(user)
                .payment(payment)
                .build();

        creditTransactionRepository.save(creditTransaction);
    }
}
