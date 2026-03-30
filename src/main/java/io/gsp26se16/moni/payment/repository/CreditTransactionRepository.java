package io.gsp26se16.moni.payment.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import io.gsp26se16.moni.payment.entity.CreditTransaction;
import io.gsp26se16.moni.payment.enumeration.PaymentType;

@Repository
public interface CreditTransactionRepository
        extends JpaRepository<CreditTransaction, Integer>, JpaSpecificationExecutor<CreditTransaction> {
    @Query("SELECT COALESCE(SUM(-ct.delta), 0) FROM CreditTransaction ct "
            + "WHERE ct.paymentType = :paymentType "
            + "AND ct.servicePricing.serviceCode = :serviceCode "
            + "AND ct.createdAt >= :startDate AND ct.createdAt <= :endDate")
    long sumConsumedCreditsByServiceCodeAndCreatedAtBetween(
            @Param("paymentType") PaymentType paymentType,
            @Param("serviceCode") String serviceCode,
            @Param("startDate") java.time.LocalDateTime startDate,
            @Param("endDate") java.time.LocalDateTime endDate);

    @Query("SELECT COUNT(ct) FROM CreditTransaction ct "
            + "WHERE ct.paymentType = :paymentType "
            + "AND ct.servicePricing.serviceCode = :serviceCode "
            + "AND ct.createdAt >= :startDate AND ct.createdAt <= :endDate")
    long countByServiceCodeAndPaymentTypeAndCreatedAtBetween(
            @Param("paymentType") PaymentType paymentType,
            @Param("serviceCode") String serviceCode,
            @Param("startDate") java.time.LocalDateTime startDate,
            @Param("endDate") java.time.LocalDateTime endDate);
}
