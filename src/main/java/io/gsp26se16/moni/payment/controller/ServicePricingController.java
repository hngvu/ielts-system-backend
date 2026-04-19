package io.gsp26se16.moni.payment.controller;

import java.util.List;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import io.gsp26se16.moni.payment.dto.request.ServicePricingCreateRequest;
import io.gsp26se16.moni.payment.dto.request.ServicePricingUpdateRequest;
import io.gsp26se16.moni.payment.dto.response.ServicePricingResponse;
import io.gsp26se16.moni.payment.service.ServicePricingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@RestController
@RequestMapping("/services")
@RequiredArgsConstructor
@Slf4j
public class ServicePricingController {

    private final ServicePricingService servicePricingService;

    @GetMapping
    public ResponseEntity<List<ServicePricingResponse>> searchServicePricings(
            @RequestParam(required = false) String name,
            @RequestParam(required = false) String serviceCode,
            @RequestParam(required = false) Integer minCreditCost,
            @RequestParam(required = false) Integer maxCreditCost,
            @RequestParam(defaultValue = "id") String sortBy,
            @RequestParam(defaultValue = "asc") String sortDir) {
        log.info("GET /services - Searching service pricings with filters");
        List<ServicePricingResponse> pricings = servicePricingService.searchServicePricings(
                name, serviceCode, minCreditCost, maxCreditCost, sortBy, sortDir);
        return ResponseEntity.ok(pricings);
    }

    @GetMapping("/{id}")
    public ResponseEntity<ServicePricingResponse> getServicePricingById(@PathVariable Integer id) {
        log.info("GET /services/{} - Fetching service pricing", id);
        ServicePricingResponse pricing = servicePricingService.getServicePricingById(id);
        return ResponseEntity.ok(pricing);
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ServicePricingResponse> createServicePricing(
            @Valid @RequestBody ServicePricingCreateRequest request) {
        log.info("POST /services - Creating new service pricing");
        ServicePricingResponse pricing = servicePricingService.createServicePricing(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(pricing);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ServicePricingResponse> updateServicePricing(
            @PathVariable Integer id, @Valid @RequestBody ServicePricingUpdateRequest request) {
        log.info("PUT /services/{} - Updating service pricing", id);
        ServicePricingResponse pricing = servicePricingService.updateServicePricing(id, request);
        return ResponseEntity.ok(pricing);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> deleteServicePricing(@PathVariable Integer id) {
        log.info("DELETE /services/{} - Deleting service pricing", id);
        servicePricingService.deleteServicePricing(id);
        return ResponseEntity.noContent().build();
    }
}
