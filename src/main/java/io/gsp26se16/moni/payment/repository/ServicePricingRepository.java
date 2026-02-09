package io.gsp26se16.moni.payment.repository;

import io.gsp26se16.moni.payment.entity.ServicePricing;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ServicePricingRepository extends JpaRepository<ServicePricing, Integer> {
}
