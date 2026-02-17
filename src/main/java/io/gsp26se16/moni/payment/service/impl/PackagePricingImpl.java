package io.gsp26se16.moni.payment.service.impl;

import io.gsp26se16.moni.payment.dto.request.PackagePricingRequest;
import io.gsp26se16.moni.payment.dto.response.PackagePricingResponse;
import io.gsp26se16.moni.payment.entity.PackagePricing;
import io.gsp26se16.moni.payment.repository.PackagePricingRepository;
import io.gsp26se16.moni.payment.service.PackagePricingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class PackagePricingImpl implements PackagePricingService {

    private final PackagePricingRepository packagePricingRepository;

    @Override
    public List<PackagePricingResponse> getAllPackagePricings() {
        log.info("Fetching all package pricings");
        return packagePricingRepository.findAll().stream()
                .map(this::convertToResponse)
                .collect(Collectors.toList());
    }

    @Override
    public List<PackagePricingResponse> getActivePackagePricings() {
        log.info("Fetching active package pricings");
        return packagePricingRepository.findByIsActive(true).stream()
                .map(this::convertToResponse)
                .collect(Collectors.toList());
    }

    @Override
    public PackagePricingResponse getPackagePricingById(Integer id) {
        log.info("Fetching package pricing by id: {}", id);
        PackagePricing packagePricing = packagePricingRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Package pricing not found with id: " + id));
        return convertToResponse(packagePricing);
    }

    @Override
    public PackagePricingResponse createPackagePricing(PackagePricingRequest request) {
        log.info("Creating new package pricing with name: {}", request.getName());
        
        if (packagePricingRepository.existsByName(request.getName())) {
            throw new RuntimeException("Package pricing already exists with name: " + request.getName());
        }

        PackagePricing packagePricing = new PackagePricing();
        packagePricing.setId(null); // Let database generate the ID
        packagePricing.setName(request.getName());
        packagePricing.setPrice(request.getPrice());
        packagePricing.setCreditAmount(request.getCreditAmount());
        packagePricing.setActive(request.getIsActive() != null ? request.getIsActive() : true);

        PackagePricing savedPackagePricing = packagePricingRepository.save(packagePricing);
        log.info("Successfully created package pricing with id: {}", savedPackagePricing.getId());
        
        return convertToResponse(savedPackagePricing);
    }

    @Override
    public PackagePricingResponse updatePackagePricing(Integer id, PackagePricingRequest request) {
        log.info("Updating package pricing with id: {}", id);
        
        PackagePricing packagePricing = packagePricingRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Package pricing not found with id: " + id));

        if (!packagePricing.getName().equals(request.getName()) && 
            packagePricingRepository.existsByName(request.getName())) {
            throw new RuntimeException("Package pricing already exists with name: " + request.getName());
        }

        packagePricing.setName(request.getName());
        packagePricing.setPrice(request.getPrice());
        packagePricing.setCreditAmount(request.getCreditAmount());
        if (request.getIsActive() != null) {
            packagePricing.setActive(request.getIsActive());
        }

        PackagePricing updatedPackagePricing = packagePricingRepository.save(packagePricing);
        log.info("Successfully updated package pricing with id: {}", updatedPackagePricing.getId());
        
        return convertToResponse(updatedPackagePricing);
    }

    @Override
    public void deletePackagePricing(Integer id) {
        log.info("Deleting package pricing with id: {}", id);
        
        if (!packagePricingRepository.existsById(id)) {
            throw new RuntimeException("Package pricing not found with id: " + id);
        }
        
        packagePricingRepository.deleteById(id);
        log.info("Successfully deleted package pricing with id: {}", id);
    }

    @Override
    public List<PackagePricingResponse> getPackagePricingsWithFilters(String name, Integer minPrice, Integer maxPrice, Integer minCreditAmount, Integer maxCreditAmount, Boolean isActive, String sortBy, String sortDir) {
        log.info("Fetching package pricings with filters: name={}, minPrice={}, maxPrice={}, minCreditAmount={}, maxCreditAmount={}, isActive={}, sortBy={}, sortDir={}", 
                name, minPrice, maxPrice, minCreditAmount, maxCreditAmount, isActive, sortBy, sortDir);
        
        List<PackagePricingResponse> pricings = packagePricingRepository.findAll().stream()
                .map(this::convertToResponse)
                .toList();
        
        // Apply filters
        if (name != null) {
            pricings = pricings.stream()
                    .filter(p -> p.getName().toLowerCase().contains(name.toLowerCase()))
                    .toList();
        }
        if (minPrice != null) {
            pricings = pricings.stream()
                    .filter(p -> p.getPrice() >= minPrice)
                    .toList();
        }
        if (maxPrice != null) {
            pricings = pricings.stream()
                    .filter(p -> p.getPrice() <= maxPrice)
                    .toList();
        }
        if (minCreditAmount != null) {
            pricings = pricings.stream()
                    .filter(p -> p.getCreditAmount() >= minCreditAmount)
                    .toList();
        }
        if (maxCreditAmount != null) {
            pricings = pricings.stream()
                    .filter(p -> p.getCreditAmount() <= maxCreditAmount)
                    .toList();
        }
        if (isActive != null) {
            pricings = pricings.stream()
                    .filter(p -> p.getIsActive().equals(isActive))
                    .toList();
        }
        
        // Apply sorting
        pricings = switch (sortBy.toLowerCase()) {
            case "name" -> sortDir.equalsIgnoreCase("desc") 
                    ? pricings.stream().sorted((a, b) -> b.getName().compareToIgnoreCase(a.getName())).toList()
                    : pricings.stream().sorted((a, b) -> a.getName().compareToIgnoreCase(b.getName())).toList();
            case "price" -> sortDir.equalsIgnoreCase("desc")
                    ? pricings.stream().sorted((a, b) -> b.getPrice().compareTo(a.getPrice())).toList()
                    : pricings.stream().sorted((a, b) -> a.getPrice().compareTo(b.getPrice())).toList();
            case "creditamount" -> sortDir.equalsIgnoreCase("desc")
                    ? pricings.stream().sorted((a, b) -> b.getCreditAmount().compareTo(a.getCreditAmount())).toList()
                    : pricings.stream().sorted((a, b) -> a.getCreditAmount().compareTo(b.getCreditAmount())).toList();
            case "isactive" -> sortDir.equalsIgnoreCase("desc")
                    ? pricings.stream().sorted((a, b) -> b.getIsActive().compareTo(a.getIsActive())).toList()
                    : pricings.stream().sorted((a, b) -> a.getIsActive().compareTo(b.getIsActive())).toList();
            default -> sortDir.equalsIgnoreCase("desc")
                    ? pricings.stream().sorted((a, b) -> b.getId().compareTo(a.getId())).toList()
                    : pricings.stream().sorted((a, b) -> a.getId().compareTo(b.getId())).toList();
        };
        
        return pricings;
    }

    @Override
    public PackagePricingResponse togglePackageStatus(Integer id) {
        log.info("Toggling status for package pricing with id: {}", id);
        
        PackagePricing packagePricing = packagePricingRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Package pricing not found with id: " + id));

        packagePricing.setActive(!packagePricing.isActive());
        
        PackagePricing updatedPackagePricing = packagePricingRepository.save(packagePricing);
        log.info("Successfully toggled status for package pricing with id: {}, new status: {}", 
                updatedPackagePricing.getId(), updatedPackagePricing.isActive());
        
        return convertToResponse(updatedPackagePricing);
    }

    private PackagePricingResponse convertToResponse(PackagePricing packagePricing) {
        return new PackagePricingResponse(
                packagePricing.getId(),
                packagePricing.getName(),
                packagePricing.getPrice(),
                packagePricing.getCreditAmount(),
                packagePricing.isActive()
        );
    }
}
