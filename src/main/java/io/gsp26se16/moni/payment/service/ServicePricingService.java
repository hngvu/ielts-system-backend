package io.gsp26se16.moni.payment.service;

import io.gsp26se16.moni.payment.dto.request.ServicePricingCreateRequest;
import io.gsp26se16.moni.payment.dto.request.ServicePricingUpdateRequest;
import io.gsp26se16.moni.payment.dto.response.ServicePricingResponse;

import java.util.List;

public interface ServicePricingService {
    List<ServicePricingResponse> searchServicePricings(String name, String serviceCode, Integer minCreditCost, Integer maxCreditCost, String sortBy, String sortDir);
    ServicePricingResponse getServicePricingById(Integer id);
    ServicePricingResponse createServicePricing(ServicePricingCreateRequest request);
    ServicePricingResponse updateServicePricing(Integer id, ServicePricingUpdateRequest request);
    void deleteServicePricing(Integer id);
}
