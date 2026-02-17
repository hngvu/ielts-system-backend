package io.gsp26se16.moni.payment.service;

import io.gsp26se16.moni.payment.dto.request.PackagePricingRequest;
import io.gsp26se16.moni.payment.dto.response.PackagePricingResponse;

import java.util.List;

public interface PackagePricingService {
    List<PackagePricingResponse> getAllPackagePricings();
    List<PackagePricingResponse> getPackagePricingsWithFilters(String name, Integer minPrice, Integer maxPrice, Integer minCreditAmount, Integer maxCreditAmount, Boolean isActive, String sortBy, String sortDir);
    List<PackagePricingResponse> getActivePackagePricings();
    PackagePricingResponse getPackagePricingById(Integer id);
    PackagePricingResponse createPackagePricing(PackagePricingRequest request);
    PackagePricingResponse updatePackagePricing(Integer id, PackagePricingRequest request);
    void deletePackagePricing(Integer id);
    PackagePricingResponse togglePackageStatus(Integer id);
}
