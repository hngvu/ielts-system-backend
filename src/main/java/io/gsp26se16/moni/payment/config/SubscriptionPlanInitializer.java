package io.gsp26se16.moni.payment.config;

import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import io.gsp26se16.moni.payment.entity.SubscriptionPlan;
import io.gsp26se16.moni.payment.repository.SubscriptionPlanRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Seed 3 gói subscription default. Admin tự chỉnh qua /admin/pricing (tab Subscriptions).
 * Chỉ tạo mới nếu code chưa có. Không ghi đè giá trị admin đã tinh chỉnh.
 */
@Component
@RequiredArgsConstructor
@Slf4j
@Order(3)
public class SubscriptionPlanInitializer implements CommandLineRunner {

    private final SubscriptionPlanRepository repository;

    @Override
    public void run(String... args) {
        seedIfMissing(
                "STARTER", "Gói Starter", "30 lượt chấm AI/tháng — phù hợp người mới luyện tập", 49_000, 30, 30, 0);
        seedIfMissing(
                "STANDARD",
                "Gói Standard",
                "80 lượt AI + 2 lượt Giảng viên/tháng — cho người ôn nghiêm túc",
                99_000,
                30,
                80,
                2);
        seedIfMissing(
                "PRO", "Gói Pro", "AI không giới hạn + 5 lượt Giảng viên/tháng — cho power user", 199_000, 30, -1, 5);
    }

    private void seedIfMissing(
            String code,
            String name,
            String description,
            int priceVnd,
            int durationDays,
            int quotaAi,
            int quotaExpert) {
        if (repository.existsByCode(code)) return;
        SubscriptionPlan plan = new SubscriptionPlan();
        plan.setCode(code);
        plan.setName(name);
        plan.setDescription(description);
        plan.setPriceVnd(priceVnd);
        plan.setDurationDays(durationDays);
        plan.setQuotaAi(quotaAi);
        plan.setQuotaExpert(quotaExpert);
        plan.setActive(true);
        repository.save(plan);
        log.info(
                "SubscriptionPlan seeded: {} = {}đ/{}days (AI={}, Expert={})",
                code,
                priceVnd,
                durationDays,
                quotaAi,
                quotaExpert);
    }
}
