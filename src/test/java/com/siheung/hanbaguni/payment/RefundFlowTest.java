package com.siheung.hanbaguni.payment;

import com.siheung.hanbaguni.entity.GroupPurchase;
import com.siheung.hanbaguni.entity.Order;
import com.siheung.hanbaguni.entity.Product;
import com.siheung.hanbaguni.entity.Store;
import com.siheung.hanbaguni.entity.User;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.UUID;

import static com.siheung.hanbaguni.payment.RefundData.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:refund_flow;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
class RefundFlowTest {

    @Autowired
    RefundService refunds;

    @Autowired
    GroupClosingTransactions closing;

    @Autowired
    GroupClosingJob closingJob;

    @Autowired
    PaymentService payments;

    @Autowired
    EntityManager em;

    @Autowired
    TransactionTemplate tx;

    @MockitoBean
    TossPaymentGateway gateway;

    @Autowired
    com.siheung.hanbaguni.service.GroupPurchaseService sellerGroups;

    Fixture fixture;

    @BeforeEach
    void setUp() {
        fixture = createFixture();
    }

    @Test
    void 마감된_모집의_결제주문은_환불되고_다시_실행해도_중복취소하지_않는다() {
        LocalDateTime deadline = tx.execute(status ->
                em.find(GroupPurchase.class, fixture.groupId()).getEndAt()
        );

        when(gateway.findForRefund(fixture.paymentKey()))
                .thenReturn(paidReceipt());
        when(gateway.cancel(any()))
                .thenReturn(refundedReceipt());

        closingJob.runOnce(deadline);

        tx.executeWithoutResult(status -> {
            assertThat(em.find(GroupPurchase.class, fixture.groupId()).getStatus())
                    .isEqualTo(GroupPurchase.Status.FAILED);
            assertThat(em.find(Order.class, fixture.orderId()).getStatus())
                    .isEqualTo(Order.Status.REFUNDED);
        });
        assertThat(snapshot().currentQuantity()).isZero();

        closingJob.runOnce(deadline.plusMinutes(1));

        verify(gateway, times(1)).cancel(argThat(attempt ->
                attempt.orderId().equals(fixture.orderId())
        ));
    }

    @Test
    void 마감된_모집은_실패로_저장하고_미결제_예약만_만료한다() {
        LocalDateTime deadline = tx.execute(status ->
                em.find(GroupPurchase.class, fixture.groupId()).getEndAt()
        );

        Long pendingId = tx.execute(status -> {
            Order pending = new Order(
                    em.find(User.class, fixture.buyerId()),
                    em.find(GroupPurchase.class, fixture.groupId()),
                    1, 1000, 1000, deadline
            );
            em.persist(pending);
            em.flush();
            return pending.getId();
        });

        assertThat(closing.closeIfDue(fixture.groupId(), deadline)).isTrue();
        assertThat(closing.closeIfDue(fixture.groupId(), deadline)).isFalse();

        tx.executeWithoutResult(status -> {
            assertThat(em.find(GroupPurchase.class, fixture.groupId()).getStatus())
                    .isEqualTo(GroupPurchase.Status.FAILED);
            assertThat(em.find(Order.class, pendingId).getStatus())
                    .isEqualTo(Order.Status.EXPIRED);
            assertThat(em.find(Order.class, fixture.orderId()).getStatus())
                    .isEqualTo(Order.Status.PAID);
        });
    }

    @Test
    void 승인중인_주문이_DB에_있으면_마감을_보류한다() {
        LocalDateTime deadline = tx.execute(status -> {
            GroupPurchase group = em.find(GroupPurchase.class, fixture.groupId());
            Order approving = new Order(
                    em.find(User.class, fixture.buyerId()),
                    group, 1, 1000, 1000, group.getEndAt()
            );
            approving.assignPaymentOrderRef(UUID.randomUUID().toString());
            approving.beginPayment(group.getEndAt().minusMinutes(1));
            em.persist(approving);
            return group.getEndAt();
        });

        assertThat(closing.closeIfDue(fixture.groupId(), deadline)).isFalse();

        tx.executeWithoutResult(status ->
                assertThat(em.find(GroupPurchase.class, fixture.groupId()).getStatus())
                        .isEqualTo(GroupPurchase.Status.OPEN)
        );
    }

