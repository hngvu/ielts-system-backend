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

@Service
@RequiredArgsConstructor
public class PaymentServiceImpl implements PaymentService {
    private final PaymentRepository paymentRepository;
    private final PackagePricingRepository packagePricingRepository;

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
                        .txnCode(generateTxnCode())
                        .createdAt(LocalDateTime.now())
                        .expiredAt(LocalDateTime.now().plusMinutes(5))
                        .status(PaymentStatus.PENDING)
                        .user(null) // To be replaced with actual user
                        .build()
        );

        return PaymentInitResponse.builder()

                .build();
    }

    @Override
    public PaymentResponse handleSePayCallback(SePayWebhookRequest sePayWebhookRequest) {
        return null;
    }

    private String generateTxnCode() {
        return "MN" + RandomStringUtils.randomAlphanumeric(4).toUpperCase();
    }
}
