package io.gsp26se16.moni.payment.controller;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import io.gsp26se16.moni.payment.dto.response.CreditTransactionResponse;
import io.gsp26se16.moni.payment.service.CreditTransactionService;
import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
@RequestMapping("/credit-transactions")
public class CreditTransactionController {
    private final CreditTransactionService creditTransactionService;

    @GetMapping
    public ResponseEntity<List<CreditTransactionResponse>> searchCreditTransactions(
            @RequestParam(required = false) Integer userId,
            @RequestParam(required = false) String paymentType,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endDate) {
        List<CreditTransactionResponse> creditTransactions =
                creditTransactionService.searchCreditTransactions(userId, paymentType, startDate, endDate);
        return ResponseEntity.ok(creditTransactions);
    }

    @GetMapping("/{id}")
    public ResponseEntity<CreditTransactionResponse> getCreditTransactionDetail(@PathVariable Integer id) {
        CreditTransactionResponse creditTransaction = creditTransactionService.getCreditTransactionDetail(id);
        return ResponseEntity.ok(creditTransaction);
    }
}
