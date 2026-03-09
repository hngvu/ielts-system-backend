package io.gsp26se16.moni.payment.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import io.gsp26se16.moni.payment.entity.ServicePricing;

@Repository
public interface ServicePricingRepository extends JpaRepository<ServicePricing, Integer> {
    Optional<ServicePricing> findByServiceCode(String serviceCode);

    boolean existsByServiceCode(String serviceCode);
}
