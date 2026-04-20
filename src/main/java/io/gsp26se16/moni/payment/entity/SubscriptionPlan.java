package io.gsp26se16.moni.payment.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;

import lombok.*;
import lombok.experimental.FieldDefaults;

/**
 * Gói subscription tháng. Admin cấu hình qua /admin/pricing (tab Subscriptions).
 * quotaAI/quotaExpert = số lượt user được dùng trong kỳ, hết thì fallback VND hoặc modal hỏi.
 * quotaAI = -1 nghĩa là unlimited (còn cap mềm xem cap ở service layer, mặc định 500/tháng).
 */
@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class SubscriptionPlan {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    Integer id;

    @Column(unique = true)
    String code;

    String name;
    String description;

    int priceVnd;
    int durationDays;

    int quotaAi; // -1 = unlimited (soft cap tại service)
    int quotaExpert; // số lượt chấm giảng viên

    boolean isActive;
}
