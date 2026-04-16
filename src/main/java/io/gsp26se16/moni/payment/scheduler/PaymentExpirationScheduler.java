package io.gsp26se16.moni.payment.scheduler;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import io.gsp26se16.moni.payment.entity.Payment;
import io.gsp26se16.moni.payment.enumeration.PaymentStatus;
import io.gsp26se16.moni.payment.repository.PaymentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Component
@RequiredArgsConstructor
@Slf4j
public class PaymentExpirationScheduler {
    private final PaymentRepository paymentRepository;

    @Scheduled(fixedRate = 60000) // Run every 60 seconds
    @Transactional
    public void expireOverduePayments() {
        List<Payment> expired = paymentRepository.findByStatusAndExpiredAtBefore(
                PaymentStatus.PENDING, LocalDateTime.now(ZoneOffset.UTC));

        if (expired.isEmpty()) return;

        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        for (Payment payment : expired) {
            payment.setStatus(PaymentStatus.EXPIRED);
            payment.setUpdatedAt(now);
        }
        paymentRepository.saveAll(expired);
        log.info("Expired {} overdue payments", expired.size());
    }
}
