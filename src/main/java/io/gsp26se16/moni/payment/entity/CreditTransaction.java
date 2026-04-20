package io.gsp26se16.moni.payment.entity;

import java.time.LocalDateTime;

import jakarta.persistence.*;

import io.gsp26se16.moni.authentication.entity.Users;
import io.gsp26se16.moni.payment.enumeration.PaymentType;
import lombok.*;
import lombok.experimental.FieldDefaults;

@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
@Builder
public class CreditTransaction {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    Integer id;

    int delta;
    int balanceBefore;
    int balanceAfter;

    @Enumerated(EnumType.STRING)
    PaymentType paymentType;

    LocalDateTime createdAt;

    @ManyToOne
    @JoinColumn(name = "user_id")
    Users user;

    @ManyToOne
    @JoinColumn(name = "service_id")
    ServicePricing servicePricing;

    @OneToOne
    @JoinColumn(name = "payment_id")
    Payment payment;

    String remark;

    // Quota tracking cho CONSUME khi dùng gói subscription (delta=0, balance VND không đổi).
    // quotaType = "AI" hoặc "EXPERT". quotaBefore/After = số lượt còn trước/sau lần dùng.
    // Null nếu giao dịch không liên quan quota (nạp tiền, trừ VND).
    String quotaType;
    Integer quotaBefore;
    Integer quotaAfter;
}
