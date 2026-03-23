package io.gsp26se16.moni.payment.controller;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import io.gsp26se16.moni.authentication.repository.UserCredentialsRepository;
import io.gsp26se16.moni.payment.dto.response.CreditTransactionResponse;
import io.gsp26se16.moni.payment.service.CreditTransactionService;
import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
@RequestMapping("/credit-transactions")
public class CreditTransactionController {
    private final CreditTransactionService creditTransactionService;
    private final UserCredentialsRepository userCredentialsRepository;

    @GetMapping
    public ResponseEntity<List<CreditTransactionResponse>> searchCreditTransactions(
            @RequestParam(required = false) String paymentType,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endDate) {
        // Auto-filter by current user
        String currentUserId = getCurrentUserId();
        List<CreditTransactionResponse> creditTransactions =
                creditTransactionService.searchCreditTransactions(currentUserId, paymentType, startDate, endDate);
        return ResponseEntity.ok(creditTransactions);
    }

    @GetMapping("/{id}")
    public ResponseEntity<CreditTransactionResponse> getCreditTransactionDetail(@PathVariable Integer id) {
        CreditTransactionResponse creditTransaction = creditTransactionService.getCreditTransactionDetail(id);
        return ResponseEntity.ok(creditTransaction);
    }

    private String getCurrentUserId() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof Jwt jwt) {
            String credentialId = jwt.getClaimAsString("userId");
            if (credentialId != null) {
                return userCredentialsRepository
                        .findById(credentialId)
                        .map(cred -> cred.getUser().getId())
                        .orElse(null);
            }
        }
        return null;
    }
}
