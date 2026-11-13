package com.siheung.hanbaguni.notification;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class NotificationDeliveryTest {
    @Test
    void 발송상태가_성공_재시도_불명_건너뜀으로_구분된다() {
        NotificationDelivery delivery = new NotificationDelivery(
                mock(Notification.class), NotificationDelivery.Channel.EMAIL, "default", null);
        delivery.claim(java.time.LocalDateTime.now());
        assertThat(delivery.getStatus()).isEqualTo(NotificationDelivery.DeliveryStatus.PROCESSING);
        assertThat(delivery.getAttemptCount()).isEqualTo(1);

        delivery.retry("일시 오류", java.time.LocalDateTime.now().plusSeconds(30));
        assertThat(delivery.getStatus()).isEqualTo(NotificationDelivery.DeliveryStatus.RETRY);

        delivery.claim(java.time.LocalDateTime.now());
        delivery.sent("smtp-accepted");
        assertThat(delivery.getStatus()).isEqualTo(NotificationDelivery.DeliveryStatus.SENT);

        NotificationDelivery skipped = new NotificationDelivery(
                mock(Notification.class), NotificationDelivery.Channel.PUSH, "subscription-1", mock(PushSubscription.class));
        skipped.skip("사용자가 채널을 해제함");
        assertThat(skipped.getStatus()).isEqualTo(NotificationDelivery.DeliveryStatus.SKIPPED);
    }
}
