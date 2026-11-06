package com.siheung.hanbaguni.payment;

import com.siheung.hanbaguni.entity.GroupPurchase;
import com.siheung.hanbaguni.entity.Order;
import com.siheung.hanbaguni.entity.Product;
import com.siheung.hanbaguni.entity.Store;
import com.siheung.hanbaguni.entity.User;
import com.siheung.hanbaguni.repository.GroupPurchaseRepository;
import com.siheung.hanbaguni.repository.OrderRepository;
import com.siheung.hanbaguni.service.PickupService;
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
    private final PickupService pickupService;

    public PaymentTransactions(
            OrderRepository orders,
            GroupPurchaseRepository groups,
            PickupService pickupService
    ) {
        this.orders = orders;
        this.groups = groups;
        this.pickupService = pickupService;
    }

    // 결제창에 전달할 주문번호와 금액을 준비합니다.
    public Prepared prepare(Long orderId, Long userId) {
        Order order = lockOwnedOrder(orderId, userId);

        validatePendingOrder(order);

        order.prepareCheckout(UUID.randomUUID().toString(), LocalDateTime.now());

        return new Prepared(
                order.getId(),
                order.getPaymentOrderRef(),
                order.getTotalPrice(),
                "한바구니 주문 " + order.getId()
        );
    }

    // 최초 요청에만 Toss 승인 API 호출 권한을 부여합니다.
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
            throw badRequest(
                    "주문번호 또는 결제 금액이 올바르지 않습니다."
            );
        }

        boolean confirmationAllowed = false;

        if (order.getStatus() == Order.Status.PENDING) {
            validatePendingOrder(order);

            order.beginPayment(LocalDateTime.now());
            order.bindPaymentKey(request.paymentKey());
            order.deferPaymentRecovery(LocalDateTime.now());

            confirmationAllowed = true;

        } else if (order.getStatus() == Order.Status.PAYMENT_PENDING
                || order.getStatus() == Order.Status.PAID) {

            if (!Objects.equals(
                    order.getPaymentKey(),
                    request.paymentKey()
            )) {
                throw conflict(
                        "이미 다른 결제 요청이 처리되었습니다. 주문 상세에서 결과를 확인해주세요."
                );
            }

        } else {
            throw conflict("결제할 수 없는 주문 상태입니다.");
        }

        return toAttempt(order, userId, confirmationAllowed);
    }

    // 재확인에서는 승인 요청 없이 조회에 필요한 정보만 반환합니다.
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

        return toAttempt(order, userId, false);
    }

    // 최초 승인 트랜잭션이 끝났고 복구 대기 시간이 지난 주문만 가져옵니다.
    public Attempt claimRecovery(Long orderId, Long userId, LocalDateTime now) {
        Order order = lockOwnedOrder(orderId, userId);
        if (order.getStatus() != Order.Status.PAYMENT_PENDING
                || (order.getPaymentRetryAt() != null && order.getPaymentRetryAt().isAfter(now))) {
            return null;
        }
        order.deferPaymentRecovery(now);
        // 오래된 주문은 조회만 허용합니다. 최초 승인과 동일한 멱등키의 유효 범위를 넘겨 재전송하지 않습니다.
        boolean canReplay = order.getPaymentRejectionCode() == null
                && order.getCreatedAt().isAfter(now.minusDays(14));
        return toAttempt(order, userId, canReplay);
    }

    // Toss의 승인 완료 결과를 확인한 뒤 결제 완료로 변경합니다.
    public Result complete(Attempt attempt, Receipt receipt) {
        Order order = lockOwnedOrder(
                attempt.orderId(),
                attempt.userId()
        );

        validateStoredAttempt(order, attempt);

        if (order.getStatus() == Order.Status.PAID) {
            // 이미 결제 완료된 경우에도 수령증 존재 여부 보장
            pickupService.createOrGetPickupForOrder(order);
            return paidResult();
        }

        if (order.getStatus() != Order.Status.PAYMENT_PENDING) {
            throw conflict(
                    "현재 주문 상태에서 결제를 완료할 수 없습니다."
            );
        }

        if (!matches(attempt, receipt)
                || !"DONE".equals(receipt.status())) {
            throw conflict(
                    "결제 승인 결과가 주문 정보와 일치하지 않습니다."
            );
        }

        order.markPaid(attempt.paymentKey());

        GroupPurchase group = order.getGroupPurchase();
        group.addPaidQuantity(order.getQuantity());

        // 목표에 도달한 순간 모집 성공으로 전환합니다.
        boolean becameSuccessful = group.succeedIfTargetReached();

        if (becameSuccessful) {
            // 아직 승인 처리를 시작하지 않은 예약만 만료시킵니다.
            for (Order pendingOrder :
                    orders.findPendingOrdersForGroupWithLock(group.getId())) {
                pendingOrder.expireForGroupSuccess();
            }
        }

        // 결제가 PAID로 확정되는 시점에 수령증(Pickup)을 즉시 생성하여 판매자 수령 목록에 반영
        pickupService.createOrGetPickupForOrder(order);

        return paidResult();
    }

    // Toss의 결제 객체에서 실패 또는 만료가 확인된 경우입니다.
    public Result fail(Attempt attempt, Receipt receipt) {
        Order order = lockOwnedOrder(
                attempt.orderId(),
                attempt.userId()
        );

        validateStoredAttempt(order, attempt);

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

        } else if (order.getStatus() != Order.Status.EXPIRED) {
            throw conflict(
                    "현재 주문 상태에서 결제 실패 처리할 수 없습니다."
            );
        }

        return new Result(
                "EXPIRED",
                "결제가 완료되지 않았습니다. 새 주문을 만들어주세요."
        );
    }

    // 최초 승인 요청에서 받은 명시적인 거절 응답을 저장합니다.
    public Attempt recordRejection(
            Attempt attempt,
            TossPaymentException exception
    ) {
        if (!attempt.confirmationAllowed() // 에러
                || !PaymentErrorPolicy.isDefiniteRejection(exception)) {
            throw conflict("확정적인 승인 거절 응답이 아닙니다.");
        }

        Order order = lockOwnedOrder(
                attempt.orderId(),
                attempt.userId()
        );

        validateStoredAttempt(order, attempt);

        if (order.getStatus() == Order.Status.PAID) {
            return toAttempt(order, attempt.userId(), false);
        }

        if (order.getStatus() != Order.Status.PAYMENT_PENDING) {
            throw conflict(
                    "승인 거절을 기록할 수 없는 주문 상태입니다."
            );
        }

        order.recordPaymentRejection(exception.getErrorCode());

        return toAttempt(order, attempt.userId(), false);
    }

    // 승인 거절 기록과 주문번호 조회 결과를 함께 확인합니다.
    public Result expireRejectedPayment(
            Attempt attempt,
            TossPaymentException lookupException
    ) {
        if (!PaymentErrorPolicy.isPaymentNotFound(lookupException)) {
            throw conflict(
                    "결제 정보가 없다는 조회 결과가 필요합니다."
            );
        }

        Order order = lockOwnedOrder(
                attempt.orderId(),
                attempt.userId()
        );

        validateStoredAttempt(order, attempt);

        if (order.getStatus() == Order.Status.PAID) {
            return paidResult();
        }

        if (!PaymentErrorPolicy.isStoredRejection(order.getPaymentRejectionCode())) {
            throw conflict(
                    "승인 거절 기록이 없어 주문을 만료시킬 수 없습니다."
            );
        }

        if (order.getStatus() == Order.Status.PAYMENT_PENDING) {
            order.failPayment();

        } else if (order.getStatus() != Order.Status.EXPIRED) {
            throw conflict("만료 처리할 수 없는 주문 상태입니다.");
        }

        return new Result(
                "EXPIRED",
                "결제가 승인되지 않아 주문을 종료했습니다. 새 주문으로 다시 진행해주세요."
        );
    }

    // 모집 → 주문 순서로 잠근 뒤 주문 소유자와 회원 상태를 확인합니다.
    private Order lockOwnedOrder(Long orderId, Long userId) {
        Long groupId = orders.findPaymentGroupId(orderId, userId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "주문을 찾을 수 없습니다."
                ));

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

        return order;
    }

    // 승인을 시작하기 전에 예약·모집·수량·금액을 확인합니다.
    private void validatePendingOrder(Order order) {
        LocalDateTime now = LocalDateTime.now();

        if (order.getUser().getStatus() != User.Status.ACTIVE) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "사용할 수 없는 회원입니다."
            );
        }

        if (order.getStatus() != Order.Status.PENDING) {
            throw conflict(
                    "결제 대기 상태의 주문만 결제할 수 있습니다."
            );
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
            throw conflict(
                    "본인 매장의 공동구매는 결제할 수 없습니다."
            );
        }

        if (order.getQuantity() == null
                || order.getQuantity() <= 0
                || order.getUnitPrice() == null
                || order.getUnitPrice() <= 0
                || order.getTotalPrice() == null
                || order.getTotalPrice() <= 0
                || (long) order.getQuantity() * order.getUnitPrice()
                != order.getTotalPrice()) {
            throw conflict(
                    "주문의 수량 또는 금액이 올바르지 않습니다."
            );
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

        // pending에는 현재 주문도 포함되어 있습니다.
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

    private Attempt toAttempt(
            Order order,
            Long userId,
            boolean confirmationAllowed
    ) {
        return new Attempt(
                order.getId(),
                userId,
                order.getPaymentOrderRef(),
                order.getPaymentKey(),
                order.getTotalPrice(),
                order.getStatus() == Order.Status.PAID,
                confirmationAllowed,
                order.getPaymentRejectionCode() // 에러
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
