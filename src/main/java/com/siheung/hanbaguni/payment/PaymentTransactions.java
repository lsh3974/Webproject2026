package com.siheung.hanbaguni.payment;

import com.siheung.hanbaguni.entity.GroupPurchase;
import com.siheung.hanbaguni.entity.Order;
import com.siheung.hanbaguni.entity.Product;
import com.siheung.hanbaguni.entity.Store;
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

import static com.siheung.hanbaguni.payment.PaymentData.*;

@Service
@Transactional(isolation = Isolation.READ_COMMITTED)
public class PaymentTransactions {

    private final OrderRepository orders;
    private final GroupPurchaseRepository groups;

    public PaymentTransactions(
            OrderRepository orders,
            GroupPurchaseRepository groups
    ) {
        this.orders = orders;
        this.groups = groups;
    }

    public Prepared prepare(Long orderId, Long userId) {
        Order order = lockOwnedOrder(orderId, userId);

        validatePendingOrder(order);

        order.assignPaymentOrderRef(
                UUID.randomUUID().toString()
        );

        return new Prepared(
                order.getId(),
                order.getPaymentOrderRef(),
                order.getTotalPrice(),
                "한바구니 주문 " + order.getId()
        );
    }

    public Attempt begin(
            Long orderId,
            Long userId,
            ConfirmRequest request
    ) {
        Order order = lockOwnedOrder(orderId, userId);

        if (request == null
                || request.paymentKey() == null
                || request.paymentKey().isBlank()
                || request.paymentKey().length() > 200
                || request.amount() == null
                || request.amount() <= 0
                || order.getPaymentOrderRef() == null
                || !Objects.equals(
                order.getPaymentOrderRef(),
                request.orderId()
        )
                || request.amount().longValue()
                != order.getTotalPrice().longValue()) {
            throw badRequest("주문번호 또는 결제 금액이 올바르지 않습니다.");
        }

        if (order.getStatus() == Order.Status.PENDING) {
            validatePendingOrder(order);

            order.beginPayment(LocalDateTime.now());
            order.bindPaymentKey(request.paymentKey());

        } else if (order.getStatus() == Order.Status.PAYMENT_PENDING
                || order.getStatus() == Order.Status.PAID) {

            if (!Objects.equals(
                    order.getPaymentKey(),
                    request.paymentKey()
            )) {
                throw conflict("이미 다른 결제 요청이 처리되었습니다.");
            }

        } else {
            throw conflict("결제할 수 없는 주문 상태입니다.");
        }

        return toAttempt(order, userId);
    }

    public Attempt resume(Long orderId, Long userId) {
        Order order = lockOwnedOrder(orderId, userId);

        if (order.getStatus() != Order.Status.PAYMENT_PENDING
                && order.getStatus() != Order.Status.PAID) {
            throw conflict("결과를 확인할 결제 요청이 없습니다.");
        }

        if (order.getPaymentKey() == null
                || order.getPaymentOrderRef() == null) {
            throw conflict("저장된 결제 정보가 없습니다.");
        }

        return toAttempt(order, userId);
    }

    public Result complete(Attempt attempt, Receipt receipt) {
        Order order = lockOwnedOrder(
                attempt.orderId(),
                attempt.userId()
        );

        validateStoredAttempt(order, attempt);

        if (order.getStatus() == Order.Status.PAID) {
            return paidResult();
        }

        if (order.getStatus() != Order.Status.PAYMENT_PENDING) {
            throw conflict("현재 주문 상태에서 결제를 완료할 수 없습니다.");
        }

        if (!matches(attempt, receipt)
                || !"DONE".equals(receipt.status())) {
            throw conflict("결제 승인 결과가 주문 정보와 일치하지 않습니다.");
        }

        order.markPaid(attempt.paymentKey());
        order.getGroupPurchase()
                .addPaidQuantity(order.getQuantity());

        return paidResult();
    }

    public Result fail(Attempt attempt, Receipt receipt) {
        Order order = lockOwnedOrder(
                attempt.orderId(),
                attempt.userId()
        );

        validateStoredAttempt(order, attempt);

        // 다른 요청에서 이미 완료했다면 완료 상태를 유지합니다.
        if (order.getStatus() == Order.Status.PAID) {
            return paidResult();
        }

        if (!matches(attempt, receipt)
                || (!"ABORTED".equals(receipt.status())
                && !"EXPIRED".equals(receipt.status()))) {
            throw conflict("결제 실패 여부를 확인할 수 없습니다.");
        }

        if (order.getStatus() == Order.Status.PAYMENT_PENDING) {
            order.failPayment();
        }

        return new Result(
                "EXPIRED",
                "결제가 완료되지 않았습니다. 새 주문을 만들어주세요."
        );
    }

