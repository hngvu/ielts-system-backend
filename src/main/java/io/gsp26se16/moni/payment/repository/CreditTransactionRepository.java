package io.gsp26se16.moni.payment.repository;

import java.util.List;
import java.util.Optional;

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

    @Query("SELECT COUNT(ct) FROM CreditTransaction ct "
            + "WHERE ct.paymentType = :paymentType "
            + "AND ct.user.id = :userId "
            + "AND ct.servicePricing.serviceCode = :serviceCode "
            + "AND ct.createdAt >= :startDate AND ct.createdAt <= :endDate")
    long countByUserIdAndServiceCodeAndPaymentTypeAndCreatedAtBetween(
            @Param("userId") String userId,
            @Param("paymentType") PaymentType paymentType,
            @Param("serviceCode") String serviceCode,
            @Param("startDate") java.time.LocalDateTime startDate,
            @Param("endDate") java.time.LocalDateTime endDate);

    @Query("SELECT DATE(ct.createdAt) as date, COUNT(ct) as count FROM CreditTransaction ct "
            + "WHERE ct.paymentType = :paymentType "
            + "AND ct.servicePricing.serviceCode = :serviceCode "
            + "AND ct.createdAt >= :startDate AND ct.createdAt <= :endDate "
            + "GROUP BY DATE(ct.createdAt) ORDER BY date")
    List<Object[]> countDailyJobsByServiceCodeAndPaymentTypeAndCreatedAtBetween(
            @Param("paymentType") PaymentType paymentType,
            @Param("serviceCode") String serviceCode,
            @Param("startDate") java.time.LocalDateTime startDate,
            @Param("endDate") java.time.LocalDateTime endDate);

    /**
     * Lấy CONSUME transaction gần nhất của user cho 1 service cụ thể.
     * Dùng khi refund: đọc quotaType để biết đã trừ quota hay tiền → hoàn đúng hình thức.
     */
    @Query("SELECT ct FROM CreditTransaction ct "
            + "WHERE ct.user.id = :userId "
            + "AND ct.servicePricing.serviceCode = :serviceCode "
            + "AND ct.paymentType = :paymentType "
            + "ORDER BY ct.createdAt DESC LIMIT 1")
    Optional<CreditTransaction> findLatestByUserAndServiceAndPaymentType(
            @Param("userId") String userId,
            @Param("serviceCode") String serviceCode,
            @Param("paymentType") PaymentType paymentType);
}
