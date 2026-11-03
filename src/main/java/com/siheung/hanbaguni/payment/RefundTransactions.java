package com.siheung.hanbaguni.payment;

import com.siheung.hanbaguni.entity.GroupPurchase;
import com.siheung.hanbaguni.entity.Order;
import com.siheung.hanbaguni.entity.User;
import com.siheung.hanbaguni.repository.GroupPurchaseRepository;
import com.siheung.hanbaguni.repository.OrderRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.Objects;
import java.util.UUID;

import static com.siheung.hanbaguni.payment.RefundData.*;

@Service
@Transactional(isolation = Isolation.READ_COMMITTED)
public class RefundTransactions {

    private final OrderRepository orders;
    private final GroupPurchaseRepository groups;

    public RefundTransactions(
            OrderRepository orders,
            GroupPurchaseRepository groups
    ) {
        this.orders = orders;
        this.groups = groups;
    }

    // 구매자가 모집 중인 본인 주문을 취소합니다.
    public Attempt startCustomerRefund(
            Long orderId,
            Long userId,
            String reason
    ) {
        Order order = lockOrder(orderId);

        checkOwner(order, userId);

        // 이미 접수된 요청은 기존 번호와 사유를 그대로 사용합니다.
        if (isRefundStarted(order)) {
            return toAttempt(order);
        }

        if (order.getUser().getStatus() != User.Status.ACTIVE) {
            throw forbidden("사용할 수 없는 회원입니다.");
        }

        GroupPurchase group = order.getGroupPurchase();

        if (group.getStatus() != GroupPurchase.Status.OPEN
                || !LocalDateTime.now().isBefore(group.getEndAt())) {
            throw conflict(
                    "모집 중인 주문만 직접 취소할 수 있습니다."
            );
        }

        return start(order, reason);
    }

    // 모집 실패 시 내부 마감 처리에서 사용할 메서드입니다.
    // 고객 요청용 컨트롤러에 직접 연결하지 않습니다.
    public Attempt startFailedGroupRefund(Long orderId) {
        Order order = lockOrder(orderId);

        if (isRefundStarted(order)) {
            return toAttempt(order);
        }
        if (order.getGroupPurchase().getStatus()
                != GroupPurchase.Status.FAILED) {
            throw conflict(
                    "모집 실패가 확정된 공동구매만 자동 환불할 수 있습니다."
            );
        }

        return start(
                order,
                "공동구매 목표 수량 미달로 인한 전액 환불"
        );
    }

    // 이미 접수된 본인 환불 요청을 재확인합니다.
    public Attempt resumeCustomerRefund(
            Long orderId,
            Long userId
    ) {
        Order order = lockOrder(orderId);

        checkOwner(order, userId);

        if (!isRefundStarted(order)) {
            throw conflict("접수된 환불 요청이 없습니다.");
        }

        return toAttempt(order);
    }

    // Toss에서 전액 취소가 확인된 뒤 호출합니다.
    public Result complete(
            Attempt attempt,
            Receipt receipt
    ) {
        Order order = lockOrder(attempt.orderId());

        validateStoredAttempt(order, attempt);

        if (order.getStatus() == Order.Status.REFUNDED) {
            return refundedResult();
        }

        if (order.getStatus() != Order.Status.REFUND_PENDING) {
            throw conflict("환불 처리 중인 주문이 아닙니다.");
        }

        if (!isFullRefund(attempt, receipt)) {
            throw conflict(
                    "주문 정보와 일치하는 전액 환불 결과가 아닙니다."
            );
        }

        order.completeRefund(attempt.refundRequestId());

        // 수량은 환불 접수 때 이미 차감했습니다.
        // 여기서 다시 차감하지 않습니다.
        return refundedResult();
    }

