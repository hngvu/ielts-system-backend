package io.gsp26se16.moni.payment.repository;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import io.gsp26se16.moni.payment.entity.Payment;
import io.gsp26se16.moni.payment.enumeration.PaymentStatus;

@Repository
public interface PaymentRepository extends JpaRepository<Payment, Integer>, JpaSpecificationExecutor<Payment> {
    boolean existsByTxnCode(String txnCode);

    List<Payment> findByStatusAndExpiredAtBefore(PaymentStatus status, LocalDateTime now);
}
