package io.gsp26se16.moni.payment.repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import io.gsp26se16.moni.payment.entity.Payment;
import io.gsp26se16.moni.payment.enumeration.PaymentStatus;

@Repository
public interface PaymentRepository extends JpaRepository<Payment, Integer>, JpaSpecificationExecutor<Payment> {
    boolean existsByTxnCode(String txnCode);

    List<Payment> findByStatusAndExpiredAtBefore(PaymentStatus status, LocalDateTime now);

    @EntityGraph(attributePaths = {"user", "packagePricing"})
    Optional<Payment> findByTxnCode(String txnCode);

    @Query("SELECT COALESCE(SUM(p.amount), 0) FROM Payment p "
            + "WHERE p.status = :status AND p.createdAt >= :startDate AND p.createdAt <= :endDate")
    long sumAmountByStatusAndCreatedAtBetween(
            @Param("status") PaymentStatus status,
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate);

    long countByStatusAndCreatedAtBetween(PaymentStatus status, LocalDateTime startDate, LocalDateTime endDate);

    @Query("SELECT DATE(p.createdAt) as date, COALESCE(SUM(p.amount), 0) as amount FROM Payment p "
            + "WHERE p.status = :status AND p.createdAt >= :startDate AND p.createdAt <= :endDate "
            + "GROUP BY DATE(p.createdAt) ORDER BY date")
    List<Object[]> getDailyRevenueByStatusAndCreatedAtBetween(
            @Param("status") PaymentStatus status,
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate);
}
