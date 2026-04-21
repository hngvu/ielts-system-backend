package io.gsp26se16.moni.payment.repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import io.gsp26se16.moni.payment.entity.UserSubscription;

@Repository
public interface UserSubscriptionRepository extends JpaRepository<UserSubscription, Integer> {
    /** Gói đang active của user (endAt > now, isActive=true). Tại 1 thời điểm chỉ nên có 1. */
    Optional<UserSubscription> findFirstByUser_IdAndIsActiveTrueAndEndAtAfterOrderByEndAtDesc(
            String userId, LocalDateTime now);

    /** Dùng cho scheduler expire — tìm các sub đã quá hạn nhưng vẫn isActive=true. */
    List<UserSubscription> findByIsActiveTrueAndEndAtBefore(LocalDateTime now);

    List<UserSubscription> findByUser_IdOrderByStartAtDesc(String userId);

    /** Gói đang active của user theo category (SCORING hoặc ROADMAP). */
    Optional<UserSubscription> findFirstByUser_IdAndIsActiveTrueAndEndAtAfterAndPlan_CategoryOrderByEndAtDesc(
            String userId, LocalDateTime now, String category);
}
