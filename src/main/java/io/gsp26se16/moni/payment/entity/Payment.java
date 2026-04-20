package io.gsp26se16.moni.payment.entity;

import java.time.LocalDateTime;

import jakarta.persistence.*;

import io.gsp26se16.moni.authentication.entity.Users;
import io.gsp26se16.moni.payment.enumeration.PaymentStatus;
import lombok.*;
import lombok.experimental.FieldDefaults;

@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class Payment {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    Integer id;

    String txnCode;
    int amount;

    String gatewayTxnId;

    @Column(columnDefinition = "TEXT")
    String webhookResponse;

    LocalDateTime createdAt;
    LocalDateTime updatedAt;
    LocalDateTime expiredAt;

    @Enumerated(EnumType.STRING)
    PaymentStatus status;

    @ManyToOne
    @JoinColumn(name = "package_id")
    PackagePricing packagePricing;

    // Payment có thể là nạp VND (packagePricing) HOẶC mua gói subscription (subscriptionPlan).
    // Đúng 1 trong 2 được set, không bao giờ cả 2.
    @ManyToOne
    @JoinColumn(name = "subscription_plan_id")
    SubscriptionPlan subscriptionPlan;

    @ManyToOne
    @JoinColumn(name = "user_id")
    Users user;
}
