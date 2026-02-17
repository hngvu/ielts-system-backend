package io.gsp26se16.moni.payment.service;

import io.gsp26se16.moni.payment.dto.request.ServicePricingRequest;
import io.gsp26se16.moni.payment.dto.response.ServicePricingResponse;

import java.util.List;

public interface ServicePricingService {
    List<ServicePricingResponse> getAllServicePricings();
    List<ServicePricingResponse> getServicePricingsWithFilters(String name, String serviceCode, Integer minCreditCost, Integer maxCreditCost, String sortBy, String sortDir);
    ServicePricingResponse getServicePricingById(Integer id);
    ServicePricingResponse getServicePricingByServiceCode(String serviceCode);
    ServicePricingResponse createServicePricing(ServicePricingRequest request);
    ServicePricingResponse updateServicePricing(Integer id, ServicePricingRequest request);
    void deleteServicePricing(Integer id);
}
