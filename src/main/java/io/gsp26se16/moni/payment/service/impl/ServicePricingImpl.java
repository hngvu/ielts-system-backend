package io.gsp26se16.moni.payment.service.impl;

import io.gsp26se16.moni.payment.dto.request.ServicePricingRequest;
import io.gsp26se16.moni.payment.dto.response.ServicePricingResponse;
import io.gsp26se16.moni.payment.entity.ServicePricing;
import io.gsp26se16.moni.payment.repository.ServicePricingRepository;
import io.gsp26se16.moni.payment.service.ServicePricingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class ServicePricingImpl implements ServicePricingService {

    private final ServicePricingRepository servicePricingRepository;

    @Override
    public List<ServicePricingResponse> getAllServicePricings() {
        log.info("Fetching all service pricings");
        return servicePricingRepository.findAll().stream()
                .map(this::convertToResponse)
                .collect(Collectors.toList());
    }

    @Override
    public ServicePricingResponse getServicePricingById(Integer id) {
        log.info("Fetching service pricing by id: {}", id);
        ServicePricing servicePricing = servicePricingRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Service pricing not found with id: " + id));
        return convertToResponse(servicePricing);
    }

    @Override
    public ServicePricingResponse getServicePricingByServiceCode(String serviceCode) {
        log.info("Fetching service pricing by service code: {}", serviceCode);
        ServicePricing servicePricing = servicePricingRepository.findByServiceCode(serviceCode)
                .orElseThrow(() -> new RuntimeException("Service pricing not found with service code: " + serviceCode));
        return convertToResponse(servicePricing);
    }

    @Override
    public ServicePricingResponse createServicePricing(ServicePricingRequest request) {
        log.info("Creating new service pricing with service code: {}", request.getServiceCode());
        
        if (servicePricingRepository.existsByServiceCode(request.getServiceCode())) {
            throw new RuntimeException("Service pricing already exists with service code: " + request.getServiceCode());
        }

        ServicePricing servicePricing = new ServicePricing();
        servicePricing.setServiceCode(request.getServiceCode());
        servicePricing.setName(request.getName());
        servicePricing.setDescription(request.getDescription());
        servicePricing.setCreditCost(request.getCreditCost());

        ServicePricing savedServicePricing = servicePricingRepository.save(servicePricing);
        log.info("Successfully created service pricing with id: {}", savedServicePricing.getId());
        
        return convertToResponse(savedServicePricing);
    }

    @Override
    public ServicePricingResponse updateServicePricing(Integer id, ServicePricingRequest request) {
        log.info("Updating service pricing with id: {}", id);
        
        ServicePricing servicePricing = servicePricingRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Service pricing not found with id: " + id));

        if (!servicePricing.getServiceCode().equals(request.getServiceCode()) && 
            servicePricingRepository.existsByServiceCode(request.getServiceCode())) {
            throw new RuntimeException("Service pricing already exists with service code: " + request.getServiceCode());
        }

        servicePricing.setServiceCode(request.getServiceCode());
        servicePricing.setName(request.getName());
        servicePricing.setDescription(request.getDescription());
        servicePricing.setCreditCost(request.getCreditCost());

        ServicePricing updatedServicePricing = servicePricingRepository.save(servicePricing);
        log.info("Successfully updated service pricing with id: {}", updatedServicePricing.getId());
        
        return convertToResponse(updatedServicePricing);
    }

    @Override
    public List<ServicePricingResponse> getServicePricingsWithFilters(String name, String serviceCode, Integer minCreditCost, Integer maxCreditCost, String sortBy, String sortDir) {
        log.info("Fetching service pricings with filters: name={}, serviceCode={}, minCreditCost={}, maxCreditCost={}, sortBy={}, sortDir={}", 
                name, serviceCode, minCreditCost, maxCreditCost, sortBy, sortDir);
        
        List<ServicePricingResponse> pricings = servicePricingRepository.findAll().stream()
                .map(this::convertToResponse)
                .toList();
        
        // Apply filters
        if (name != null) {
            pricings = pricings.stream()
                    .filter(p -> p.getName().toLowerCase().contains(name.toLowerCase()))
                    .toList();
        }
        if (serviceCode != null) {
            pricings = pricings.stream()
                    .filter(p -> p.getServiceCode().toLowerCase().contains(serviceCode.toLowerCase()))
                    .toList();
        }
        if (minCreditCost != null) {
            pricings = pricings.stream()
                    .filter(p -> p.getCreditCost() >= minCreditCost)
                    .toList();
        }
        if (maxCreditCost != null) {
            pricings = pricings.stream()
                    .filter(p -> p.getCreditCost() <= maxCreditCost)
                    .toList();
        }
        
        // Apply sorting
        pricings = switch (sortBy.toLowerCase()) {
            case "name" -> sortDir.equalsIgnoreCase("desc") 
                    ? pricings.stream().sorted((a, b) -> b.getName().compareToIgnoreCase(a.getName())).toList()
                    : pricings.stream().sorted((a, b) -> a.getName().compareToIgnoreCase(b.getName())).toList();
            case "servicecode" -> sortDir.equalsIgnoreCase("desc")
                    ? pricings.stream().sorted((a, b) -> b.getServiceCode().compareToIgnoreCase(a.getServiceCode())).toList()
                    : pricings.stream().sorted((a, b) -> a.getServiceCode().compareToIgnoreCase(b.getServiceCode())).toList();
            case "creditcost" -> sortDir.equalsIgnoreCase("desc")
                    ? pricings.stream().sorted((a, b) -> b.getCreditCost().compareTo(a.getCreditCost())).toList()
                    : pricings.stream().sorted((a, b) -> a.getCreditCost().compareTo(b.getCreditCost())).toList();
            default -> sortDir.equalsIgnoreCase("desc")
                    ? pricings.stream().sorted((a, b) -> b.getId().compareTo(a.getId())).toList()
                    : pricings.stream().sorted((a, b) -> a.getId().compareTo(b.getId())).toList();
        };
        
        return pricings;
    }

    @Override
    public void deleteServicePricing(Integer id) {
        log.info("Deleting service pricing with id: {}", id);
        
        if (!servicePricingRepository.existsById(id)) {
            throw new RuntimeException("Service pricing not found with id: " + id);
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
                servicePricing.getCreditCost()
        );
    }
}
