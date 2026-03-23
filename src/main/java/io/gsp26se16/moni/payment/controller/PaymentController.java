package io.gsp26se16.moni.payment.controller;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import io.gsp26se16.moni.authentication.repository.UserCredentialsRepository;
import io.gsp26se16.moni.common.exception.AppException;
import io.gsp26se16.moni.common.exception.ErrorCode;
import io.gsp26se16.moni.payment.dto.request.PaymentInitRequest;
import io.gsp26se16.moni.payment.dto.request.SePayWebhookRequest;
import io.gsp26se16.moni.payment.dto.response.PaymentInitResponse;
import io.gsp26se16.moni.payment.dto.response.PaymentResponse;
import io.gsp26se16.moni.payment.service.PaymentNotificationService;
import io.gsp26se16.moni.payment.service.PaymentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@RestController
@RequiredArgsConstructor
@RequestMapping("/payments")
@Slf4j
public class PaymentController {
    @Value("${sepay.api-key}")
    private String SEPAY_API_KEY;

    private final PaymentService paymentService;
    private final PaymentNotificationService notificationService;
    private final UserCredentialsRepository userCredentialsRepository;

    @PostMapping("/sepay")
    public ResponseEntity<PaymentResponse> handleSePayWebhook(
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @RequestBody SePayWebhookRequest sePayWebhookRequest) {
        log.info("=== SEpay Webhook Received ===");
        log.info("Authorization header: {}", authHeader != null ? "[PRESENT]" : "[ABSENT]");
        log.info("Webhook request: {}", sePayWebhookRequest);
        if (authHeader == null || !authHeader.equals("Apikey " + SEPAY_API_KEY)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        PaymentResponse response = paymentService.handleSePayCallback(sePayWebhookRequest);
        log.info("Webhook processed successfully: {}", response);
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

    /** SSE endpoint — token via query param since EventSource doesn't support headers */
    @GetMapping(value = "/subscribe", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter subscribe(@RequestParam String token) {
        try {
            var signedJWT = com.nimbusds.jwt.SignedJWT.parse(token);
            String credentialId = signedJWT.getJWTClaimsSet().getStringClaim("userId");
            if (credentialId != null) {
                String userId = userCredentialsRepository
                        .findById(credentialId)
                        .map(cred -> cred.getUser().getId())
                        .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_EXISTED));
                return notificationService.subscribe(userId);
            }
        } catch (AppException e) {
            throw e;
        } catch (Exception e) {
            throw new AppException(ErrorCode.UNAUTHENTICATED);
        }
        throw new AppException(ErrorCode.UNAUTHENTICATED);
    }
}
