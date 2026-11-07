package com.siheung.hanbaguni.payment;

import com.siheung.hanbaguni.entity.GroupPurchase;
import com.siheung.hanbaguni.entity.Order;
import com.siheung.hanbaguni.repository.GroupPurchaseRepository;
import com.siheung.hanbaguni.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class GroupClosingTransactions {

    private final GroupPurchaseRepository groups;
    private final OrderRepository orders;
    private final com.siheung.hanbaguni.notification.NotificationEventService notificationEvents;

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public boolean closeIfDue(Long groupId, LocalDateTime now) {
        GroupPurchase group = groups.findByIdWithLock(groupId)
                .orElseThrow();

        boolean hasPendingApproval = orders.existsByGroupPurchaseIdAndStatus(
                groupId, Order.Status.PAYMENT_PENDING
        );

        if (!group.failIfDeadlineReached(now, hasPendingApproval)) {
            return false;
        }

        for (Order order : orders.findPendingOrdersForGroupWithLock(groupId)) {
            order.expireReservation();
        }

        // 모집 실패 확정과 환불 예정 안내 이벤트를 같은 트랜잭션에 저장합니다.
        notificationEvents.groupFailed(group);
        return true;
    }
}