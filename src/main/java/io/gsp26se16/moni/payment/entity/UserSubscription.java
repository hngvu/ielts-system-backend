package io.gsp26se16.moni.payment.entity;

import java.time.LocalDateTime;

import jakarta.persistence.*;

import io.gsp26se16.moni.authentication.entity.Users;
import lombok.*;
import lombok.experimental.FieldDefaults;

/**
 * Instance gói đang hoạt động của 1 user. Khi user mua plan → tạo record mới với
 * remainAi/remainExpert = plan.quotaAi/quotaExpert, endAt = now + plan.durationDays.
 *
 * Scheduler expire sẽ set isActive=false khi endAt < now. Không rollover quota (đã chốt).
 * Chấm bài: ưu tiên trừ quota gói trước; hết → modal hỏi user trừ VND.
 */
@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class UserSubscription {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    Integer id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "user_id")
    Users user;

    @ManyToOne
    @JoinColumn(name = "plan_id")
    SubscriptionPlan plan;

    LocalDateTime startAt;
    LocalDateTime endAt;

    int remainAi; // -1 nếu plan unlimited, còn cap mềm ở service layer
    int remainExpert;

    int usedAi; // tổng lượt đã dùng trong kỳ — dùng cho cap mềm khi plan.quotaAi=-1
    int usedExpert;

    boolean isActive;
}