    @Test
    void 모집중인_본인주문은_전액환불하고_수량을_차감한다() {
        when(gateway.findForRefund(fixture.paymentKey()))
                .thenReturn(paidReceipt());

        when(gateway.cancel(any()))
                .thenReturn(refundedReceipt());

        Result result = refunds.cancelMyOrder(
                fixture.orderId(),
                fixture.buyerId(),
                "고객 요청 취소"
        );

        assertThat(result.status()).isEqualTo("REFUNDED");

        Snapshot saved = snapshot();

        assertThat(saved.status()).isEqualTo(Order.Status.REFUNDED);
        assertThat(saved.currentQuantity()).isZero();
        assertThat(saved.refundRequestId()).isNotBlank();
        assertThat(saved.reason()).isEqualTo("고객 요청 취소");

        ArgumentCaptor<Attempt> captor =
                ArgumentCaptor.forClass(Attempt.class);

        verify(gateway).cancel(captor.capture());

        Attempt sent = captor.getValue();

        assertThat(sent.amount()).isEqualTo(2000);
        assertThat(sent.paymentKey()).isEqualTo(fixture.paymentKey());
        assertThat(sent.orderRef()).isEqualTo(fixture.orderRef());
        assertThat(sent.refundRequestId())
                .isEqualTo(saved.refundRequestId());
    }

    @Test
    void 다른_사용자는_주문을_환불할_수_없다() {
        assertThatThrownBy(() -> refunds.cancelMyOrder(
                fixture.orderId(),
                fixture.otherUserId(),
                "다른 사람의 요청"
        )).isInstanceOf(ResponseStatusException.class);

        assertThat(snapshot().status()).isEqualTo(Order.Status.PAID);
        assertThat(snapshot().currentQuantity()).isEqualTo(2);

        verify(gateway, never()).findForRefund(anyString());
        verify(gateway, never()).cancel(any());
    }

    @Test
    void 모집성공_이후에는_구매자가_직접_취소할_수_없다() {
        tx.executeWithoutResult(status -> {
            GroupPurchase group = em.find(
                    GroupPurchase.class,
                    fixture.groupId()
            );

            group.updateStatus(GroupPurchase.Status.SUCCESS);
        });

        assertThatThrownBy(() -> refunds.cancelMyOrder(
                fixture.orderId(),
                fixture.buyerId(),
                "고객 취소"
        )).isInstanceOf(ResponseStatusException.class);

        assertThat(snapshot().status()).isEqualTo(Order.Status.PAID);
        assertThat(snapshot().currentQuantity()).isEqualTo(2);

        verify(gateway, never()).findForRefund(anyString());
        verify(gateway, never()).cancel(any());
    }

    @Test
    void 환불완료_요청을_반복해도_수량과_외부취소는_중복처리하지_않는다() {
        when(gateway.findForRefund(fixture.paymentKey()))
                .thenReturn(paidReceipt());

        when(gateway.cancel(any()))
                .thenReturn(refundedReceipt());

        refunds.cancelMyOrder(
                fixture.orderId(),
                fixture.buyerId(),
                "최초 취소 사유"
        );

        String originalRequestId = snapshot().refundRequestId();

        Result repeated = refunds.cancelMyOrder(
                fixture.orderId(),
                fixture.buyerId(),
                "변경된 사유"
        );

        Result retried = refunds.retryMyRefund(
                fixture.orderId(),
                fixture.buyerId()
        );

        assertThat(repeated.status()).isEqualTo("REFUNDED");
        assertThat(retried.status()).isEqualTo("REFUNDED");

        Snapshot saved = snapshot();

        assertThat(saved.currentQuantity()).isZero();
        assertThat(saved.refundRequestId()).isEqualTo(originalRequestId);
        assertThat(saved.reason()).isEqualTo("최초 취소 사유");

        verify(gateway, times(1)).cancel(any());
        verify(gateway, times(1))
                .findForRefund(fixture.paymentKey());
    }

