package io.gsp26se16.moni.payment.service;

import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

public interface PaymentNotificationService {

    SseEmitter subscribe(String userId);

    void notifyPaymentSuccess(String userId, Integer paymentId, double newBalance);
}
