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

        return true;
    }
}