    private Order lockOwnedOrder(Long orderId, Long userId) {
        Long groupId = orders.findPaymentGroupId(orderId, userId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "주문을 찾을 수 없습니다."
                ));

        // 예약 처리와 동일하게 모집 → 주문 순서로 잠급니다.
        groups.findByIdWithLock(groupId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "공동구매를 찾을 수 없습니다."
                ));

        Order order = orders.findPaymentOrderWithLock(orderId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "주문을 찾을 수 없습니다."
                ));

        if (!order.isOwnedBy(userId)) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "본인 주문만 결제할 수 있습니다."
            );
        }

        if (order.getUser().getStatus() != User.Status.ACTIVE) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "사용할 수 없는 회원입니다."
            );
        }

        return order;
    }

    private void validatePendingOrder(Order order) {
        LocalDateTime now = LocalDateTime.now();

        if (order.getStatus() != Order.Status.PENDING) {
            throw conflict("결제 대기 상태의 주문만 결제할 수 있습니다.");
        }

        if (order.getReservedUntil() == null
                || !order.getReservedUntil().isAfter(now)) {
            throw conflict("예약 시간이 만료되었습니다.");
        }

        GroupPurchase group = order.getGroupPurchase();

        if (group.getStatus() != GroupPurchase.Status.OPEN
                || now.isBefore(group.getStartAt())
                || !now.isBefore(group.getEndAt())
                || group.getStore().getStatus() != Store.Status.ACTIVE
                || group.getProduct().getStatus()
                != Product.Status.ON_SALE) {
            throw conflict("현재 결제할 수 없는 공동구매입니다.");
        }

        if (Objects.equals(
                group.getStore().getSeller().getId(),
                order.getUserId()
        )) {
            throw conflict("본인 매장의 공동구매는 결제할 수 없습니다.");
        }

        if (order.getQuantity() == null
                || order.getQuantity() <= 0
                || order.getUnitPrice() == null
                || order.getUnitPrice() <= 0
                || order.getTotalPrice() == null
                || order.getTotalPrice() <= 0
                || (long) order.getQuantity() * order.getUnitPrice()
                != order.getTotalPrice()) {
            throw conflict("주문의 수량 또는 금액이 올바르지 않습니다.");
        }

        int paid = Math.max(
                group.getCurrentQuantity(),
                orders.sumQuantityByGroupPurchaseIdAndStatus(
                        group.getId(),
                        Order.Status.PAID
                )
        );

        int pending = orders.sumActivePendingQuantity(
                group.getId(),
                now
        );

        // pending에는 현재 주문도 이미 포함되어 있습니다.
        if ((long) paid + pending > group.getMaxQuantity()) {
            throw conflict("공동구매 가능 수량을 초과했습니다.");
        }

        Integer limit = group.getLimitPerUser();

        if (limit != null && limit > 0
                && orders.sumUserActiveQuantity(
                group.getId(),
                order.getUserId(),
                now
        ) > limit) {
            throw conflict("1인당 구매 가능 수량을 초과했습니다.");
        }
    }

    private Attempt toAttempt(Order order, Long userId) {
        return new Attempt(
                order.getId(),
                userId,
                order.getPaymentOrderRef(),
                order.getPaymentKey(),
                order.getTotalPrice(),
                order.getStatus() == Order.Status.PAID
        );
    }

    private void validateStoredAttempt(
            Order order,
            Attempt attempt
    ) {
        if (!Objects.equals(
                order.getPaymentOrderRef(),
                attempt.orderRef()
        ) || !Objects.equals(
                order.getPaymentKey(),
                attempt.paymentKey()
        ) || order.getTotalPrice() != attempt.amount()) {
            throw conflict("저장된 결제 정보가 일치하지 않습니다.");
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
                && attempt.amount() == receipt.totalAmount()
                && "KRW".equals(receipt.currency());
    }

    private Result paidResult() {
        return new Result("PAID", "결제가 완료되었습니다.");
    }

    private ResponseStatusException badRequest(String message) {
        return new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                message
        );
    }

    private ResponseStatusException conflict(String message) {
        return new ResponseStatusException(
                HttpStatus.CONFLICT,
                message
        );
    }
}