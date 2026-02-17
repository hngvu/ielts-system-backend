package io.gsp26se16.moni.payment.service;

import io.gsp26se16.moni.payment.dto.request.PackagePricingCreateRequest;
import io.gsp26se16.moni.payment.dto.request.PackagePricingUpdateRequest;
import io.gsp26se16.moni.payment.dto.response.PackagePricingResponse;

import java.util.List;

public interface PackagePricingService {
    List<PackagePricingResponse> searchPackagePricings(String name, Integer minPrice, Integer maxPrice, Integer minCreditAmount, Integer maxCreditAmount, Boolean isActive, String sortBy, String sortDir);
    PackagePricingResponse getPackagePricingById(Integer id);
    PackagePricingResponse createPackagePricing(PackagePricingCreateRequest request);
    PackagePricingResponse updatePackagePricing(Integer id, PackagePricingUpdateRequest request);
    void deletePackagePricing(Integer id);
}
