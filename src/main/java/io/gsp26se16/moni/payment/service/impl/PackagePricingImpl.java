package io.gsp26se16.moni.payment.service.impl;

import java.util.List;

import org.springframework.stereotype.Service;

import io.gsp26se16.moni.common.exception.AppException;
import io.gsp26se16.moni.common.exception.ErrorCode;
import io.gsp26se16.moni.payment.dto.request.PackagePricingCreateRequest;
import io.gsp26se16.moni.payment.dto.request.PackagePricingUpdateRequest;
import io.gsp26se16.moni.payment.dto.response.PackagePricingResponse;
import io.gsp26se16.moni.payment.entity.PackagePricing;
import io.gsp26se16.moni.payment.repository.PackagePricingRepository;
import io.gsp26se16.moni.payment.repository.PaymentRepository;
import io.gsp26se16.moni.payment.service.PackagePricingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class PackagePricingImpl implements PackagePricingService {

    private final PackagePricingRepository packagePricingRepository;
    private final PaymentRepository paymentRepository;

    @Override
    public List<PackagePricingResponse> searchPackagePricings(
            String name,
            Integer minPrice,
            Integer maxPrice,
            Integer minCreditAmount,
            Integer maxCreditAmount,
            Boolean isActive,
            String sortBy,
            String sortDir) {
        log.info(
                "Searching package pricings with filters: name={}, minPrice={}, maxPrice={}, minCreditAmount={}, maxCreditAmount={}, isActive={}, sortBy={}, sortDir={}",
                name,
                minPrice,
                maxPrice,
                minCreditAmount,
                maxCreditAmount,
                isActive,
                sortBy,
                sortDir);

        List<PackagePricingResponse> pricings = packagePricingRepository.findAll().stream()
                .map(this::convertToResponse)
                .toList();

        // Apply filters
        if (name != null) {
            pricings = pricings.stream()
                    .filter(p -> p.name().toLowerCase().contains(name.toLowerCase()))
                    .toList();
        }
        if (minPrice != null) {
            pricings = pricings.stream().filter(p -> p.price() >= minPrice).toList();
        }
        if (maxPrice != null) {
            pricings = pricings.stream().filter(p -> p.price() <= maxPrice).toList();
        }
        if (minCreditAmount != null) {
            pricings = pricings.stream()
                    .filter(p -> p.creditAmount() >= minCreditAmount)
                    .toList();
        }
        if (maxCreditAmount != null) {
            pricings = pricings.stream()
                    .filter(p -> p.creditAmount() <= maxCreditAmount)
                    .toList();
        }
        if (isActive != null) {
            pricings =
                    pricings.stream().filter(p -> p.isActive().equals(isActive)).toList();
        }

        // Apply sorting
        pricings = switch (sortBy.toLowerCase()) {
            case "name" -> sortDir.equalsIgnoreCase("desc")
                    ? pricings.stream()
                            .sorted((a, b) -> b.name().compareToIgnoreCase(a.name()))
                            .toList()
                    : pricings.stream()
                            .sorted((a, b) -> a.name().compareToIgnoreCase(b.name()))
                            .toList();
            case "price" -> sortDir.equalsIgnoreCase("desc")
                    ? pricings.stream()
                            .sorted((a, b) -> b.price().compareTo(a.price()))
                            .toList()
                    : pricings.stream()
                            .sorted((a, b) -> a.price().compareTo(b.price()))
                            .toList();
            case "creditamount" -> sortDir.equalsIgnoreCase("desc")
                    ? pricings.stream()
                            .sorted((a, b) -> b.creditAmount().compareTo(a.creditAmount()))
                            .toList()
                    : pricings.stream()
                            .sorted((a, b) -> a.creditAmount().compareTo(b.creditAmount()))
                            .toList();
            case "isactive" -> sortDir.equalsIgnoreCase("desc")
                    ? pricings.stream()
                            .sorted((a, b) -> b.isActive().compareTo(a.isActive()))
                            .toList()
                    : pricings.stream()
                            .sorted((a, b) -> a.isActive().compareTo(b.isActive()))
                            .toList();
            default -> sortDir.equalsIgnoreCase("desc")
                    ? pricings.stream()
                            .sorted((a, b) -> b.id().compareTo(a.id()))
                            .toList()
                    : pricings.stream()
                            .sorted((a, b) -> a.id().compareTo(b.id()))
                            .toList();};

        return pricings;
    }

    @Override
    public PackagePricingResponse getPackagePricingById(Integer id) {
        log.info("Fetching package pricing by id: {}", id);
        PackagePricing packagePricing = packagePricingRepository
                .findById(id)
                .orElseThrow(() -> new AppException(ErrorCode.PACKAGE_PRICING_NOT_FOUND));
        return convertToResponse(packagePricing);
    }

    @Override
    public PackagePricingResponse createPackagePricing(PackagePricingCreateRequest request) {
        log.info("Creating new package pricing with name: {}", request.name());

        if (packagePricingRepository.existsByName(request.name())) {
            throw new RuntimeException("Package pricing already exists with name: " + request.name());
        }

        PackagePricing packagePricing = new PackagePricing();
        packagePricing.setId(null); // Let database generate the ID
        packagePricing.setName(request.name());
        packagePricing.setPrice(request.price());
        packagePricing.setCreditAmount(request.creditAmount());
        packagePricing.setActive(true); // Default to active

        PackagePricing savedPackagePricing = packagePricingRepository.save(packagePricing);
        log.info("Successfully created package pricing with id: {}", savedPackagePricing.getId());

        return convertToResponse(savedPackagePricing);
    }

    @Override
    public PackagePricingResponse updatePackagePricing(Integer id, PackagePricingUpdateRequest request) {
        log.info("Updating package pricing with id: {}", id);

        PackagePricing packagePricing = packagePricingRepository
                .findById(id)
                .orElseThrow(() -> new AppException(ErrorCode.PACKAGE_PRICING_NOT_FOUND));

        if (!packagePricing.getName().equals(request.name()) && packagePricingRepository.existsByName(request.name())) {
            throw new RuntimeException("Package pricing already exists with name: " + request.name());
        }

        packagePricing.setName(request.name());
        packagePricing.setPrice(request.price());
        packagePricing.setCreditAmount(request.creditAmount());
        if (request.isActive() != null) {
            packagePricing.setActive(request.isActive());
        }

        PackagePricing updatedPackagePricing = packagePricingRepository.save(packagePricing);
        log.info("Successfully updated package pricing with id: {}", updatedPackagePricing.getId());

        return convertToResponse(updatedPackagePricing);
    }

    @Override
    public void deletePackagePricing(Integer id) {
        log.info("Deleting package pricing with id: {}", id);

        PackagePricing packagePricing = packagePricingRepository
                .findById(id)
                .orElseThrow(() -> new AppException(ErrorCode.PACKAGE_PRICING_NOT_FOUND));

        // Gói đã có payment tham chiếu (FK) → không hard delete được → soft delete (isActive=false)
        // để giữ lịch sử giao dịch. Trang user /payment đã filter isActive=true nên tự ẩn.
        if (paymentRepository.existsByPackagePricing_Id(id)) {
            packagePricing.setActive(false);
            packagePricingRepository.save(packagePricing);
            log.info("Soft-deleted package pricing id={} (has linked payments)", id);
            return;
        }

        packagePricingRepository.deleteById(id);
        log.info("Hard-deleted package pricing id={}", id);
    }

    private PackagePricingResponse convertToResponse(PackagePricing packagePricing) {
        return new PackagePricingResponse(
                packagePricing.getId(),
                packagePricing.getName(),
                packagePricing.getPrice(),
                packagePricing.getCreditAmount(),
                packagePricing.isActive());
    }
}
