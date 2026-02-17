package io.gsp26se16.moni.payment.repository;

import io.gsp26se16.moni.payment.entity.PackagePricing;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PackagePricingRepository extends JpaRepository<PackagePricing, Integer> {
    List<PackagePricing> findByIsActive(boolean isActive);
    Optional<PackagePricing> findByName(String name);
    boolean existsByName(String name);
}
