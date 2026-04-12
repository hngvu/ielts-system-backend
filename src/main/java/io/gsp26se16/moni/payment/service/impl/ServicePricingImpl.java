package io.gsp26se16.moni.payment.service.impl;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;

import io.gsp26se16.moni.authentication.entity.UserCredentials;
import io.gsp26se16.moni.authentication.repository.UserCredentialsRepository;
import io.gsp26se16.moni.common.exception.AppException;
import io.gsp26se16.moni.common.exception.ErrorCode;
import io.gsp26se16.moni.payment.dto.request.ServicePricingCreateRequest;
import io.gsp26se16.moni.payment.dto.request.ServicePricingUpdateRequest;
import io.gsp26se16.moni.payment.dto.response.ServicePricingResponse;
import io.gsp26se16.moni.payment.entity.ServicePricing;
import io.gsp26se16.moni.payment.enumeration.PaymentType;
import io.gsp26se16.moni.payment.repository.CreditTransactionRepository;
import io.gsp26se16.moni.payment.repository.ServicePricingRepository;
import io.gsp26se16.moni.payment.service.ServicePricingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class ServicePricingImpl implements ServicePricingService {

    private final ServicePricingRepository servicePricingRepository;
    private final UserCredentialsRepository userCredentialsRepository;
    private final CreditTransactionRepository creditTransactionRepository;

    @Override
    public List<ServicePricingResponse> searchServicePricings(
            String name,
            String serviceCode,
            Integer minCreditCost,
            Integer maxCreditCost,
            String sortBy,
            String sortDir) {
        log.info(
                "Searching service pricings with filters: name={}, serviceCode={}, minCreditCost={}, maxCreditCost={}, sortBy={}, sortDir={}",
                name,
                serviceCode,
                minCreditCost,
                maxCreditCost,
                sortBy,
                sortDir);

        List<ServicePricingResponse> pricings = servicePricingRepository.findAll().stream()
                .map(this::convertToResponse)
                .map(this::applyFreeDailyQuotaCheck)
                .toList();

        // Apply filters
        if (name != null) {
            pricings = pricings.stream()
                    .filter(p -> p.name().toLowerCase().contains(name.toLowerCase()))
                    .toList();
        }
        if (serviceCode != null) {
            pricings = pricings.stream()
                    .filter(p -> p.serviceCode().toLowerCase().contains(serviceCode.toLowerCase()))
                    .toList();
        }
        if (minCreditCost != null) {
            pricings = pricings.stream()
                    .filter(p -> p.creditCost() >= minCreditCost)
                    .toList();
        }
        if (maxCreditCost != null) {
            pricings = pricings.stream()
                    .filter(p -> p.creditCost() <= maxCreditCost)
                    .toList();
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
            case "servicecode" -> sortDir.equalsIgnoreCase("desc")
                    ? pricings.stream()
                            .sorted((a, b) -> b.serviceCode().compareToIgnoreCase(a.serviceCode()))
                            .toList()
                    : pricings.stream()
                            .sorted((a, b) -> a.serviceCode().compareToIgnoreCase(b.serviceCode()))
                            .toList();
            case "creditcost" -> sortDir.equalsIgnoreCase("desc")
                    ? pricings.stream()
                            .sorted((a, b) -> b.creditCost().compareTo(a.creditCost()))
                            .toList()
                    : pricings.stream()
                            .sorted((a, b) -> a.creditCost().compareTo(b.creditCost()))
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
    public ServicePricingResponse getServicePricingById(Integer id) {
        log.info("Fetching service pricing by id: {}", id);
        ServicePricing servicePricing = servicePricingRepository
                .findById(id)
                .orElseThrow(() -> new AppException(ErrorCode.SERVICE_PRICING_NOT_FOUND));
        return convertToResponse(servicePricing);
    }

    @Override
    public ServicePricingResponse createServicePricing(ServicePricingCreateRequest request) {
        log.info("Creating new service pricing with service code: {}", request.serviceCode());

        if (servicePricingRepository.existsByServiceCode(request.serviceCode())) {
            throw new RuntimeException("Service pricing already exists with service code: " + request.serviceCode());
        }

        ServicePricing servicePricing = new ServicePricing();
        servicePricing.setServiceCode(request.serviceCode());
        servicePricing.setName(request.name());
        servicePricing.setDescription(request.description());
        servicePricing.setCreditCost(request.creditCost());

        ServicePricing savedServicePricing = servicePricingRepository.save(servicePricing);
        log.info("Successfully created service pricing with id: {}", savedServicePricing.getId());

        return convertToResponse(savedServicePricing);
    }

    @Override
    public ServicePricingResponse updateServicePricing(Integer id, ServicePricingUpdateRequest request) {
        log.info("Updating service pricing with id: {}", id);

        ServicePricing servicePricing = servicePricingRepository
                .findById(id)
                .orElseThrow(() -> new AppException(ErrorCode.SERVICE_PRICING_NOT_FOUND));

        if (!servicePricing.getServiceCode().equals(request.serviceCode())
                && servicePricingRepository.existsByServiceCode(request.serviceCode())) {
            throw new RuntimeException("Service pricing already exists with service code: " + request.serviceCode());
        }

        servicePricing.setServiceCode(request.serviceCode());
        servicePricing.setName(request.name());
        servicePricing.setDescription(request.description());
        servicePricing.setCreditCost(request.creditCost());

        ServicePricing updatedServicePricing = servicePricingRepository.save(servicePricing);
        log.info("Successfully updated service pricing with id: {}", updatedServicePricing.getId());

        return convertToResponse(updatedServicePricing);
    }

    @Override
    public void deleteServicePricing(Integer id) {
        log.info("Deleting service pricing with id: {}", id);

        if (!servicePricingRepository.existsById(id)) {
            throw new AppException(ErrorCode.SERVICE_PRICING_NOT_FOUND);
        }

        servicePricingRepository.deleteById(id);
        log.info("Successfully deleted service pricing with id: {}", id);
    }

    private ServicePricingResponse convertToResponse(ServicePricing servicePricing) {
        return new ServicePricingResponse(
                servicePricing.getId(),
                servicePricing.getServiceCode(),
                servicePricing.getName(),
                servicePricing.getDescription(),
                servicePricing.getCreditCost());
    }

    private ServicePricingResponse applyFreeDailyQuotaCheck(ServicePricingResponse response) {
        if (!"AI_SPEAKING_SCORE".equals(response.serviceCode()) && !"AI_WRITING_SCORE".equals(response.serviceCode())) {
            return response;
        }

        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof Jwt jwt)) {
            return response;
        }

        // Admins viewing the service catalog must see the actual DB credit cost,
        // not the per-user free-daily-quota override.
        boolean isAdmin = authentication.getAuthorities().stream().anyMatch(a -> "ROLE_ADMIN".equals(a.getAuthority()));
        if (isAdmin) {
            return response;
        }

        String credentialId = jwt.getClaim("userId");
        if (credentialId == null) {
            return response;
        }

        UserCredentials credentials =
                userCredentialsRepository.findById(credentialId).orElse(null);
        if (credentials == null || credentials.getUser() == null) {
            return response;
        }

        LocalDateTime startOfDay = LocalDate.now().atStartOfDay();
        LocalDateTime endOfDay = startOfDay.plusDays(1).minusNanos(1);
        long usedToday = creditTransactionRepository.countByUserIdAndServiceCodeAndPaymentTypeAndCreatedAtBetween(
                credentials.getUser().getId(), PaymentType.CONSUME, response.serviceCode(), startOfDay, endOfDay);

        if (usedToday == 0) {
            // Give 1 free turn per day!
            return new ServicePricingResponse(
                    response.id(), response.serviceCode(), response.name(), response.description(), 0 // Free!
                    );
        }

        return response;
    }
}