    @Test
    void 취소응답이_유실되어도_조회에서_환불완료를_확인하면_복구한다() {
        // 취소 전 조회는 결제 완료, 취소 후 조회는 환불 완료
        when(gateway.findForRefund(fixture.paymentKey()))
                .thenReturn(
                        paidReceipt(),
                        refundedReceipt()
                );

        when(gateway.cancel(any()))
                .thenThrow(new ResourceAccessException("timeout"));

        Result result = refunds.cancelMyOrder(
                fixture.orderId(),
                fixture.buyerId(),
                "고객 취소"
        );

        assertThat(result.status()).isEqualTo("REFUNDED");
        assertThat(snapshot().status()).isEqualTo(Order.Status.REFUNDED);
        assertThat(snapshot().currentQuantity()).isZero();

        verify(gateway, times(1)).cancel(any());
        verify(gateway, times(2))
                .findForRefund(fixture.paymentKey());
    }

    @Test
    void 환불재시도는_같은_요청번호를_쓰고_수량을_다시_차감하지_않는다() {
        // 조회에는 계속 결제 금액이 남아 있는 것으로 나타납니다.
        when(gateway.findForRefund(fixture.paymentKey()))
                .thenReturn(paidReceipt());

        when(gateway.cancel(any()))
                .thenThrow(new ResourceAccessException("timeout"));

        Result first = refunds.cancelMyOrder(
                fixture.orderId(),
                fixture.buyerId(),
                "고객 취소"
        );

        String originalRequestId = snapshot().refundRequestId();

        assertThat(first.status()).isEqualTo("REFUND_PENDING");
        assertThat(snapshot().status())
                .isEqualTo(Order.Status.REFUND_PENDING);
        assertThat(snapshot().currentQuantity()).isZero();

        Result second = refunds.retryMyRefund(
                fixture.orderId(),
                fixture.buyerId()
        );

        assertThat(second.status()).isEqualTo("REFUND_PENDING");
        assertThat(snapshot().currentQuantity()).isZero();

        // 이후 취소 요청이 정상 응답을 반환하도록 변경합니다.
        doReturn(refundedReceipt())
                .when(gateway)
                .cancel(any());

        Result third = refunds.retryMyRefund(
                fixture.orderId(),
                fixture.buyerId()
        );

        assertThat(third.status()).isEqualTo("REFUNDED");
        assertThat(snapshot().currentQuantity()).isZero();

        ArgumentCaptor<Attempt> captor =
                ArgumentCaptor.forClass(Attempt.class);

        verify(gateway, times(3)).cancel(captor.capture());

        assertThat(captor.getAllValues())
                .extracting(Attempt::refundRequestId)
                .containsOnly(originalRequestId);

        assertThat(captor.getAllValues())
                .extracting(Attempt::amount)
                .containsOnly(2000);

        assertThat(captor.getAllValues())
                .extracting(Attempt::reason)
                .containsOnly("고객 취소");
    }

    @Test
    void 조회금액이_주문과_다르면_취소요청과_환불완료를_진행하지_않는다() {
        Receipt wrongReceipt = new Receipt(
                fixture.paymentKey(),
                fixture.orderRef(),
                3000L,
                3000L,
                "KRW",
                "DONE"
        );

        when(gateway.findForRefund(fixture.paymentKey()))
                .thenReturn(wrongReceipt);

        Result result = refunds.cancelMyOrder(
                fixture.orderId(),
                fixture.buyerId(),
                "고객 취소"
        );

        assertThat(result.status()).isEqualTo("REFUND_PENDING");
        assertThat(snapshot().status())
                .isEqualTo(Order.Status.REFUND_PENDING);

        // 취소 접수 수량은 모집 성공 수량에서 제외됩니다.
        assertThat(snapshot().currentQuantity()).isZero();

        verify(gateway, never()).cancel(any());
    }

