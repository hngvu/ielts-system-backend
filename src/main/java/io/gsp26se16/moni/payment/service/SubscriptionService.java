package io.gsp26se16.moni.payment.service;

import java.util.List;
import java.util.Optional;

import io.gsp26se16.moni.payment.dto.request.SubscriptionPlanUpsertRequest;
import io.gsp26se16.moni.payment.dto.response.SubscriptionPlanResponse;
import io.gsp26se16.moni.payment.dto.response.UserSubscriptionResponse;

public interface SubscriptionService {
    // ----- Plan: public -----
    List<SubscriptionPlanResponse> listActivePlans();

    List<SubscriptionPlanResponse> listAllPlans(); // admin view

    SubscriptionPlanResponse getPlan(Integer id);

    // ----- Plan: admin CRUD -----
    SubscriptionPlanResponse createPlan(SubscriptionPlanUpsertRequest req);

    SubscriptionPlanResponse updatePlan(Integer id, SubscriptionPlanUpsertRequest req);

    void deletePlan(Integer id);

    // ----- User subscription -----
    /** Lấy gói đang active của user hiện tại (nếu có). */
    Optional<UserSubscriptionResponse> getMyActiveSubscription(String credentialId);

    /**
     * Kích hoạt gói cho user sau khi payment thành công.
     * Gọi từ PaymentServiceImpl khi webhook success với subscriptionPlan != null.
     */
    void activateSubscription(String userId, Integer planId);

    /** Kiểm tra user có gói ROADMAP đang active không. */
    boolean hasActiveRoadmapSubscription(String userId);

    /** Lấy gói ROADMAP đang active của user (nếu có). */
    Optional<UserSubscriptionResponse> getActiveRoadmapSubscription(String credentialId);
}
