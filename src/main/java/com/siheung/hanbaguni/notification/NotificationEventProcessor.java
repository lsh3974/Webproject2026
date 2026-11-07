package com.siheung.hanbaguni.notification;

import com.siheung.hanbaguni.entity.User;
import com.siheung.hanbaguni.repository.NotificationDeliveryRepository;
import com.siheung.hanbaguni.repository.NotificationEventRepository;
import com.siheung.hanbaguni.repository.NotificationPreferenceRepository;
import com.siheung.hanbaguni.repository.NotificationRepository;
import com.siheung.hanbaguni.repository.PushSubscriptionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "hanbaguni.notifications.enabled", havingValue = "true", matchIfMissing = true)
public class NotificationEventProcessor {
    private final NotificationEventRepository events;
    private final NotificationRepository notifications;
    private final NotificationPreferenceRepository preferences;
    private final NotificationDeliveryRepository deliveries;
    private final PushSubscriptionRepository subscriptions;

    /** Outbox event와 웹 알림/채널 작업 생성은 DB 트랜잭션 안에서 처리합니다. */
    @Scheduled(fixedDelayString = "${hanbaguni.notifications.outbox-delay-ms:3000}")
    @Transactional
    public void processPendingEvents() {
        var due = events.findDueForProcessing(LocalDateTime.now(), PageRequest.of(0, 50));
        for (NotificationEvent event : due) {
            try {
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
            } catch (RuntimeException exception) {
                log.error("알림 Outbox 처리 실패 eventId={} eventKey={}", event.getId(), event.getEventKey(), exception);
                throw exception; // 전체 트랜잭션 롤백 후 다음 스케줄에서 재시도합니다.
            }
        }
    }

    private void addDelivery(Notification notification, NotificationDelivery.Channel channel,
                             String targetKey, PushSubscription subscription) {
        if (!deliveries.existsByNotificationIdAndChannelAndTargetKey(notification.getId(), channel, targetKey)) {
            deliveries.save(new NotificationDelivery(notification, channel, targetKey, subscription));
        }
    }
}
