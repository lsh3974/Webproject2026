package com.siheung.hanbaguni.notification;

import com.siheung.hanbaguni.entity.GroupPurchase;
import com.siheung.hanbaguni.entity.Order;
import com.siheung.hanbaguni.entity.Pickup;
import com.siheung.hanbaguni.entity.Store;
import com.siheung.hanbaguni.entity.User;
import com.siheung.hanbaguni.repository.NotificationEventRepository;
import com.siheung.hanbaguni.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;

/** 상태 변경 트랜잭션 안에서 수신자별 Outbox 이벤트를 저장합니다. 외부 네트워크 호출은 하지 않습니다. */
@Service
@RequiredArgsConstructor
@Transactional
public class NotificationEventService {
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyyy.MM.dd HH:mm");
    private final NotificationEventRepository events;
    private final OrderRepository orders;

    public void paymentCompleted(Order order) {
        GroupPurchase group = order.getGroupPurchase();
        String item = group.getTitle();
        enqueue("ORDER", order.getId(), NotificationType.PAYMENT_COMPLETED, order.getUser(), order.getId(), group.getId(),
                "결제가 완료되었습니다", item + " 주문의 결제가 완료되었습니다.", "/orders/" + order.getId());
        enqueueSellerOrder(NotificationType.PAYMENT_COMPLETED, order,
                "새 결제 완료", item + " 주문의 결제가 완료되었습니다.");
    }

    public void groupSucceeded(GroupPurchase group) {
        for (Order order : orders.findByGroupPurchaseIdAndStatus(group.getId(), Order.Status.PAID)) {
            enqueueGroupBuyer(group, order, NotificationType.GROUP_SUCCEEDED,
                    "공동구매 성공", group.getTitle() + " 공동구매가 목표 수량을 달성했습니다.");
        }
        enqueueSellerGroup(group, NotificationType.GROUP_SUCCEEDED,
                "공동구매 모집 성공", group.getTitle() + " 모집이 목표 수량을 달성했습니다.");
    }

    /** 모집이 SUCCESS가 된 뒤 늦게 PAID 확정된 주문자는 본인 알림만 추가합니다. */
    public void latePaidAfterGroupSuccess(Order order) {
        GroupPurchase group = order.getGroupPurchase();
        enqueueGroupBuyer(group, order, NotificationType.GROUP_SUCCEEDED,
                "공동구매 성공", group.getTitle() + " 공동구매가 목표 수량을 달성했습니다.");
    }

    public void groupFailed(GroupPurchase group) {
        for (Order order : orders.findByGroupPurchaseIdAndStatus(group.getId(), Order.Status.PAID)) {
            enqueueGroupBuyer(group, order, NotificationType.GROUP_FAILED,
                    "공동구매 모집 실패 · 환불 예정", group.getTitle() + " 모집이 목표 수량 미달로 종료되었습니다. 결제 금액은 전액 환불 예정입니다.");
        }
        enqueueSellerGroup(group, NotificationType.GROUP_FAILED,
                "공동구매 모집 실패", group.getTitle() + " 모집이 목표 수량 미달로 종료되어 환불 처리가 진행됩니다.");
    }

    public void refundCompleted(Order order) {
        GroupPurchase group = order.getGroupPurchase();
        enqueue("ORDER", order.getId(), NotificationType.REFUND_COMPLETED, order.getUser(), order.getId(), group.getId(),
                "환불이 완료되었습니다", group.getTitle() + " 주문의 전액 환불이 확인되었습니다.", "/orders/" + order.getId());
        enqueueSellerOrder(NotificationType.REFUND_COMPLETED, order,
                "주문 환불 완료", group.getTitle() + " 주문의 전액 환불이 확인되었습니다.");
    }

    public void pickupReady(Pickup pickup) {
        Order order = pickup.getOrder();
        if (order == null) return; // 시연용 가상 수령 건은 알림 대상이 아닙니다.
        GroupPurchase group = pickup.getGroupPurchase();
        // 자유 입력 수령 안내에는 전화번호 등 민감정보가 들어갈 수 있어 알림에는 구조화된 매장/시간만 포함합니다.
        String body = group.getStore().getName() + "에서 " + group.getTitle() + " 상품을 수령할 수 있습니다. 수령 기간: "
                + group.getPickupStartAt().format(DATE) + " ~ " + group.getPickupEndAt().format(DATE);
        enqueue("ORDER", order.getId(), NotificationType.PICKUP_READY, order.getUser(), order.getId(), group.getId(),
                "상품 수령이 가능합니다", body, "/orders/" + order.getId());
    }

    public void pickupCompleted(Pickup pickup) {
        Order order = pickup.getOrder();
        if (order == null) return;
        GroupPurchase group = pickup.getGroupPurchase();
        enqueue("ORDER", order.getId(), NotificationType.PICKUP_COMPLETED, order.getUser(), order.getId(), group.getId(),
                "상품 수령이 완료되었습니다", group.getTitle() + " 상품의 매장 수령이 완료되었습니다.", "/orders/" + order.getId());
        User seller = group.getStore().getSeller();
        enqueue("ORDER", order.getId(), NotificationType.PICKUP_COMPLETED, seller, order.getId(), group.getId(),
                "상품 수령 완료", group.getTitle() + " 주문의 고객 수령이 완료되었습니다.", sellerPickupUrl(group.getStore()));
    }

    private void enqueueGroupBuyer(GroupPurchase group, Order order, NotificationType type, String title, String body) {
        enqueue("GROUP", group.getId(), type, order.getUser(), null, group.getId(), title, body, "/orders");
    }

    private void enqueueSellerOrder(NotificationType type, Order order, String title, String body) {
        Store store = order.getGroupPurchase().getStore();
        enqueue("ORDER", order.getId(), type, store.getSeller(), order.getId(), order.getGroupPurchaseId(), title, body, sellerPickupUrl(store));
    }

    private void enqueueSellerGroup(GroupPurchase group, NotificationType type, String title, String body) {
        enqueue("GROUP", group.getId(), type, group.getStore().getSeller(), null, group.getId(), title, body,
                "/seller/stores/" + group.getStore().getId() + "/group-purchases");
    }

    private void enqueue(String aggregateType, Long aggregateId, NotificationType type, User recipient,
                         Long orderId, Long groupId, String title, String body, String targetUrl) {
        if (recipient == null || recipient.getId() == null) return;
        String key = type.name() + ":" + aggregateType + ":" + aggregateId + ":USER:" + recipient.getId();
        if (!events.existsByEventKey(key)) {
            events.save(new NotificationEvent(key, type, recipient, orderId, groupId,
                    redactPhoneNumbers(title), redactPhoneNumbers(body), targetUrl));
        }
    }

    private String redactPhoneNumbers(String text) {
        if (text == null) return "";
        return text.replaceAll("(?<!\\d)(?:\\+?82[\\s-]?0?10[\\s-]?\\d{4}[\\s-]?\\d{4}|0\\d{1,2}[\\s-]?\\d{3,4}[\\s-]?\\d{4})(?!\\d)", "[전화번호 생략]");
    }

    private String sellerPickupUrl(Store store) {
        return "/seller/stores/" + store.getId() + "/pickups";
    }
}
