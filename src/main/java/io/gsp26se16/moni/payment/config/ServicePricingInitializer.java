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
        seedIfAbsent("AI_WRITING_SCORE", "Chấm Writing bằng AI", "Chấm bài Writing Task 1/2 tự động bằng AI", 30);
        seedIfAbsent("AI_SPEAKING_SCORE", "Chấm Speaking bằng AI", "Chấm bài Speaking tự động bằng AI", 30);
        seedIfAbsent(
                "EXPERT_WRITING_SCORE",
                "Chấm Writing với Giảng viên",
                "Chấm bài Writing trực tiếp với giảng viên qua video call",
                100);
        seedIfAbsent(
                "EXPERT_SPEAKING_SCORE",
                "Chấm Speaking với Giảng viên",
                "Chấm bài Speaking trực tiếp với giảng viên qua video call",
                120);
    }

    private void seedIfAbsent(String code, String name, String description, int cost) {
        if (!repository.existsByServiceCode(code)) {
            ServicePricing sp = new ServicePricing();
            sp.setServiceCode(code);
            sp.setName(name);
            sp.setDescription(description);
            sp.setCreditCost(cost);
            repository.save(sp);
            log.info("ServicePricing seeded: {} = {} credits", code, cost);
        }
    }
}
