package io.gsp26se16.moni.payment.controller;

import java.util.List;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import io.gsp26se16.moni.payment.dto.request.PackagePricingCreateRequest;
import io.gsp26se16.moni.payment.dto.request.PackagePricingUpdateRequest;
import io.gsp26se16.moni.payment.dto.response.PackagePricingResponse;
import io.gsp26se16.moni.payment.service.PackagePricingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@RestController
@RequestMapping("/packages")
@RequiredArgsConstructor
@Slf4j
public class PackagePricingController {

    private final PackagePricingService packagePricingService;

    @GetMapping
    public ResponseEntity<List<PackagePricingResponse>> searchPackagePricings(
            @RequestParam(required = false) String name,
            @RequestParam(required = false) Integer minPrice,
            @RequestParam(required = false) Integer maxPrice,
            @RequestParam(required = false) Integer minCreditAmount,
            @RequestParam(required = false) Integer maxCreditAmount,
            @RequestParam(required = false) Boolean isActive,
            @RequestParam(defaultValue = "id") String sortBy,
            @RequestParam(defaultValue = "asc") String sortDir) {
        log.info("GET /packages - Searching package pricings with filters");
        List<PackagePricingResponse> pricings = packagePricingService.searchPackagePricings(
                name, minPrice, maxPrice, minCreditAmount, maxCreditAmount, isActive, sortBy, sortDir);
        return ResponseEntity.ok(pricings);
    }

    @GetMapping("/{id}")
    public ResponseEntity<PackagePricingResponse> getPackagePricingById(@PathVariable Integer id) {
        log.info("GET /packages/{} - Fetching package pricing", id);
        PackagePricingResponse pricing = packagePricingService.getPackagePricingById(id);
        return ResponseEntity.ok(pricing);
    }

    @PostMapping
    public ResponseEntity<PackagePricingResponse> createPackagePricing(
            @Valid @RequestBody PackagePricingCreateRequest request) {
        log.info("POST /packages - Creating new package pricing");
        PackagePricingResponse pricing = packagePricingService.createPackagePricing(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(pricing);
    }

    @PutMapping("/{id}")
    public ResponseEntity<PackagePricingResponse> updatePackagePricing(
            @PathVariable Integer id, @Valid @RequestBody PackagePricingUpdateRequest request) {
        log.info("PUT /packages/{} - Updating package pricing", id);
        PackagePricingResponse pricing = packagePricingService.updatePackagePricing(id, request);
        return ResponseEntity.ok(pricing);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deletePackagePricing(@PathVariable Integer id) {
        log.info("DELETE /packages/{} - Deleting package pricing", id);
        packagePricingService.deletePackagePricing(id);
        return ResponseEntity.noContent().build();
    }
}
