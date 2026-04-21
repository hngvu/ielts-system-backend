package io.gsp26se16.moni.payment.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import io.gsp26se16.moni.payment.entity.SubscriptionPlan;

@Repository
public interface SubscriptionPlanRepository extends JpaRepository<SubscriptionPlan, Integer> {
    Optional<SubscriptionPlan> findByCode(String code);

    boolean existsByCode(String code);

    List<SubscriptionPlan> findByCategoryAndIsActiveTrue(String category);
}
