package io.gsp26se16.moni.payment.controller;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import io.gsp26se16.moni.common.dto.ApiResponse;
import io.gsp26se16.moni.payment.dto.response.AdminRevenueDashboardResponse;
import io.gsp26se16.moni.payment.service.AdminDashboardService;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/admin/dashboard")
@RequiredArgsConstructor
public class AdminDashboardController {

    private final AdminDashboardService adminDashboardService;

    @GetMapping("/revenue")
    public ResponseEntity<ApiResponse<AdminRevenueDashboardResponse>> getRevenueDashboard(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate) {
        LocalDate today = LocalDate.now();
        LocalDate defaultFrom = today.minusDays(30);
        LocalDate safeFromDate = fromDate != null ? fromDate : defaultFrom;
        LocalDate safeToDate = toDate != null ? toDate : today;

        if (safeFromDate.isAfter(safeToDate)) {
            LocalDate temp = safeFromDate;
            safeFromDate = safeToDate;
            safeToDate = temp;
        }

        LocalDateTime startDate = safeFromDate.atStartOfDay();
        LocalDateTime endDate = safeToDate.atTime(LocalTime.MAX);

        AdminRevenueDashboardResponse result = adminDashboardService.getRevenueDashboard(startDate, endDate);
        return ResponseEntity.ok(ApiResponse.<AdminRevenueDashboardResponse>builder()
                .result(result)
                .build());
    }
}
