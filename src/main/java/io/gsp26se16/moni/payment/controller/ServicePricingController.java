package io.gsp26se16.moni.payment.controller;

import io.gsp26se16.moni.payment.dto.request.ServicePricingRequest;
import io.gsp26se16.moni.payment.dto.response.ServicePricingResponse;
import io.gsp26se16.moni.payment.service.ServicePricingService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/services")
@RequiredArgsConstructor
@Slf4j
public class ServicePricingController {

    private final ServicePricingService servicePricingService;

    @GetMapping
    public ResponseEntity<List<ServicePricingResponse>> getAllServicePricings(
            @RequestParam(required = false) String name,
            @RequestParam(required = false) String serviceCode,
            @RequestParam(required = false) Integer minCreditCost,
            @RequestParam(required = false) Integer maxCreditCost,
            @RequestParam(defaultValue = "id") String sortBy,
            @RequestParam(defaultValue = "asc") String sortDir) {
        log.info("GET /services - Fetching service pricings with filters: name={}, serviceCode={}, minCreditCost={}, maxCreditCost={}, sortBy={}, sortDir={}", 
                name, serviceCode, minCreditCost, maxCreditCost, sortBy, sortDir);
        
        List<ServicePricingResponse> pricings = servicePricingService.getServicePricingsWithFilters(
                name, serviceCode, minCreditCost, maxCreditCost, sortBy, sortDir);
        
        return ResponseEntity.ok(pricings);
    }

    @GetMapping("/{id}")
    public ResponseEntity<ServicePricingResponse> getServicePricingById(@PathVariable Integer id) {
        log.info("GET /api/v1/service-pricings/{} - Fetching service pricing", id);
        ServicePricingResponse pricing = servicePricingService.getServicePricingById(id);
        return ResponseEntity.ok(pricing);
    }

    @GetMapping("/code/{serviceCode}")
    public ResponseEntity<ServicePricingResponse> getServicePricingByServiceCode(@PathVariable String serviceCode) {
        log.info("GET /api/v1/service-pricings/service-code/{} - Fetching service pricing by code", serviceCode);
        ServicePricingResponse pricing = servicePricingService.getServicePricingByServiceCode(serviceCode);
        return ResponseEntity.ok(pricing);
    }

    @PostMapping
    public ResponseEntity<ServicePricingResponse> createServicePricing(@Valid @RequestBody ServicePricingRequest request) {
        log.info("POST /api/v1/service-pricings - Creating new service pricing with code: {}", request.getServiceCode());
        ServicePricingResponse pricing = servicePricingService.createServicePricing(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(pricing);
    }

    @PutMapping("/{id}")
    public ResponseEntity<ServicePricingResponse> updateServicePricing(
            @PathVariable Integer id,
            @Valid @RequestBody ServicePricingRequest request) {
        log.info("PUT /api/v1/service-pricings/{} - Updating service pricing", id);
        ServicePricingResponse pricing = servicePricingService.updateServicePricing(id, request);
        return ResponseEntity.ok(pricing);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteServicePricing(@PathVariable Integer id) {
        log.info("DELETE /api/v1/service-pricings/{} - Deleting service pricing", id);
        servicePricingService.deleteServicePricing(id);
        return ResponseEntity.noContent().build();
    }
}
