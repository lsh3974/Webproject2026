package com.siheung.hanbaguni.payment;

import com.siheung.hanbaguni.entity.Order;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

class OrderRefundStateTest {

    @Test
    void 결제완료_주문은_환불요청후_환불처리중이_된다() {
        Order order = paidOrder();
        String requestId = UUID.randomUUID().toString();

        boolean changed = order.beginRefund(
                requestId,
                "고객 요청에 의한 취소"
        );

        assertThat(changed).isTrue();
        assertThat(order.getStatus())
                .isEqualTo(Order.Status.REFUND_PENDING);
        assertThat(order.getRefundRequestId()).isEqualTo(requestId);
        assertThat(order.getCancelReason())
                .isEqualTo("고객 요청에 의한 취소");
    }

    @Test
    void 같은_환불요청을_반복해도_사유와_요청번호를_변경하지_않는다() {
        Order order = paidOrder();
        String requestId = UUID.randomUUID().toString();

        order.beginRefund(requestId, "최초 환불 사유"); // beginRefund 메서드 싹다 에러

        boolean changed = order.beginRefund(
                requestId,
                "재요청에서 전달한 다른 사유"
        );

        assertThat(changed).isFalse();
        assertThat(order.getStatus())
                .isEqualTo(Order.Status.REFUND_PENDING);
        assertThat(order.getRefundRequestId()).isEqualTo(requestId);
        assertThat(order.getCancelReason())
                .isEqualTo("최초 환불 사유");
    }

    @Test
    void 다른_환불요청번호로_기존_요청을_덮어쓸_수_없다() {
        Order order = paidOrder();
        String originalId = UUID.randomUUID().toString();

        order.beginRefund(originalId, "고객 취소");

        assertThatThrownBy(() -> order.beginRefund(
                UUID.randomUUID().toString(),
                "다른 요청"
        )).isInstanceOf(IllegalStateException.class);

        assertThat(order.getRefundRequestId()).isEqualTo(originalId);
        assertThat(order.getStatus())
                .isEqualTo(Order.Status.REFUND_PENDING);
    }

    @Test
    void 결제대기와_승인중_주문은_환불을_시작할_수_없다() {
        Order pending = pendingOrder();

        assertThatThrownBy(() -> pending.beginRefund(
                UUID.randomUUID().toString(),
                "취소"
        )).isInstanceOf(IllegalStateException.class);

        Order approving = pendingOrder();
        approving.beginPayment(LocalDateTime.now());

        assertThatThrownBy(() -> approving.beginRefund(
                UUID.randomUUID().toString(),
                "취소"
        )).isInstanceOf(IllegalStateException.class);

        assertThat(pending.getStatus())
                .isEqualTo(Order.Status.PENDING);
        assertThat(approving.getStatus())
                .isEqualTo(Order.Status.PAYMENT_PENDING);
    }

    @Test
    void 환불완료는_한번만_반영한다() {
        Order order = paidOrder();
        String requestId = UUID.randomUUID().toString();

        order.beginRefund(requestId, "모집 실패");

        assertThat(order.completeRefund(requestId)).isTrue(); // completeRefund 메서드 싹다 에러
        assertThat(order.completeRefund(requestId)).isFalse();

        assertThat(order.getStatus())
                .isEqualTo(Order.Status.REFUNDED);

        // 완료된 동일 요청을 다시 시작해도 상태가 되돌아가지 않습니다.
        assertThat(order.beginRefund(requestId, "모집 실패"))
                .isFalse();

        assertThat(order.getStatus())
                .isEqualTo(Order.Status.REFUNDED);
    }

    @Test
    void 다른_요청번호로_환불완료_처리할_수_없다() {
        Order order = paidOrder();
        String requestId = UUID.randomUUID().toString();

        order.beginRefund(requestId, "고객 취소");

        assertThatThrownBy(() -> order.completeRefund(
                UUID.randomUUID().toString()
        )).isInstanceOf(IllegalStateException.class);

        assertThat(order.getStatus())
                .isEqualTo(Order.Status.REFUND_PENDING);
    }

    @Test
    void 환불요청_없이_바로_환불완료로_변경할_수_없다() {
        Order order = paidOrder();

        assertThatThrownBy(() -> order.completeRefund(
                UUID.randomUUID().toString()
        )).isInstanceOf(IllegalStateException.class);

        assertThat(order.getStatus())
                .isEqualTo(Order.Status.PAID);
    }

    private Order pendingOrder() {
        return new Order(
                null,
                null,
                2,
                1000,
                2000,
                LocalDateTime.now().plusMinutes(15)
        );
    }

    private Order paidOrder() {
        Order order = pendingOrder();

        order.beginPayment(LocalDateTime.now());
        order.markPaid("test-payment-" + UUID.randomUUID());

        return order;
    }
}