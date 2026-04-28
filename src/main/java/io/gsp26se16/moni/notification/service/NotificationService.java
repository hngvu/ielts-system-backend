package io.gsp26se16.moni.notification.service;

import java.util.List;

import io.gsp26se16.moni.notification.dto.response.NotificationResponse;
import io.gsp26se16.moni.notification.entity.Notification;
import io.gsp26se16.moni.notification.enumeration.NotificationType;

public interface NotificationService {

    /** Create + persist a notification, push via SSE if user is connected. */
    Notification create(
            String userId, NotificationType type, String title, String message, String link, Integer scoringSessionId);

    List<NotificationResponse> listForUser(String userId, int limit);

    long unreadCount(String userId);

    void markAsRead(String userId, Long notificationId);

    int markAllAsRead(String userId);
}
