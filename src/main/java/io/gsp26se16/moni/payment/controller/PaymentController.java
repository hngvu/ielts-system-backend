package io.gsp26se16.moni.payment.controller;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import io.gsp26se16.moni.payment.dto.request.PaymentInitRequest;
import io.gsp26se16.moni.payment.dto.request.SePayWebhookRequest;
import io.gsp26se16.moni.payment.dto.response.PaymentInitResponse;
import io.gsp26se16.moni.payment.dto.response.PaymentResponse;
import io.gsp26se16.moni.payment.service.PaymentService;
import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
@RequestMapping("/payments")
public class PaymentController {
    private final PaymentService paymentService;

    @PostMapping("/sepay")
    public ResponseEntity<PaymentResponse> handleSePayWebhook(@RequestBody SePayWebhookRequest sePayWebhookRequest) {
        PaymentResponse response = paymentService.handleSePayCallback(sePayWebhookRequest);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/init")
    public ResponseEntity<PaymentInitResponse> initPayment(@RequestBody PaymentInitRequest paymentInitRequest) {
        PaymentInitResponse response = paymentService.initPayment(paymentInitRequest);
        return ResponseEntity.ok(response);
    }

    @GetMapping
    public ResponseEntity<List<PaymentResponse>> searchPayments(
            @RequestParam(required = false) Integer userId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endDate) {
        List<PaymentResponse> payments = paymentService.searchPayments(userId, status, startDate, endDate);
        return ResponseEntity.ok(payments);
    }
}
