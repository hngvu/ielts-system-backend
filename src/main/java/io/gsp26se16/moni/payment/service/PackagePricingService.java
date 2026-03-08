package io.gsp26se16.moni.payment.service;

import java.util.List;

import io.gsp26se16.moni.payment.dto.request.PackagePricingCreateRequest;
import io.gsp26se16.moni.payment.dto.request.PackagePricingUpdateRequest;
import io.gsp26se16.moni.payment.dto.response.PackagePricingResponse;

public interface PackagePricingService {
    List<PackagePricingResponse> searchPackagePricings(
            String name,
            Integer minPrice,
            Integer maxPrice,
            Integer minCreditAmount,
            Integer maxCreditAmount,
            Boolean isActive,
            String sortBy,
            String sortDir);

    PackagePricingResponse getPackagePricingById(Integer id);

    PackagePricingResponse createPackagePricing(PackagePricingCreateRequest request);

    PackagePricingResponse updatePackagePricing(Integer id, PackagePricingUpdateRequest request);

    void deletePackagePricing(Integer id);
}
