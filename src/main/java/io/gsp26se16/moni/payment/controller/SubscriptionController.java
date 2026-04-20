package io.gsp26se16.moni.payment.controller;

import java.util.List;
import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import io.gsp26se16.moni.payment.dto.request.SubscriptionPlanUpsertRequest;
import io.gsp26se16.moni.payment.dto.response.SubscriptionPlanResponse;
import io.gsp26se16.moni.payment.service.SubscriptionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Subscription endpoints — public list, user's own active sub, admin CRUD.
 * Route prefix /subscriptions theo quy ước REST.
 */
@RestController
@RequestMapping("/subscriptions")
@RequiredArgsConstructor
@Slf4j
public class SubscriptionController {

    private final SubscriptionService subscriptionService;

    /** Public: liệt kê plan isActive=true cho user xem trang /payment. */
    @GetMapping("/plans")
    public ResponseEntity<List<SubscriptionPlanResponse>> listActivePlans() {
        return ResponseEntity.ok(subscriptionService.listActivePlans());
    }

    /** Admin: tất cả plans (cả inactive) cho trang /admin/pricing tab Subscriptions. */
    @GetMapping("/admin/plans")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<SubscriptionPlanResponse>> listAllPlansForAdmin() {
        return ResponseEntity.ok(subscriptionService.listAllPlans());
    }

    @GetMapping("/plans/{id}")
    public ResponseEntity<SubscriptionPlanResponse> getPlan(@PathVariable Integer id) {
        return ResponseEntity.ok(subscriptionService.getPlan(id));
    }

    @PostMapping("/admin/plans")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<SubscriptionPlanResponse> createPlan(@RequestBody SubscriptionPlanUpsertRequest req) {
        return ResponseEntity.ok(subscriptionService.createPlan(req));
    }

    @PutMapping("/admin/plans/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<SubscriptionPlanResponse> updatePlan(
            @PathVariable Integer id, @RequestBody SubscriptionPlanUpsertRequest req) {
        return ResponseEntity.ok(subscriptionService.updatePlan(id, req));
    }

    @DeleteMapping("/admin/plans/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> deletePlan(@PathVariable Integer id) {
        subscriptionService.deletePlan(id);
        return ResponseEntity.noContent().build();
    }

    /** User: gói đang active (null nếu không có). FE dùng cho active-sub banner. */
    @GetMapping("/my/active")
    public ResponseEntity<Map<String, Object>> getMyActiveSubscription(@AuthenticationPrincipal Jwt jwt) {
        String credentialId = jwt.getSubject();
        return subscriptionService
                .getMyActiveSubscription(credentialId)
                .<ResponseEntity<Map<String, Object>>>map(sub -> ResponseEntity.ok(Map.of("subscription", sub)))
                .orElseGet(() -> ResponseEntity.ok(Map.of()));
    }
}
