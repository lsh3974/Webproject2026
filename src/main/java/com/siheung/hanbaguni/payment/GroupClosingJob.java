package com.siheung.hanbaguni.payment;

import com.siheung.hanbaguni.entity.GroupPurchase;
import com.siheung.hanbaguni.entity.Order;
import com.siheung.hanbaguni.repository.GroupPurchaseRepository;
import com.siheung.hanbaguni.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class GroupClosingJob {

    private final GroupPurchaseRepository groups;
    private final OrderRepository orders;
    private final GroupClosingTransactions closing;
    private final RefundService refunds;
    private final PaymentService payments;

    public void runOnce(LocalDateTime now) {
        for (Long orderId : orders.findDuePaymentIds(Order.Status.PAYMENT_PENDING, now)) {
            try {
                payments.recoverPending(orderId);
            } catch (RuntimeException ex) {
                log.warn("마감 결제 재조회 오류 orderId={}", orderId, ex);
            }
        }

        for (Long groupId : groups.findDueGroupIds(GroupPurchase.Status.OPEN, now)) {
            try {
                closing.closeIfDue(groupId, now);
            } catch (RuntimeException ex) {
                log.warn("공동구매 마감 실패 groupId={}", groupId, ex);
            }
        }

        List<Long> orderIds = orders.findRefundCandidateIds(
                GroupPurchase.Status.FAILED,
                Order.Status.PAID, Order.Status.REFUND_PENDING
        );

        for (Long orderId : orderIds) {
            try {
                refunds.refundFailedOrder(orderId);
            } catch (RuntimeException ex) {
                log.warn("모집 실패 주문 환불 오류 orderId={}", orderId, ex);
            }
        }
    }
}