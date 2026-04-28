package io.gsp26se16.moni.notification.service.impl;

import java.util.List;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.gsp26se16.moni.notification.dto.response.NotificationResponse;
import io.gsp26se16.moni.notification.entity.Notification;
import io.gsp26se16.moni.notification.enumeration.NotificationType;
import io.gsp26se16.moni.notification.repository.NotificationRepository;
import io.gsp26se16.moni.notification.service.NotificationService;
import io.gsp26se16.moni.notification.service.NotificationSseService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationServiceImpl implements NotificationService {

    private final NotificationRepository repository;
    private final NotificationSseService sseService;

    @Override
    @Transactional
    public Notification create(
            String userId, NotificationType type, String title, String message, String link, Integer scoringSessionId) {
        Notification notification = Notification.builder()
                .userId(userId)
                .type(type)
                .title(title)
                .message(message)
                .link(link)
                .scoringSessionId(scoringSessionId)
                .isRead(false)
                .build();
        Notification saved = repository.save(notification);

        // Fire SSE outside DB write but inside the same transaction is fine — emitters are best-effort
        try {
            sseService.pushNew(userId, NotificationResponse.from(saved));
        } catch (Exception e) {
            log.warn("Failed to push SSE notification to user {}: {}", userId, e.getMessage());
        }
        return saved;
    }

    @Override
    @Transactional(readOnly = true)
    public List<NotificationResponse> listForUser(String userId, int limit) {
        int safeLimit = Math.min(Math.max(limit, 1), 50);
        return repository.findByUserIdOrderByCreatedAtDesc(userId, PageRequest.of(0, safeLimit)).stream()
                .map(NotificationResponse::from)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public long unreadCount(String userId) {
        return repository.countByUserIdAndIsReadFalse(userId);
    }

    @Override
    @Transactional
    public void markAsRead(String userId, Long notificationId) {
        repository.findById(notificationId).ifPresent(n -> {
            // Ownership check — silently ignore mismatch to keep API idempotent
            if (!userId.equals(n.getUserId())) return;
            if (Boolean.FALSE.equals(n.getIsRead())) {
                n.setIsRead(true);
                repository.save(n);
            }
        });
    }

    @Override
    @Transactional
    public int markAllAsRead(String userId) {
        return repository.markAllReadForUser(userId);
    }
}
