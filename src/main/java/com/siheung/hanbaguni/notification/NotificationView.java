package com.siheung.hanbaguni.notification;

import java.time.LocalDateTime;

public record NotificationView(Long id, String type, String title, String body,
                               String targetUrl, LocalDateTime createdAt, boolean read) {
    public static NotificationView from(Notification n) {
        return new NotificationView(n.getId(), n.getEventType().name(), n.getTitle(), n.getBody(),
                n.getTargetUrl(), n.getCreatedAt(), n.getReadAt() != null);
    }
}
