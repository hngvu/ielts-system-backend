package io.gsp26se16.moni.payment.dto.request;

/**
 * Init payment cho 1 trong 2 loại (exactly-one):
 *   - Nạp VND vào ví: set packageId + amount (amount == package.price)
 *   - Mua gói subscription: set subscriptionPlanId + amount (amount == plan.priceVnd)
 */
public record PaymentInitRequest(Integer packageId, Integer subscriptionPlanId, Integer amount) {}
