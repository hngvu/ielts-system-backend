package io.gsp26se16.moni.payment.controller;

import io.gsp26se16.moni.payment.dto.request.PackagePricingRequest;
import io.gsp26se16.moni.payment.dto.response.PackagePricingResponse;
import io.gsp26se16.moni.payment.service.PackagePricingService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/packages")
@RequiredArgsConstructor
@Slf4j
public class PackagePricingController {

    private final PackagePricingService packagePricingService;

    @GetMapping
    public ResponseEntity<List<PackagePricingResponse>> getAllPackagePricings(
            @RequestParam(required = false) String name,
            @RequestParam(required = false) Integer minPrice,
            @RequestParam(required = false) Integer maxPrice,
            @RequestParam(required = false) Integer minCreditAmount,
            @RequestParam(required = false) Integer maxCreditAmount,
            @RequestParam(required = false) Boolean isActive,
            @RequestParam(defaultValue = "id") String sortBy,
            @RequestParam(defaultValue = "asc") String sortDir) {
        log.info("GET /packages - Fetching package pricings with filters: name={}, minPrice={}, maxPrice={}, minCreditAmount={}, maxCreditAmount={}, isActive={}, sortBy={}, sortDir={}", 
                name, minPrice, maxPrice, minCreditAmount, maxCreditAmount, isActive, sortBy, sortDir);
        
        List<PackagePricingResponse> pricings = packagePricingService.getPackagePricingsWithFilters(
                name, minPrice, maxPrice, minCreditAmount, maxCreditAmount, isActive, sortBy, sortDir);
        
        return ResponseEntity.ok(pricings);
    }

    @GetMapping("/active")
    public ResponseEntity<List<PackagePricingResponse>> getActivePackagePricings() {
        log.info("GET /api/v1/package-pricings/active - Fetching active package pricings");
        List<PackagePricingResponse> pricings = packagePricingService.getActivePackagePricings();
        return ResponseEntity.ok(pricings);
    }

    @GetMapping("/{id}")
    public ResponseEntity<PackagePricingResponse> getPackagePricingById(@PathVariable Integer id) {
        log.info("GET /api/v1/package-pricings/{} - Fetching package pricing", id);
        PackagePricingResponse pricing = packagePricingService.getPackagePricingById(id);
        return ResponseEntity.ok(pricing);
    }

    @PostMapping
    public ResponseEntity<PackagePricingResponse> createPackagePricing(@Valid @RequestBody PackagePricingRequest request) {
        log.info("POST /api/v1/package-pricings - Creating new package pricing with name: {}", request.getName());
        PackagePricingResponse pricing = packagePricingService.createPackagePricing(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(pricing);
    }

    @PutMapping("/{id}")
    public ResponseEntity<PackagePricingResponse> updatePackagePricing(
            @PathVariable Integer id,
            @Valid @RequestBody PackagePricingRequest request) {
        log.info("PUT /api/v1/package-pricings/{} - Updating package pricing", id);
        PackagePricingResponse pricing = packagePricingService.updatePackagePricing(id, request);
        return ResponseEntity.ok(pricing);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deletePackagePricing(@PathVariable Integer id) {
        log.info("DELETE /api/v1/package-pricings/{} - Deleting package pricing", id);
        packagePricingService.deletePackagePricing(id);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/{id}/toggle-status")
    public ResponseEntity<PackagePricingResponse> togglePackageStatus(@PathVariable Integer id) {
        log.info("PATCH /api/v1/package-pricings/{}/toggle-status - Toggling package status", id);
        PackagePricingResponse pricing = packagePricingService.togglePackageStatus(id);
        return ResponseEntity.ok(pricing);
    }
}
