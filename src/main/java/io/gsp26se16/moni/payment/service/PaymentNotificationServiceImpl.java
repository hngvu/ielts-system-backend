package io.gsp26se16.moni.payment.service;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
public class PaymentNotificationServiceImpl implements PaymentNotificationService {

    private final Map<String, SseEmitter> emitters = new ConcurrentHashMap<>();

    public SseEmitter subscribe(String userId) {
        SseEmitter emitter = new SseEmitter(15 * 60 * 1000L); // 15 min timeout (match payment expiry)

        emitters.put(userId, emitter);

        emitter.onCompletion(() -> emitters.remove(userId));
        emitter.onTimeout(() -> emitters.remove(userId));
        emitter.onError(e -> emitters.remove(userId));

        return emitter;
    }

    public void notifyPaymentSuccess(String userId, Integer paymentId, double newBalance) {
        SseEmitter emitter = emitters.get(userId);
        if (emitter == null) return;

        try {
            emitter.send(SseEmitter.event()
                    .name("payment_success")
                    .data(Map.of(
                            "paymentId", paymentId,
                            "credit", newBalance,
                            "message", "Nạp credit thành công!")));
        } catch (IOException e) {
            log.warn("Failed to send SSE to user {}: {}", userId, e.getMessage());
            emitters.remove(userId);
        }
    }
}
