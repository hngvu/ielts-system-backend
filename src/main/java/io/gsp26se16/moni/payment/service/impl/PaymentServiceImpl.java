package io.gsp26se16.moni.payment.service.impl;

import io.gsp26se16.moni.payment.dto.request.PaymentInitRequest;
import io.gsp26se16.moni.payment.dto.request.SePayWebhookRequest;
import io.gsp26se16.moni.payment.dto.response.PaymentInitResponse;
import io.gsp26se16.moni.payment.dto.response.PaymentResponse;
import io.gsp26se16.moni.payment.entity.Payment;
import io.gsp26se16.moni.payment.enumeration.PaymentStatus;
import io.gsp26se16.moni.payment.repository.PackagePricingRepository;
import io.gsp26se16.moni.payment.repository.PaymentRepository;
import io.gsp26se16.moni.payment.service.PaymentService;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.RandomStringUtils;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class PaymentServiceImpl implements PaymentService {
    private final PaymentRepository paymentRepository;
    private final PackagePricingRepository packagePricingRepository;
    private final String txnCodePrefix = "MN";
    private final String txnCodeCharset = "23456789ABCDEFGHJKMNPQRSTUVWXYZ"; // exclude 0,1,I,L,O
    private final int txnCodeLength = 6 - txnCodePrefix.length();

    @Override
    public PaymentInitResponse initPayment(PaymentInitRequest paymentInitRequest) {
        var packagePricing = packagePricingRepository.findById(paymentInitRequest.packageId())
                .orElseThrow(() -> new RuntimeException("Package pricing not found"));

        if (packagePricing.getPrice() != paymentInitRequest.amount()) {
            throw new RuntimeException("Amount does not match package pricing");
        }

        var payment = paymentRepository.save(
                Payment.builder()
                        .packagePricing(packagePricing)
                        .amount(paymentInitRequest.amount())
                        .txnCode(generateTxnCode()) // check unique later
                        .createdAt(LocalDateTime.now())
                        .expiredAt(LocalDateTime.now().plusMinutes(5))
                        .status(PaymentStatus.PENDING)
                        .user(null) // To be replaced with actual user
                        .build()
        );

        return PaymentInitResponse.builder()
                .id(payment.getId())
                .amount(payment.getAmount())
                .txnCode(payment.getTxnCode())
                .qrCodeUrl("https://qr.sepay.vn/img?") // add acc, bank, amount, des
                .expiredAt(payment.getExpiredAt())
                .build();
    }

    @Override
    public PaymentResponse handleSePayCallback(SePayWebhookRequest sePayWebhookRequest) {

        Pattern pattern = Pattern.compile(Pattern.quote(txnCodePrefix) + "[" + txnCodeCharset + "]{" + txnCodeLength + "}");
        Matcher matcher = pattern.matcher(sePayWebhookRequest.content());
        String txnCode = matcher.find() ? matcher.group() : null;

        // repo find by txnCode

        // update payment status


        return null;
    }

    private String generateTxnCode() {
        return txnCodePrefix + RandomStringUtils.random(txnCodeLength, txnCodeCharset);
    }
}
