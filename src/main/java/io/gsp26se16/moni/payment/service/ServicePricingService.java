package io.gsp26se16.moni.payment.service;

import java.util.List;

import io.gsp26se16.moni.payment.dto.request.ServicePricingCreateRequest;
import io.gsp26se16.moni.payment.dto.request.ServicePricingUpdateRequest;
import io.gsp26se16.moni.payment.dto.response.ServicePricingResponse;
import io.gsp26se16.moni.payment.dto.response.ServiceQuotaResponse;

public interface ServicePricingService {
    List<ServicePricingResponse> searchServicePricings(
            String name,
            String serviceCode,
            Integer minCreditCost,
            Integer maxCreditCost,
            String sortBy,
            String sortDir);

    ServicePricingResponse getServicePricingById(Integer id);

    ServicePricingResponse createServicePricing(ServicePricingCreateRequest request);

    ServicePricingResponse updateServicePricing(Integer id, ServicePricingUpdateRequest request);

    void deleteServicePricing(Integer id);

    /**
     * Trả về trạng thái quota miễn phí 1 lượt/ngày cho user hiện tại + service chỉ định.
     * Dùng cho trang practice để hiển thị "Miễn phí" thay vì giá đậu khi user chưa dùng trong ngày.
     */
    ServiceQuotaResponse getUserServiceQuota(String serviceCode);
}
