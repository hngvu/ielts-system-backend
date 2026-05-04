package io.gsp26se16.moni.payment.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.gsp26se16.moni.common.dto.ApiResponse;
import io.gsp26se16.moni.payment.dto.response.PaymentResponse;
import io.gsp26se16.moni.payment.service.PaymentService;
import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/admin/payments")
public class AdminPaymentStatusController {

    private final PaymentService paymentService;

    @PostMapping("/{paymentId}/approve-late")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<PaymentResponse>> approveLatePayment(@PathVariable Integer paymentId) {
        PaymentResponse result = paymentService.approveLatePayment(paymentId);
        return ResponseEntity.ok(ApiResponse.<PaymentResponse>builder()
                .result(result)
                .message("Late payment approved")
                .build());
    }

    @PostMapping("/{paymentId}/refund-duplicate")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<PaymentResponse>> refundDuplicatePayment(@PathVariable Integer paymentId) {
        PaymentResponse result = paymentService.refundDuplicatePayment(paymentId);
        return ResponseEntity.ok(ApiResponse.<PaymentResponse>builder()
                .result(result)
                .message("Duplicate payment refunded")
                .build());
    }
}
