package io.gsp26se16.moni.payment.controller;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import io.gsp26se16.moni.common.dto.ApiResponse;
import io.gsp26se16.moni.payment.dto.response.CreditTransactionResponse;
import io.gsp26se16.moni.payment.service.CreditTransactionService;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/admin/credit-transactions")
@RequiredArgsConstructor
public class AdminCreditTransactionController {

    private final CreditTransactionService creditTransactionService;

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<List<CreditTransactionResponse>>> searchCreditTransactions(
            @RequestParam(required = false) String userId,
            @RequestParam(required = false) String paymentType,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate) {

        LocalDateTime startDate = fromDate != null ? fromDate.atStartOfDay() : null;
        LocalDateTime endDate = toDate != null ? toDate.atTime(LocalTime.MAX) : null;

        List<CreditTransactionResponse> result =
                creditTransactionService.searchCreditTransactions(userId, paymentType, startDate, endDate);

        return ResponseEntity.ok(ApiResponse.<List<CreditTransactionResponse>>builder()
                .result(result)
                .build());
    }
}