    @Test
    void 첫_조회부터_통신이_실패하면_환불대기를_유지한다() {
        when(gateway.findForRefund(fixture.paymentKey()))
                .thenThrow(new ResourceAccessException("timeout"));

        Result result = refunds.cancelMyOrder(
                fixture.orderId(),
                fixture.buyerId(),
                "고객 취소"
        );

        assertThat(result.status()).isEqualTo("REFUND_PENDING");
        assertThat(snapshot().status())
                .isEqualTo(Order.Status.REFUND_PENDING);
        assertThat(snapshot().currentQuantity()).isZero();

        verify(gateway, never()).cancel(any());
    }

    @Test
    void 모집실패_주문은_내부처리로_전액환불한다() {
        tx.executeWithoutResult(status -> {
            GroupPurchase group = em.find(
                    GroupPurchase.class,
                    fixture.groupId()
            );

            group.updateStatus(GroupPurchase.Status.FAILED);
        });

        when(gateway.findForRefund(fixture.paymentKey()))
                .thenReturn(paidReceipt());

        when(gateway.cancel(any()))
                .thenReturn(refundedReceipt());

        Result result = refunds.refundFailedOrder(fixture.orderId());

        assertThat(result.status()).isEqualTo("REFUNDED");
        assertThat(snapshot().status()).isEqualTo(Order.Status.REFUNDED);
        assertThat(snapshot().currentQuantity()).isZero();
        assertThat(snapshot().reason())
                .isEqualTo("공동구매 목표 수량 미달로 인한 전액 환불");
    }

    @Test
    void 주문이_있는_모집은_판매자가_마감하거나_목표를_바꿀_수_없다() {
        Long storeId = tx.execute(s -> em.find(GroupPurchase.class, fixture.groupId()).getStore().getId());
        var form = tx.execute(s -> com.siheung.hanbaguni.dto.GroupPurchaseForm.from(
                em.find(GroupPurchase.class, fixture.groupId())));
        form.setTargetQuantity(2);
        assertThatThrownBy(() -> sellerGroups.updateStatus(storeId, fixture.groupId(),
                fixture.otherUserId(), GroupPurchase.Status.CLOSED))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("409");
        assertThatThrownBy(() -> sellerGroups.updateGroupPurchase(storeId, fixture.groupId(),
                fixture.otherUserId(), form)).isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("409");
    }

    @Test
    void 마감때_기존_승인을_조회해_목표에_도달하면_성공한다() {
        String ref = UUID.randomUUID().toString();
        String key = "test-" + ref;
        LocalDateTime deadline = tx.execute(s -> {
            GroupPurchase group = em.find(GroupPurchase.class, fixture.groupId());
            Order order = new Order(em.find(User.class, fixture.buyerId()), group,
                    3, 1000, 3000, group.getEndAt());
            order.assignPaymentOrderRef(ref);
            order.beginPayment(group.getEndAt().minusMinutes(1));
            order.bindPaymentKey(key);
            em.persist(order);
            return group.getEndAt();
        });
        closingJob.runOnce(deadline);
        tx.executeWithoutResult(s -> assertThat(em.find(GroupPurchase.class, fixture.groupId()).getStatus())
                .isEqualTo(GroupPurchase.Status.OPEN));
        when(gateway.findByOrderId(ref)).thenReturn(new PaymentData.Receipt(key, ref, 3000, "KRW", "DONE"));
        Long orderId = tx.execute(s -> em.createQuery("select o.id from Order o where o.paymentOrderRef = :ref", Long.class)
                .setParameter("ref", ref).getSingleResult());
        payments.recover(orderId, fixture.buyerId(), deadline);
        closingJob.runOnce(deadline);
        tx.executeWithoutResult(s -> assertThat(em.find(GroupPurchase.class, fixture.groupId()).getStatus())
                .isEqualTo(GroupPurchase.Status.SUCCESS));
        verify(gateway, never()).confirm(any());
    }

