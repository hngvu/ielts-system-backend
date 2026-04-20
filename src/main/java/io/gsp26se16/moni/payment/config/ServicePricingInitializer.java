package io.gsp26se16.moni.payment.config;

import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import io.gsp26se16.moni.payment.entity.ServicePricing;
import io.gsp26se16.moni.payment.repository.ServicePricingRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Component
@RequiredArgsConstructor
@Slf4j
@Order(2)
public class ServicePricingInitializer implements CommandLineRunner {

    private final ServicePricingRepository repository;

    @Override
    public void run(String... args) {
        // Default prices in VND. Sau refactor VND, creditCost field giờ mang nghĩa VND cost.
        // Admin tự chỉnh qua /admin/pricing (tab Services).
        seedOrHeal("AI_WRITING_SCORE", "Chấm Writing bằng AI", "Chấm bài Writing Task 1/2 tự động bằng AI", 2_000);
        seedOrHeal("AI_SPEAKING_SCORE", "Chấm Speaking bằng AI", "Chấm bài Speaking tự động bằng AI", 3_000);
        seedOrHeal(
                "EXPERT_WRITING_SCORE",
                "Chấm Writing với Giảng viên",
                "Chấm bài Writing trực tiếp với giảng viên qua video call",
                30_000);
        seedOrHeal(
                "EXPERT_SPEAKING_SCORE",
                "Chấm Speaking với Giảng viên",
                "Chấm bài Speaking trực tiếp với giảng viên qua video call",
                50_000);
    }

    /**
     * Tạo mới nếu chưa có. Nếu đã có nhưng creditCost ≤ 0 (data bị lỗi), tự heal về default.
     * Không ghi đè creditCost khi giá trị đã > 0 để tránh mất tinh chỉnh của admin.
     */
    private void seedOrHeal(String code, String name, String description, int cost) {
        repository
                .findByServiceCode(code)
                .ifPresentOrElse(
                        existing -> {
                            if (existing.getCreditCost() <= 0) {
                                existing.setName(name);
                                existing.setDescription(description);
                                existing.setCreditCost(cost);
                                repository.save(existing);
                                log.info("ServicePricing healed: {} reset to {} credits", code, cost);
                            }
                        },
                        () -> {
                            ServicePricing sp = new ServicePricing();
                            sp.setServiceCode(code);
                            sp.setName(name);
                            sp.setDescription(description);
                            sp.setCreditCost(cost);
                            repository.save(sp);
                            log.info("ServicePricing seeded: {} = {} credits", code, cost);
                        });
    }
}
