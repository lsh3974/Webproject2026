package com.siheung.hanbaguni.notification;

import com.siheung.hanbaguni.entity.User;
import com.siheung.hanbaguni.repository.NotificationDeliveryRepository;
import com.siheung.hanbaguni.repository.NotificationPreferenceRepository;
import com.siheung.hanbaguni.repository.PushSubscriptionRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NotificationDeliveryTransactionsTest {
    @Mock NotificationDeliveryRepository deliveries;
    @Mock PushSubscriptionRepository subscriptions;
    @Mock NotificationPreferenceRepository preferences;

    @Test
    void 푸시_구독_소유자가_알림_수신자와_같으면_발송_대상으로_인정한다() {
        NotificationDeliveryTransactions transactions = new NotificationDeliveryTransactions(
                deliveries, subscriptions, preferences);
        NotificationDelivery delivery = mock(NotificationDelivery.class);
        Notification notification = mock(Notification.class);
        PushSubscription subscription = mock(PushSubscription.class);
        User recipient = mock(User.class);

        when(deliveries.findById(50L)).thenReturn(Optional.of(delivery));
        when(delivery.getChannel()).thenReturn(NotificationDelivery.Channel.PUSH);
        when(delivery.getPushSubscription()).thenReturn(subscription);
        when(delivery.getNotification()).thenReturn(notification);
        when(notification.getUser()).thenReturn(recipient);
        when(recipient.getId()).thenReturn(10L);
        when(subscription.getId()).thenReturn(70L);
        when(subscriptions.findByIdAndUserId(70L, 10L)).thenReturn(Optional.of(subscription));
        when(subscription.isActive()).thenReturn(true);

        assertThat(transactions.isPushSubscriptionOwnedByRecipient(50L)).isTrue();
    }

    @Test
    void 푸시_구독_소유자가_알림_수신자와_다르면_발송_대상에서_제외한다() {
        NotificationDeliveryTransactions transactions = new NotificationDeliveryTransactions(
                deliveries, subscriptions, preferences);
        NotificationDelivery delivery = mock(NotificationDelivery.class);
        Notification notification = mock(Notification.class);
        PushSubscription subscription = mock(PushSubscription.class);
        User recipient = mock(User.class);

        when(deliveries.findById(51L)).thenReturn(Optional.of(delivery));
        when(delivery.getChannel()).thenReturn(NotificationDelivery.Channel.PUSH);
        when(delivery.getPushSubscription()).thenReturn(subscription);
        when(delivery.getNotification()).thenReturn(notification);
        when(notification.getUser()).thenReturn(recipient);
        when(recipient.getId()).thenReturn(10L);
        when(subscription.getId()).thenReturn(70L);
        when(subscriptions.findByIdAndUserId(70L, 10L)).thenReturn(Optional.empty());

        assertThat(transactions.isPushSubscriptionOwnedByRecipient(51L)).isFalse();
    }

    @Test
    void 비활성_구독은_소유자가_일치해도_발송_대상에서_제외한다() {
        NotificationDeliveryTransactions transactions = new NotificationDeliveryTransactions(
                deliveries, subscriptions, preferences);
        NotificationDelivery delivery = mock(NotificationDelivery.class);
        Notification notification = mock(Notification.class);
        PushSubscription subscription = mock(PushSubscription.class);
        User recipient = mock(User.class);

        when(deliveries.findById(52L)).thenReturn(Optional.of(delivery));
        when(delivery.getChannel()).thenReturn(NotificationDelivery.Channel.PUSH);
        when(delivery.getPushSubscription()).thenReturn(subscription);
        when(delivery.getNotification()).thenReturn(notification);
        when(notification.getUser()).thenReturn(recipient);
        when(recipient.getId()).thenReturn(10L);
        when(subscription.getId()).thenReturn(70L);
        when(subscriptions.findByIdAndUserId(70L, 10L)).thenReturn(Optional.of(subscription));
        when(subscription.isActive()).thenReturn(false);

        assertThat(transactions.isPushSubscriptionOwnedByRecipient(52L)).isFalse();
    }

    @Test
    void 만료된_푸시응답은_알림_수신자와_현재_구독_소유자가_일치할_때만_비활성화한다() {
        NotificationDeliveryTransactions transactions = new NotificationDeliveryTransactions(
                deliveries, subscriptions, preferences);
        NotificationDelivery delivery = mock(NotificationDelivery.class);
        Notification notification = mock(Notification.class);
        PushSubscription subscription = mock(PushSubscription.class);
        User recipient = mock(User.class);

        when(deliveries.findByIdForUpdate(60L)).thenReturn(Optional.of(delivery));
        when(delivery.getPushSubscription()).thenReturn(subscription);
        when(delivery.getNotification()).thenReturn(notification);
        when(notification.getUser()).thenReturn(recipient);
        when(recipient.getId()).thenReturn(10L);
        when(subscription.getId()).thenReturn(70L);

        transactions.markFailure(60L, new NotificationSendException(
                "브라우저 푸시 구독이 만료되었습니다.",
                NotificationSendException.Outcome.SUBSCRIPTION_GONE));

        // Repository의 조건부 UPDATE가 id와 현재 소유자(userId)를 함께 검사한다.
        verify(subscriptions).deactivateIfOwnedByUser(70L, 10L);
        verify(delivery).removeSubscriptionTarget();
    }

    @Test
    void 만료된_푸시응답에_구독이_없으면_구독_비활성화_쿼리를_호출하지_않는다() {
        NotificationDeliveryTransactions transactions = new NotificationDeliveryTransactions(
                deliveries, subscriptions, preferences);
        NotificationDelivery delivery = mock(NotificationDelivery.class);
        Notification notification = mock(Notification.class);
        User recipient = mock(User.class);

        when(deliveries.findByIdForUpdate(61L)).thenReturn(Optional.of(delivery));
        when(delivery.getPushSubscription()).thenReturn(null);
        when(delivery.getNotification()).thenReturn(notification);
        when(notification.getUser()).thenReturn(recipient);
        when(recipient.getId()).thenReturn(10L);

        transactions.markFailure(61L, new NotificationSendException(
                "브라우저 푸시 구독이 만료되었습니다.",
                NotificationSendException.Outcome.SUBSCRIPTION_GONE));

        verify(subscriptions, never()).deactivateIfOwnedByUser(anyLong(), anyLong());
        verify(delivery).removeSubscriptionTarget();
    }
}
