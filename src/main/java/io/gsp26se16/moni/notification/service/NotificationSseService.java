package io.gsp26se16.moni.notification.service;

import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import io.gsp26se16.moni.notification.dto.response.NotificationResponse;

public interface NotificationSseService {

    SseEmitter subscribe(String userId);

    /** Push a "new notification" event to the user if connected. Silent no-op otherwise. */
    void pushNew(String userId, NotificationResponse payload);
}
