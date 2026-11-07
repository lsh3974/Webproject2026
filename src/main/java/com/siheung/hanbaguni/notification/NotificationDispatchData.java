package com.siheung.hanbaguni.notification;

import java.time.LocalDateTime;

public record NotificationDispatchData(
        Long deliveryId,
        LocalDateTime deliveryCreatedAt,
        Long notificationId,
        NotificationDelivery.Channel channel,
        int attemptCount,
        String title,
        String body,
        String targetUrl,
        String recipientEmail,
        String recipientPhone,
        String recipientName,
        boolean emailVerified,
        boolean phoneVerified,
        boolean emailEnabled,
        boolean smsEnabled,
        boolean pushEnabled,
        boolean pushSubscriptionActive,
        Long pushSubscriptionId,
        String pushEndpoint,
        String pushP256dh,
        String pushAuth
) {}
