package io.gsp26se16.moni.notification.dto.response;

import java.sql.Timestamp;

import io.gsp26se16.moni.notification.entity.Notification;
import io.gsp26se16.moni.notification.enumeration.NotificationType;
import lombok.Builder;

@Builder
public record NotificationResponse(
        Long id,
        NotificationType type,
        String title,
        String message,
        String link,
        Boolean isRead,
        Integer scoringSessionId,
        Timestamp createdAt) {

    public static NotificationResponse from(Notification n) {
        return NotificationResponse.builder()
                .id(n.getId())
                .type(n.getType())
                .title(n.getTitle())
                .message(n.getMessage())
                .link(n.getLink())
                .isRead(n.getIsRead())
                .scoringSessionId(n.getScoringSessionId())
                .createdAt(n.getCreatedAt())
                .build();
    }
}
