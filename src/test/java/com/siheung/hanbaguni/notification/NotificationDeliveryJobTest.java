package com.siheung.hanbaguni.notification;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class NotificationDeliveryJobTest {
    @Test
    void 푸시_구독_소유자가_변경되었으면_전송하지_않고_SKIPPED_처리한다() {
        NotificationDeliveryTransactions transactions = mock(NotificationDeliveryTransactions.class);
        NotificationChannelSender pushSender = mock(NotificationChannelSender.class);
        when(pushSender.channel()).thenReturn(NotificationDelivery.Channel.PUSH);
        when(transactions.claimNext()).thenReturn(Optional.of(99L), Optional.empty());
        when(transactions.load(99L)).thenReturn(new NotificationDispatchData(
                99L, LocalDateTime.now(), 88L, NotificationDelivery.Channel.PUSH, 1,
                "결제 완료", "거래 정보", "/orders/1", null, null, null,
                false, false, false, false, true, true,
                77L, "https://fcm.googleapis.com/example", "p256dh", "auth"));
        when(transactions.isPushSubscriptionOwnedByRecipient(99L)).thenReturn(false);

        NotificationDeliveryJob job = new NotificationDeliveryJob(transactions, List.of(pushSender));
        job.dispatch();

        verify(transactions).markSkipped(eq(99L), contains("소유자가"));
        verify(pushSender, never()).send(any(NotificationDispatchData.class));
        verify(transactions, never()).markSent(eq(99L), any());
    }
}
