package com.siheung.hanbaguni.notification;

import com.siheung.hanbaguni.entity.GroupPurchase;
import com.siheung.hanbaguni.entity.Order;
import com.siheung.hanbaguni.entity.Store;
import com.siheung.hanbaguni.entity.User;
import com.siheung.hanbaguni.entity.Pickup;
import java.time.LocalDateTime;
import com.siheung.hanbaguni.repository.NotificationEventRepository;
import com.siheung.hanbaguni.repository.OrderRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NotificationEventServiceTest {
    @Mock NotificationEventRepository events;
    @Mock OrderRepository orders;
    @InjectMocks NotificationEventService service;

    @Test
    void 결제완료는_구매자와_판매자에게_각각_이벤트를_기록한다() {
        User buyer = user(10L);
        User seller = user(20L);
        Store store = mock(Store.class);
        GroupPurchase group = mock(GroupPurchase.class);
        Order order = mock(Order.class);
        when(order.getId()).thenReturn(100L);
        when(order.getGroupPurchaseId()).thenReturn(200L);
        when(order.getUser()).thenReturn(buyer);
        when(order.getGroupPurchase()).thenReturn(group);
        when(group.getId()).thenReturn(200L);
        when(group.getTitle()).thenReturn("사과 공동구매");
        when(group.getStore()).thenReturn(store);
        when(store.getId()).thenReturn(300L);
        when(store.getSeller()).thenReturn(seller);
        when(events.existsByEventKey(any())).thenReturn(false);

        service.paymentCompleted(order);

        ArgumentCaptor<NotificationEvent> captor = ArgumentCaptor.forClass(NotificationEvent.class);
        verify(events, times(2)).save(captor.capture());
        assertThat(captor.getAllValues())
                .extracting(NotificationEvent::getEventType)
                .containsOnly(NotificationType.PAYMENT_COMPLETED);
        assertThat(captor.getAllValues())
                .extracting(NotificationEvent::getRecipient)
                .containsExactlyInAnyOrder(buyer, seller);
        assertThat(captor.getAllValues())
                .extracting(NotificationEvent::getTargetUrl)
                .contains("/orders/100", "/seller/stores/300/pickups");
    }

    @Test
    void 모집실패_알림은_PAID_구매자에게만_환불예정으로_기록한다() {
        User buyer = user(10L);
        User seller = user(20L);
        Store store = mock(Store.class);
        GroupPurchase group = mock(GroupPurchase.class);
        Order paid = mock(Order.class);
        when(paid.getUser()).thenReturn(buyer);
        when(orders.findByGroupPurchaseIdAndStatus(200L, Order.Status.PAID)).thenReturn(List.of(paid));
        when(group.getId()).thenReturn(200L);
        when(group.getTitle()).thenReturn("감자 공동구매");
        when(group.getStore()).thenReturn(store);
        when(store.getSeller()).thenReturn(seller);
        when(store.getId()).thenReturn(300L);
        when(events.existsByEventKey(any())).thenReturn(false);

        service.groupFailed(group);

        ArgumentCaptor<NotificationEvent> captor = ArgumentCaptor.forClass(NotificationEvent.class);
        verify(events, times(2)).save(captor.capture());
        NotificationEvent buyerEvent = captor.getAllValues().stream()
                .filter(event -> event.getRecipient().equals(buyer)).findFirst().orElseThrow();
        assertThat(buyerEvent.getEventType()).isEqualTo(NotificationType.GROUP_FAILED);
        assertThat(buyerEvent.getTitle()).contains("환불 예정");
        assertThat(buyerEvent.getBody()).contains("전액 환불 예정");
        verify(orders).findByGroupPurchaseIdAndStatus(200L, Order.Status.PAID);
    }

    @Test
    void 동일_모집결과_수신자_이벤트키가_존재하면_중복저장하지_않는다() {
        User buyer = user(10L);
        User seller = user(20L);
        Store store = mock(Store.class);
        GroupPurchase group = mock(GroupPurchase.class);
        Order paid = mock(Order.class);
        when(paid.getUser()).thenReturn(buyer);
        when(orders.findByGroupPurchaseIdAndStatus(200L, Order.Status.PAID)).thenReturn(List.of(paid));
        when(group.getId()).thenReturn(200L);
        when(group.getTitle()).thenReturn("배 공동구매");
        when(group.getStore()).thenReturn(store);
        when(store.getSeller()).thenReturn(seller);
        when(store.getId()).thenReturn(300L);
        when(events.existsByEventKey(any())).thenReturn(true);

        service.groupSucceeded(group);

        verify(events, never()).save(any());
    }


    @Test
    void 수령_알림은_자유입력_안내와_전화번호를_외부_알림_본문에_포함하지_않는다() {
        User buyer = user(10L);
        Store store = mock(Store.class);
        GroupPurchase group = mock(GroupPurchase.class);
        Order order = mock(Order.class);
        Pickup pickup = mock(Pickup.class);
        when(order.getId()).thenReturn(100L);
        when(order.getUser()).thenReturn(buyer);
        when(pickup.getOrder()).thenReturn(order);
        when(pickup.getGroupPurchase()).thenReturn(group);
        when(group.getId()).thenReturn(200L);
        when(group.getTitle()).thenReturn("상담 010-1234-5678 사과 공동구매");
        when(group.getStore()).thenReturn(store);
        when(store.getName()).thenReturn("시흥마트");
        when(group.getPickupStartAt()).thenReturn(LocalDateTime.of(2026, 11, 10, 10, 0));
        when(group.getPickupEndAt()).thenReturn(LocalDateTime.of(2026, 11, 12, 18, 0));
        when(events.existsByEventKey(any())).thenReturn(false);

        service.pickupReady(pickup);

        ArgumentCaptor<NotificationEvent> captor = ArgumentCaptor.forClass(NotificationEvent.class);
        verify(events).save(captor.capture());
        NotificationEvent event = captor.getValue();
        assertThat(event.getTitle()).doesNotContain("010-1234-5678");
        assertThat(event.getBody()).doesNotContain("010-1234-5678", "010-9999-8888");
        assertThat(event.getBody()).contains("시흥마트", "2026.11.10 10:00", "2026.11.12 18:00");
    }

    private User user(Long id) {
        User user = mock(User.class);
        when(user.getId()).thenReturn(id);
        return user;
    }
}
