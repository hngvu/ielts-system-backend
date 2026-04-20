package io.gsp26se16.moni.payment.config;

import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import io.gsp26se16.moni.payment.entity.PackagePricing;
import io.gsp26se16.moni.payment.repository.PackagePricingRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Seed 4 gói top-up VND default (có bonus). Sau refactor VND:
 *   price = user trả (VND)
 *   creditAmount = VND nhận vào ví (có thể cao hơn price do bonus)
 *
 * Admin tự chỉnh qua /admin/pricing (tab Top-up Packages). Chỉ tạo mới nếu chưa có.
 */
@Component
@RequiredArgsConstructor
@Slf4j
@Order(4)
public class PackagePricingInitializer implements CommandLineRunner {

    private final PackagePricingRepository repository;

    @Override
    public void run(String... args) {
        seedIfMissing("Nạp 50k", 50_000, 50_000);
        seedIfMissing("Nạp 100k (+10%)", 100_000, 110_000);
        seedIfMissing("Nạp 200k (+15%)", 200_000, 230_000);
        seedIfMissing("Nạp 500k (+20%)", 500_000, 600_000);
    }

    private void seedIfMissing(String name, int priceVnd, int vndReceived) {
        if (repository.existsByName(name)) return;
        PackagePricing pkg = new PackagePricing();
        pkg.setName(name);
        pkg.setPrice(priceVnd);
        pkg.setCreditAmount(vndReceived);
        pkg.setActive(true);
        repository.save(pkg);
        log.info("PackagePricing seeded: {} (pay {}đ → receive {}đ)", name, priceVnd, vndReceived);
    }
}