    private Attempt start(Order order, String reason) {
        if (order.getStatus() != Order.Status.PAID) {
            throw conflict(
                    "결제 완료 주문만 환불을 시작할 수 있습니다."
            );
        }

        if (order.getPaymentKey() == null
                || order.getPaymentKey().isBlank()
                || order.getPaymentOrderRef() == null
                || order.getPaymentOrderRef().isBlank()
                || order.getTotalPrice() == null
                || order.getTotalPrice() <= 0) {
            throw conflict("저장된 결제 정보가 올바르지 않습니다.");
        }

        if (reason == null
                || reason.isBlank()
                || reason.strip().length() > 200) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "취소 사유를 1~200자로 입력해주세요."
            );
        }

        String requestId = UUID.randomUUID().toString();

        boolean changed = order.beginRefund(
                requestId,
                reason.strip()
        );

        if (changed) {
            order.getGroupPurchase()
                    .removePaidQuantity(order.getQuantity());
        }

        return toAttempt(order);
    }

    private Order lockOrder(Long orderId) {
        Long groupId = orders.findGroupIdForRefund(orderId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "주문을 찾을 수 없습니다."
                ));

        // 결제 처리와 동일하게 모집 → 주문 순서로 잠급니다.
        groups.findByIdWithLock(groupId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "공동구매를 찾을 수 없습니다."
                ));

        return orders.findPaymentOrderWithLock(orderId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "주문을 찾을 수 없습니다."
                ));
    }

    private void checkOwner(Order order, Long userId) {
        if (!order.isOwnedBy(userId)) {
            throw forbidden("본인 주문만 취소하거나 확인할 수 있습니다.");
        }
    }

    private boolean isRefundStarted(Order order) {
        return order.getStatus() == Order.Status.REFUND_PENDING
                || order.getStatus() == Order.Status.REFUNDED;
    }

    private Attempt toAttempt(Order order) {
        if (order.getRefundRequestId() == null
                || order.getPaymentKey() == null
                || order.getPaymentOrderRef() == null
                || order.getCancelReason() == null) {
            throw conflict("저장된 환불 요청 정보가 없습니다.");
        }

        return new Attempt(
                order.getId(),
                order.getUserId(),
                order.getPaymentOrderRef(),
                order.getPaymentKey(),
                order.getTotalPrice(),
                order.getRefundRequestId(),
                order.getCancelReason(),
                order.getStatus() == Order.Status.REFUNDED
        );
    }

    private void validateStoredAttempt(
            Order order,
            Attempt attempt
    ) {
        if (!Objects.equals(
                order.getUserId(),
                attempt.userId()
        ) || !Objects.equals(
                order.getPaymentOrderRef(),
                attempt.orderRef()
        ) || !Objects.equals(
                order.getPaymentKey(),
                attempt.paymentKey()
        ) || !Objects.equals(
                order.getRefundRequestId(),
                attempt.refundRequestId()
        ) || !Objects.equals(
                order.getCancelReason(),
                attempt.reason()
        ) || order.getTotalPrice() != attempt.amount()) {
            throw conflict("저장된 환불 정보가 일치하지 않습니다.");
        }
    }

    public static boolean matches(
            Attempt attempt,
            Receipt receipt
    ) {
        return receipt != null
                && Objects.equals(
                attempt.paymentKey(),
                receipt.paymentKey()
        )
                && Objects.equals(
                attempt.orderRef(),
                receipt.orderId()
        )
                && receipt.totalAmount() != null
                && receipt.totalAmount().longValue() == attempt.amount()
                && "KRW".equals(receipt.currency());
    }

    public static boolean isFullRefund(
            Attempt attempt,
            Receipt receipt
    ) {
        return matches(attempt, receipt)
                && "CANCELED".equals(receipt.status())
                && receipt.balanceAmount() != null
                && receipt.balanceAmount().longValue() == 0L;
    }

    private Result refundedResult() {
        return new Result(
                "REFUNDED",
                "전액 환불이 완료되었습니다."
        );
    }

    private ResponseStatusException conflict(String message) {
        return new ResponseStatusException(
                HttpStatus.CONFLICT,
                message
        );
    }

    private ResponseStatusException forbidden(String message) {
        return new ResponseStatusException(
                HttpStatus.FORBIDDEN,
                message
        );
    }
}