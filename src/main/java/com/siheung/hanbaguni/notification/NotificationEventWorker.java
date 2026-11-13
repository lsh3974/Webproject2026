package com.siheung.hanbaguni.notification;

import com.siheung.hanbaguni.entity.User;
import com.siheung.hanbaguni.repository.NotificationDeliveryRepository;
import com.siheung.hanbaguni.repository.NotificationEventRepository;
import com.siheung.hanbaguni.repository.NotificationPreferenceRepository;
import com.siheung.hanbaguni.repository.NotificationRepository;
import com.siheung.hanbaguni.repository.PushSubscriptionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationEventWorker {
    private final NotificationEventRepository events;
    private final NotificationRepository notifications;
    private final NotificationPreferenceRepository preferences;
    private final NotificationDeliveryRepository deliveries;
    private final PushSubscriptionRepository subscriptions;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void process(Long eventId) {
        NotificationEvent event = events.findByIdForUpdate(eventId).orElse(null);
        if (event == null || event.getStatus() != NotificationEvent.EventStatus.PENDING) return;
        if (!notifications.existsByEventKey(event.getEventKey())) {
            Notification notification = notifications.save(new Notification(event));
            User user = event.getRecipient();
            var preference = preferences.findByUserId(user.getId()).orElse(null);
            if (preference != null) {
                if (preference.isEmailEnabled() && user.getEmailVerifiedAt() != null
                        && user.getEmail() != null && !user.getEmail().isBlank()) {
                    addDelivery(notification, NotificationDelivery.Channel.EMAIL, "default", null);
                }
                if (preference.isSmsEnabled() && user.getPhoneVerifiedAt() != null
                        && user.getPhoneE164() != null && !user.getPhoneE164().isBlank()) {
                    addDelivery(notification, NotificationDelivery.Channel.SMS, "default", null);
                }
                if (preference.isPushEnabled()) {
                    for (PushSubscription subscription : subscriptions.findByUserIdAndActiveTrue(user.getId())) {
                        addDelivery(notification, NotificationDelivery.Channel.PUSH,
                                "subscription-" + subscription.getId(), subscription);
                    }
                }
            }
        }
        event.markProcessed();
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordFailure(Long eventId, RuntimeException exception) {
        NotificationEvent event = events.findByIdForUpdate(eventId).orElse(null);
        if (event == null || event.getStatus() != NotificationEvent.EventStatus.PENDING) return;
        log.error("알림 Outbox 개별 처리 실패 eventId={} eventKey={}", event.getId(), event.getEventKey(), exception);
        event.recordFailure(exception);
    }

    private void addDelivery(Notification notification, NotificationDelivery.Channel channel,
                             String targetKey, PushSubscription subscription) {
        if (!deliveries.existsByNotificationIdAndChannelAndTargetKey(notification.getId(), channel, targetKey)) {
            deliveries.save(new NotificationDelivery(notification, channel, targetKey, subscription));
        }
    }
}