    @Test
    void 이미_접수한_고객환불은_모집성공_이후에도_자동복구한다() {
        when(gateway.findForRefund(fixture.paymentKey()))
                .thenThrow(new ResourceAccessException("timeout")).thenReturn(refundedReceipt());
        refunds.cancelMyOrder(fixture.orderId(), fixture.buyerId(), "고객 취소");
        String originalId = snapshot().refundRequestId();
        tx.executeWithoutResult(s -> {
            GroupPurchase group = em.find(GroupPurchase.class, fixture.groupId());
            group.addPaidQuantity(group.getTargetQuantity());
            group.succeedIfTargetReached();
        });
        closingJob.runOnce(LocalDateTime.now());
        assertThat(snapshot().status()).isEqualTo(Order.Status.REFUNDED);
        assertThat(snapshot().refundRequestId()).isEqualTo(originalId);
        assertThat(snapshot().currentQuantity()).isEqualTo(5);
        verify(gateway, never()).cancel(argThat(a -> a.orderId().equals(fixture.orderId())));
    }

    private Receipt paidReceipt() {
        return new Receipt(
                fixture.paymentKey(),
                fixture.orderRef(),
                2000L,
                2000L,
                "KRW",
                "DONE"
        );
    }

    private Receipt refundedReceipt() {
        return new Receipt(
                fixture.paymentKey(),
                fixture.orderRef(),
                2000L,
                0L,
                "KRW",
                "CANCELED"
        );
    }

    private Snapshot snapshot() {
        return tx.execute(status -> {
            Order order = em.find(Order.class, fixture.orderId());

            return new Snapshot(
                    order.getStatus(),
                    order.getGroupPurchase().getCurrentQuantity(),
                    order.getRefundRequestId(),
                    order.getCancelReason()
            );
        });
    }

    private Fixture createFixture() {
        return tx.execute(status -> {
            String suffix = UUID.randomUUID().toString();

            User seller = new User(
                    "seller-" + suffix + "@example.test",
                    "unused-hash",
                    "판매자-" + suffix
            );

            User buyer = new User(
                    "buyer-" + suffix + "@example.test",
                    "unused-hash",
                    "구매자-" + suffix
            );

            em.persist(seller);
            em.persist(buyer);

            Store store = new Store(
                    seller,
                    "환불 테스트 매장",
                    null,
                    "031-000-0000",
                    "시흥시",
                    null,
                    null
            );
            em.persist(store);

            Product product = new Product(
                    store,
                    "환불 테스트 상품",
                    "식품",
                    2000,
                    "개",
                    "테스트용",
                    null
            );
            em.persist(product);

            LocalDateTime now = LocalDateTime.now();

            GroupPurchase group = new GroupPurchase(
                    store,
                    product,
                    "환불 테스트 공동구매",
                    1000,
                    5,
                    20,
                    10,
                    now.minusHours(1),
                    now.plusDays(1),
                    now.plusDays(2),
                    now.plusDays(3),
                    null,
                    null,
                    GroupPurchase.Status.OPEN
            );
            em.persist(group);

            String orderRef = UUID.randomUUID().toString();
            String paymentKey = "test-payment-" + suffix;

            Order order = new Order(
                    buyer,
                    group,
                    2,
                    1000,
                    2000,
                    now.plusMinutes(15)
            );

            order.assignPaymentOrderRef(orderRef);
            order.beginPayment(now);
            order.markPaid(paymentKey);
            group.addPaidQuantity(2);

            em.persist(order);
            em.flush();

            return new Fixture(
                    order.getId(),
                    group.getId(),
                    buyer.getId(),
                    seller.getId(),
                    orderRef,
                    paymentKey
            );
        });
    }

    record Fixture(
            Long orderId,
            Long groupId,
            Long buyerId,
            Long otherUserId,
            String orderRef,
            String paymentKey
    ) {
    }

    record Snapshot(
            Order.Status status,
            int currentQuantity,
            String refundRequestId,
            String reason
    ) {
    }
}