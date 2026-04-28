package io.gsp26se16.moni.notification.service.impl;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import io.gsp26se16.moni.notification.dto.response.NotificationResponse;
import io.gsp26se16.moni.notification.service.NotificationSseService;
import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
public class NotificationSseServiceImpl implements NotificationSseService {

    // 30 min — long-lived stream; FE auto-reconnects after timeout
    private static final long TIMEOUT_MS = 30L * 60 * 1000;

    // A user may open multiple tabs/devices — keep all emitters and fan out
    private final Map<String, CopyOnWriteArrayList<SseEmitter>> emittersByUser = new ConcurrentHashMap<>();

    @Override
    public SseEmitter subscribe(String userId) {
        SseEmitter emitter = new SseEmitter(TIMEOUT_MS);
        emittersByUser
                .computeIfAbsent(userId, k -> new CopyOnWriteArrayList<>())
                .add(emitter);

        emitter.onCompletion(() -> remove(userId, emitter));
        emitter.onTimeout(() -> remove(userId, emitter));
        emitter.onError(e -> remove(userId, emitter));

        try {
            // Initial handshake event so FE knows connection is alive
            emitter.send(SseEmitter.event().name("connected").data(Map.of("ok", true)));
        } catch (IOException e) {
            remove(userId, emitter);
        }
        return emitter;
    }

    @Override
    public void pushNew(String userId, NotificationResponse payload) {
        var list = emittersByUser.get(userId);
        if (list == null || list.isEmpty()) return;

        for (SseEmitter emitter : list) {
            try {
                emitter.send(SseEmitter.event().name("notification").data(payload));
            } catch (IOException e) {
                log.warn("SSE push failed for user {}: {}", userId, e.getMessage());
                remove(userId, emitter);
            }
        }
    }

    private void remove(String userId, SseEmitter emitter) {
        var list = emittersByUser.get(userId);
        if (list != null) {
            list.remove(emitter);
            if (list.isEmpty()) emittersByUser.remove(userId);
        }
    }
}
