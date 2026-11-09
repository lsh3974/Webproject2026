package com.siheung.hanbaguni.notification;

import com.siheung.hanbaguni.entity.User;
import com.siheung.hanbaguni.repository.PushSubscriptionRepository;
import com.siheung.hanbaguni.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NotificationPushSubscriptionServiceTest {
    @Mock PushSubscriptionRepository subscriptions;
    @Mock UserRepository users;

    @Test
    void 로그아웃한_브라우저의_구독만_비활성화한다() {
        NotificationPushSubscriptionService service =
                new NotificationPushSubscriptionService(subscriptions, users);
        PushSubscription currentDevice = mock(PushSubscription.class);
        User owner = mock(User.class);
        when(subscriptions.findByEndpoint("https://fcm.googleapis.com/current-device"))
                .thenReturn(Optional.of(currentDevice));
        when(currentDevice.getUser()).thenReturn(owner);
        when(owner.getId()).thenReturn(10L);

        service.deactivateForUserAndEndpoint(10L, "https://fcm.googleapis.com/current-device");

        verify(currentDevice).deactivate();
        verify(subscriptions).findByEndpoint("https://fcm.googleapis.com/current-device");
    }

    @Test
    void 다른_계정이_소유한_endpoint는_비활성화하지_않는다() {
        NotificationPushSubscriptionService service =
                new NotificationPushSubscriptionService(subscriptions, users);
        PushSubscription otherAccountDevice = mock(PushSubscription.class);
        User owner = mock(User.class);
        when(subscriptions.findByEndpoint("https://fcm.googleapis.com/other-device"))
                .thenReturn(Optional.of(otherAccountDevice));
        when(otherAccountDevice.getUser()).thenReturn(owner);
        when(owner.getId()).thenReturn(20L);

        service.deactivateForUserAndEndpoint(10L, "https://fcm.googleapis.com/other-device");

        verify(otherAccountDevice, never()).deactivate();
    }
}
