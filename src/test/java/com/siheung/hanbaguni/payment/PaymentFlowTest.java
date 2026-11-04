package com.siheung.hanbaguni.payment;

import com.siheung.hanbaguni.entity.GroupPurchase;
import com.siheung.hanbaguni.entity.Order;
import com.siheung.hanbaguni.entity.Product;
import com.siheung.hanbaguni.entity.Store;
import com.siheung.hanbaguni.entity.User;
import com.siheung.hanbaguni.dto.GroupPurchaseForm;
import com.siheung.hanbaguni.repository.OrderRepository;
import com.siheung.hanbaguni.service.GroupPurchaseService;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.UUID;

import static com.siheung.hanbaguni.payment.PaymentData.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;


@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:payment_flow;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
class PaymentFlowTest {

    @Autowired
    PaymentService payments;

    @Autowired
    com.siheung.hanbaguni.service.OrderReservationService reservations;

    @Autowired
    EntityManager em;

    @Autowired
    TransactionTemplate tx;

    @MockitoBean
    TossPaymentGateway gateway;

    Fixture fixture;
    Prepared prepared;
    String paymentKey;

    @BeforeEach
    void setUp() {
        fixture = createFixture();
        prepared = payments.prepare(
                fixture.orderId(),
                fixture.buyerId()
        );

        paymentKey = "test-payment-" + UUID.randomUUID();
    }

    @Test
    void 정상_승인하면_결제완료와_수량을_저장한다() {
        when(gateway.confirm(any()))
                .thenReturn(receipt("DONE", prepared.amount()));

        Result result = confirm(prepared.amount());

        assertThat(result.status()).isEqualTo("PAID");
        assertThat(snapshot().status()).isEqualTo(Order.Status.PAID);
        assertThat(snapshot().currentQuantity()).isEqualTo(2);
    }

    @Test
    void 같은_승인을_다시_요청해도_수량은_한번만_증가한다() {
        when(gateway.confirm(any()))
                .thenReturn(receipt("DONE", prepared.amount()));

        confirm(prepared.amount());
        confirm(prepared.amount());

        assertThat(snapshot().currentQuantity()).isEqualTo(2);

        verify(gateway, times(1)).confirm(any());
    }

    @Test
    void 다른_사용자의_주문은_승인할_수_없다() {
        assertThatThrownBy(() -> payments.confirm(
                fixture.orderId(),
                fixture.sellerId(),
                request(prepared.amount())
        )).isInstanceOf(ResponseStatusException.class);

        verify(gateway, never()).find(anyString());

        assertThat(snapshot().status()).isEqualTo(Order.Status.PENDING);
    }

    @Test
    void 브라우저에서_변조한_금액은_거절한다() {
        assertThatThrownBy(() -> confirm(1))
                .isInstanceOf(ResponseStatusException.class);

        verify(gateway, never()).findByOrderId(anyString());

        assertThat(snapshot().status()).isEqualTo(Order.Status.PENDING);
    }

    @Test
    void 예약이_만료되면_승인을_시작하지_않는다() {
        tx.executeWithoutResult(status -> {
            em.createQuery("""
                    update Order o
                    set o.reservedUntil = :deadline
                    where o.id = :id
                    """)
                    .setParameter(
                            "deadline",
                            LocalDateTime.now().minusMinutes(1)
                    )
                    .setParameter("id", fixture.orderId())
                    .executeUpdate();
        });

        assertThatThrownBy(() -> confirm(prepared.amount()))
                .isInstanceOf(ResponseStatusException.class);

        verify(gateway, never()).confirm(any());
    }

    @Test
    void 승인응답이_유실되어도_조회결과가_완료면_복구한다() {
        when(gateway.confirm(any()))
                .thenThrow(new ResourceAccessException("timeout"));

        when(gateway.find(paymentKey))
                .thenReturn(receipt("DONE", prepared.amount()));

        Result result = confirm(prepared.amount());

        assertThat(result.status()).isEqualTo("PAID");
        assertThat(snapshot().currentQuantity()).isEqualTo(2);
    }

    @Test
    void 승인과_조회가_모두_실패하면_수량을_유지하고_재확인한다() {
        when(gateway.confirm(any()))
                .thenThrow(new ResourceAccessException("timeout"));

        when(gateway.find(anyString()))
                .thenThrow(new ResourceAccessException("timeout"));

        when(gateway.findByOrderId(anyString()))
                .thenThrow(new ResourceAccessException("timeout"));

        Result first = confirm(prepared.amount());

        assertThat(first.status()).isEqualTo("PAYMENT_PENDING");
        assertThat(snapshot().status())
                .isEqualTo(Order.Status.PAYMENT_PENDING);
        assertThat(snapshot().currentQuantity()).isZero();

        // 재확인은 승인 재요청이 아니라 조회로 복구합니다.
        doReturn(receipt("DONE", prepared.amount()))
                .when(gateway)
                .find(paymentKey);

        Result retried = payments.retry(
                fixture.orderId(),
                fixture.buyerId()
        );

        assertThat(retried.status()).isEqualTo("PAID");
        assertThat(snapshot().currentQuantity()).isEqualTo(2);

        // 처음 요청한 승인 API는 총 한 번만 호출돼야 합니다.
        verify(gateway, times(1)).confirm(any());
    }

    @Test
    void Toss_응답금액이_다르면_결제완료로_저장하지_않는다() {
        when(gateway.confirm(any()))
                .thenReturn(receipt("DONE", 1));

        Result result = confirm(prepared.amount());

        assertThat(result.status()).isEqualTo("PAYMENT_PENDING");
        assertThat(snapshot().status())
                .isEqualTo(Order.Status.PAYMENT_PENDING);
        assertThat(snapshot().currentQuantity()).isZero();
    }

    @Test
    void Toss가_승인실패를_확인하면_주문을_만료처리한다() {
        when(gateway.confirm(any()))
                .thenReturn(receipt("ABORTED", prepared.amount()));

        Result result = confirm(prepared.amount());

        assertThat(result.status()).isEqualTo("EXPIRED");
        assertThat(snapshot().status()).isEqualTo(Order.Status.EXPIRED);
        assertThat(snapshot().currentQuantity()).isZero();
    }

    @ParameterizedTest
    @CsvSource({"400, INVALID_PAYMENT_KEY", "403, REJECT_CARD_COMPANY", "403, REJECT_CARD_PAYMENT"})
    void 승인거절후_주문번호에도_결제가_없으면_종료한다(int statusCode, String rejectionCode) {
        TossPaymentException rejected = new TossPaymentException(
                TossPaymentException.Kind.HTTP_RESPONSE, statusCode, rejectionCode
        );

        TossPaymentException notFound = new TossPaymentException(
                TossPaymentException.Kind.HTTP_RESPONSE,
                404,
                "NOT_FOUND_PAYMENT"
        );

        when(gateway.confirm(any())).thenThrow(rejected);
        when(gateway.find(paymentKey)).thenThrow(notFound);
        when(gateway.findByOrderId(prepared.orderRef()))
                .thenThrow(notFound);

        Result result = confirm(prepared.amount());

        assertThat(result.status()).isEqualTo("EXPIRED");
        assertThat(snapshot().status()).isEqualTo(Order.Status.EXPIRED);
        assertThat(snapshot().currentQuantity()).isZero();

        verify(gateway, times(1)).confirm(any());
    }

    @ParameterizedTest
    @CsvSource({"403, FORBIDDEN_REQUEST", "500, REJECT_CARD_COMPANY"})
    void 확정거절이_아니면_거래없음만으로_만료시키지_않는다(int statusCode, String code) {
        when(gateway.confirm(any())).thenThrow(new TossPaymentException(
                TossPaymentException.Kind.HTTP_RESPONSE, statusCode, code));
        TossPaymentException missing = new TossPaymentException(
                TossPaymentException.Kind.HTTP_RESPONSE, 404, "NOT_FOUND_PAYMENT");
        when(gateway.find(paymentKey)).thenThrow(missing);
        when(gateway.findByOrderId(prepared.orderRef())).thenThrow(missing);

        assertThat(confirm(prepared.amount()).status()).isEqualTo("PAYMENT_PENDING");
        tx.executeWithoutResult(s -> assertThat(em.find(Order.class, fixture.orderId())
                .getPaymentRejectionCode()).isNull());
    }

    @Test
    void 승인응답이_불확실하면_조회결과가_없어도_만료시키지_않는다() {
        TossPaymentException notFound = new TossPaymentException(
                TossPaymentException.Kind.HTTP_RESPONSE,
                404,
                "NOT_FOUND_PAYMENT"
        );

        when(gateway.confirm(any()))
                .thenThrow(new ResourceAccessException("timeout"));

        when(gateway.find(paymentKey)).thenThrow(notFound);
        when(gateway.findByOrderId(prepared.orderRef()))
                .thenThrow(notFound);

        Result result = confirm(prepared.amount());

        assertThat(result.status()).isEqualTo("PAYMENT_PENDING");
        assertThat(snapshot().status())
                .isEqualTo(Order.Status.PAYMENT_PENDING);
        assertThat(snapshot().currentQuantity()).isZero();
    }


    @ParameterizedTest
    @CsvSource({"400, INVALID_PAYMENT_KEY", "403, REJECT_CARD_COMPANY", "403, REJECT_CARD_PAYMENT"})
    void 승인거절_기록을_저장한뒤_조회가_실패해도_재확인으로_종료한다(int statusCode, String rejectionCode) {
        TossPaymentException rejected = new TossPaymentException(
                TossPaymentException.Kind.HTTP_RESPONSE, statusCode, rejectionCode
        );

        TossPaymentException notFound = new TossPaymentException(
                TossPaymentException.Kind.HTTP_RESPONSE,
                404,
                "NOT_FOUND_PAYMENT"
        );

        when(gateway.confirm(any())).thenThrow(rejected);

        when(gateway.find(paymentKey))
                .thenThrow(new ResourceAccessException("timeout"));

        when(gateway.findByOrderId(prepared.orderRef()))
                .thenThrow(new ResourceAccessException("timeout"));

        // 승인 거절은 받았지만 조회가 실패한 첫 번째 요청
        Result first = confirm(prepared.amount());

        assertThat(first.status()).isEqualTo("PAYMENT_PENDING");
        assertThat(snapshot().status())
                .isEqualTo(Order.Status.PAYMENT_PENDING);
        assertThat(snapshot().currentQuantity()).isZero();

        // 거절 근거가 DB에 저장됐는지 확인
        String savedCode = tx.execute(status ->
                em.find(Order.class, fixture.orderId())
                        .getPaymentRejectionCode()
        );

        assertThat(savedCode).isEqualTo(rejectionCode);

        // 통신이 복구되어 결제 정보 없음을 확인할 수 있게 됨
        doThrow(notFound)
                .when(gateway)
                .find(paymentKey);

        doThrow(notFound)
                .when(gateway)
                .findByOrderId(prepared.orderRef());

        Result retried = payments.retry(
                fixture.orderId(),
                fixture.buyerId()
        );

        assertThat(retried.status()).isEqualTo("EXPIRED");
        assertThat(snapshot().status()).isEqualTo(Order.Status.EXPIRED);
        assertThat(snapshot().currentQuantity()).isZero();

        // 재확인하면서 승인 API를 다시 호출하지 않았는지 확인
        verify(gateway, times(1)).confirm(any());
        verify(gateway, times(2))
                .findByOrderId(prepared.orderRef());
    }


    @Test
    void 승인응답을_기다리는_동안_중복요청이_와도_승인은_한번만_호출한다()
            throws Exception {

        CountDownLatch approvalStarted = new CountDownLatch(1);
        CountDownLatch releaseApproval = new CountDownLatch(1);

        ExecutorService executor = Executors.newFixedThreadPool(2);

        Receipt approvedReceipt = receipt(
                "DONE",
                prepared.amount()
        );

        TossPaymentException notFound = new TossPaymentException(
                TossPaymentException.Kind.HTTP_RESPONSE,
                404,
                "NOT_FOUND_PAYMENT"
        );

        // 첫 승인 호출을 잠시 대기시킵니다.
        when(gateway.confirm(any())).thenAnswer(invocation -> {
            approvalStarted.countDown();

            boolean released = releaseApproval.await(
                    10,
                    TimeUnit.SECONDS
            );

            if (!released) {
                throw new AssertionError(
                        "테스트에서 승인 응답 대기를 해제하지 못했습니다."
                );
            }

            return approvedReceipt;
        });

        // 첫 승인이 처리 중이므로 조회에서는 아직 결제가 보이지 않음
        when(gateway.find(paymentKey)).thenThrow(notFound);

        when(gateway.findByOrderId(prepared.orderRef()))
                .thenThrow(notFound);

        try {
            Future<Result> firstRequest = executor.submit(
                    () -> confirm(prepared.amount())
            );

            assertThat(approvalStarted.await(5, TimeUnit.SECONDS))
                    .as("첫 번째 요청이 승인 API 호출까지 도달해야 합니다.")
                    .isTrue();

            // 첫 요청이 승인 응답을 기다리는 동안 같은 요청을 보냅니다.
            Future<Result> duplicateRequest = executor.submit(
                    () -> confirm(prepared.amount())
            );

            Result duplicateResult = duplicateRequest.get(
                    5,
                    TimeUnit.SECONDS
            );

            assertThat(duplicateResult.status())
                    .isEqualTo("PAYMENT_PENDING");

            assertThat(snapshot().status())
                    .isEqualTo(Order.Status.PAYMENT_PENDING);

            assertThat(snapshot().currentQuantity()).isZero();

            verify(gateway, times(1)).confirm(any());

            // 첫 번째 승인 응답을 돌려줍니다.
            releaseApproval.countDown();

            Result firstResult = firstRequest.get(
                    5,
                    TimeUnit.SECONDS
            );

            assertThat(firstResult.status()).isEqualTo("PAID");
            assertThat(snapshot().status()).isEqualTo(Order.Status.PAID);
            assertThat(snapshot().currentQuantity()).isEqualTo(2);

            // 완료 후에도 승인 호출과 수량 반영은 한 번뿐이어야 합니다.
            verify(gateway, times(1)).confirm(any());

        } finally {
            releaseApproval.countDown();
            executor.shutdownNow();
            executor.awaitTermination(5, TimeUnit.SECONDS);
        }
    }
    @Autowired
    OrderRepository orders;

    @Autowired
    GroupPurchaseService sellerGroups;

    @Test
    void 최초_준비만_유예하고_반복준비로_기한을_연장하지_않는다() {
        Fixture fresh = createFixture();
        LocalDateTime before = LocalDateTime.now();
        tx.executeWithoutResult(s -> em.createQuery(
                "update Order o set o.reservedUntil = :time where o.id = :id")
                .setParameter("time", before.plusMinutes(1)).setParameter("id", fresh.orderId()).executeUpdate());
        Prepared first = payments.prepare(fresh.orderId(), fresh.buyerId());
        LocalDateTime until = tx.execute(s -> em.find(Order.class, fresh.orderId()).getReservedUntil());
        assertThat(until).isBetween(before.plusMinutes(5).minusSeconds(1), LocalDateTime.now().plusMinutes(5).plusSeconds(1));
        assertThat(payments.prepare(fresh.orderId(), fresh.buyerId()).orderRef()).isEqualTo(first.orderRef());
        tx.executeWithoutResult(s -> {
            Order order = em.find(Order.class, fresh.orderId());
            assertThat(order.getReservedUntil()).isEqualTo(until);
            Long groupId = order.getGroupPurchaseId();
            assertThat(orders.sumActivePendingQuantity(groupId, until.minusSeconds(1))).isEqualTo(2);
            assertThat(orders.sumActivePendingQuantity(groupId, until)).isZero();
            assertThat(orders.findMaxReservedUntil(groupId, before)).contains(until);
        });
    }

    @Test
    void 원래_기한이_지나도_결제창_유예_안이면_승인한다() {
        LocalDateTime opened = LocalDateTime.now().minusMinutes(2);
        Long id = tx.execute(s -> {
            Order base = em.find(Order.class, fixture.orderId());
            Order order = new Order(base.getUser(), base.getGroupPurchase(), 1, 1000, 1000, opened.plusMinutes(1));
            order.prepareCheckout("grace-" + paymentKey, opened);
            em.persist(order);
            return order.getId();
        });
        String ref = "grace-" + paymentKey;
        when(gateway.confirm(any())).thenReturn(new Receipt(paymentKey, ref, 1000, "KRW", "DONE"));
        assertThat(payments.confirm(id, fixture.buyerId(), new ConfirmRequest(paymentKey, ref, 1000L)).status())
                .isEqualTo("PAID");
    }

    @Test
    void 결제창_유예는_공동구매_마감시각을_넘지_않는다() {
        tx.executeWithoutResult(s -> {
            Order base = em.find(Order.class, fixture.orderId());
            GroupPurchase group = base.getGroupPurchase();
            LocalDateTime opened = group.getEndAt().minusMinutes(2);
            Order order = new Order(base.getUser(), group, 1, 1000, 1000, opened.plusMinutes(1));
            order.prepareCheckout("capped", opened);
            assertThat(order.getReservedUntil()).isEqualTo(group.getEndAt());
            assertThatThrownBy(() -> order.beginPayment(group.getEndAt())).isInstanceOf(IllegalStateException.class);
        });
    }

    @Test
    void 기한이_지난_승인중_주문도_회원별_수량과_마감단축을_보호한다() {
        tx.executeWithoutResult(s -> {
            Order order = em.find(Order.class, fixture.orderId());
            order.beginPayment(LocalDateTime.now());
            order.bindPaymentKey(paymentKey);
            em.flush();
            em.createQuery("update Order o set o.reservedUntil = :time where o.id = :id")
                    .setParameter("time", LocalDateTime.now().minusMinutes(1))
                    .setParameter("id", order.getId()).executeUpdate();
        });
        GroupPurchaseForm form = tx.execute(s -> GroupPurchaseForm.from(
                em.find(Order.class, fixture.orderId()).getGroupPurchase()));
        Long groupId = tx.execute(s -> em.find(Order.class, fixture.orderId()).getGroupPurchaseId());
        Long storeId = tx.execute(s -> em.find(GroupPurchase.class, groupId).getStore().getId());
        form.setLimitPerUser(1);
        assertThatThrownBy(() -> sellerGroups.updateGroupPurchase(storeId, groupId, fixture.sellerId(), form))
                .isInstanceOf(ResponseStatusException.class).hasMessageContaining("1인당");
        form.setLimitPerUser(10);
        form.setEndAt(form.getEndAt().minusMinutes(1));
        assertThatThrownBy(() -> sellerGroups.updateGroupPurchase(storeId, groupId, fixture.sellerId(), form))
                .isInstanceOf(ResponseStatusException.class).hasMessageContaining("승인 결과");
    }

    @Autowired
    PaymentTransactions transactions;

    // 외부 HTTP를 호출하기 직전에 프로세스가 종료된 DB 상태를 재현합니다.
    private void interruptBeforeApproval() {
        transactions.begin(fixture.orderId(), fixture.buyerId(), request(prepared.amount()));
        tx.executeWithoutResult(s -> em.createQuery(
                        "update Order o set o.paymentRetryAt = :past where o.id = :id")
                .setParameter("past", LocalDateTime.now().minusMinutes(1))
                .setParameter("id", fixture.orderId()).executeUpdate());
    }

    @Test
    void 승인전_중단되면_서버가_저장된_동일요청으로_복구한다() {
        interruptBeforeApproval();
        when(gateway.findByOrderId(prepared.orderRef())).thenThrow(new TossPaymentException(
                TossPaymentException.Kind.HTTP_RESPONSE, 404, "NOT_FOUND_PAYMENT"));
        when(gateway.confirm(any())).thenReturn(receipt("DONE", prepared.amount()));

        var job = new PaymentRecoveryJob(orders, payments);
        job.recoverPendingPayments();
        job.recoverPendingPayments();

        verify(gateway, times(1)).confirm(argThat(a ->
                a.orderRef().equals(prepared.orderRef()) && a.paymentKey().equals(paymentKey)
                        && a.amount() == prepared.amount()));
        assertThat(snapshot().status()).isEqualTo(Order.Status.PAID);
        assertThat(snapshot().currentQuantity()).isEqualTo(2);
    }

    @Test
    void 이미_승인된_결제를_발견하면_재전송없이_복구한다() {
        interruptBeforeApproval();
        when(gateway.findByOrderId(prepared.orderRef())).thenReturn(receipt("DONE", prepared.amount()));
        payments.recover(fixture.orderId(), fixture.buyerId(), LocalDateTime.now());
        verify(gateway, never()).confirm(any());
        assertThat(snapshot().status()).isEqualTo(Order.Status.PAID);
        assertThat(snapshot().currentQuantity()).isEqualTo(2);
    }

    @Test
    void 복구승인에서_세션만료가_확정되고_거래도_없으면_수량을_해제한다() {
        interruptBeforeApproval();
        when(gateway.findByOrderId(prepared.orderRef())).thenThrow(new TossPaymentException(
                TossPaymentException.Kind.HTTP_RESPONSE, 404, "NOT_FOUND_PAYMENT"));
        when(gateway.confirm(any())).thenThrow(new TossPaymentException(
                TossPaymentException.Kind.HTTP_RESPONSE, 404, "NOT_FOUND_PAYMENT_SESSION"));
        payments.recover(fixture.orderId(), fixture.buyerId(), LocalDateTime.now());
        assertThat(snapshot().status()).isEqualTo(Order.Status.EXPIRED);
        assertThat(snapshot().currentQuantity()).isZero();
    }

    @Test
    void 서버복구_조회가_불확실하면_승인을_재전송하지_않는다() {
        interruptBeforeApproval();
        when(gateway.findByOrderId(prepared.orderRef())).thenThrow(new ResourceAccessException("timeout"));
        payments.recover(fixture.orderId(), fixture.buyerId(), LocalDateTime.now());
        verify(gateway, never()).confirm(any());
        assertThat(snapshot().status()).isEqualTo(Order.Status.PAYMENT_PENDING);
        assertThat(snapshot().currentQuantity()).isZero();
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.EnumSource(value = User.Status.class, names = {"SUSPENDED", "WITHDRAWN"})
    void 승인도중_회원상태가_바뀌어도_결과를_한번만_반영한다(User.Status accountStatus) {
        Fixture other = createFixture();
        when(gateway.confirm(any())).thenAnswer(invocation -> {
            tx.executeWithoutResult(s -> em.createQuery("update User u set u.status = :status where u.id in :ids")
                    .setParameter("status", accountStatus)
                    .setParameter("ids", java.util.List.of(fixture.buyerId(), other.buyerId())).executeUpdate());
            return receipt("DONE", prepared.amount());
        });
        assertThat(confirm(prepared.amount()).status()).isEqualTo("PAID");
        assertThat(payments.retry(fixture.orderId(), fixture.buyerId()).status()).isEqualTo("PAID");
        assertThat(snapshot().currentQuantity()).isEqualTo(2);
        assertThatThrownBy(() -> payments.prepare(other.orderId(), other.buyerId()))
                .isInstanceOf(ResponseStatusException.class).hasMessageContaining("403");
        verify(gateway, times(1)).confirm(any());
    }

    @Test
    void 결제완료_수량이_목표미만이면_모집중을_유지한다() {
        when(gateway.confirm(any()))
                .thenReturn(receipt("DONE", prepared.amount()));

        Result result = confirm(prepared.amount());

        GroupPurchase.Status groupStatus = tx.execute(status ->
                em.find(Order.class, fixture.orderId())
                        .getGroupPurchase()
                        .getStatus()
        );

        assertThat(result.status()).isEqualTo("PAID");
        assertThat(groupStatus).isEqualTo(GroupPurchase.Status.OPEN);
        assertThat(snapshot().currentQuantity()).isEqualTo(2);
    }

    @Test
    void 목표달성시_즉시성공하고_대기예약만_만료하며_진행중승인은_완료한다() {
        // 현재 주문은 2개이므로 목표를 2개로 설정합니다.
        // 같은 모집에 일반 예약 1개와 승인 시작용 예약 1개도 만듭니다.
        Long[] ids = tx.execute(status -> {
            Order original = em.find(Order.class, fixture.orderId());
            Long groupId = original.getGroupPurchaseId();

            em.createQuery("""
                update GroupPurchase g
                set g.targetQuantity = :target
                where g.id = :id
                """)
                    .setParameter("target", 2)
                    .setParameter("id", groupId)
                    .executeUpdate();

            // JPQL 직접 수정 후 기존 캐시를 비웁니다.
            em.clear();

            GroupPurchase group = em.find(GroupPurchase.class, groupId);
            User buyer = em.find(User.class, fixture.buyerId());

            Order waiting = new Order(
                    buyer,
                    group,
                    1,
                    1000,
                    1000,
                    LocalDateTime.now().plusMinutes(15)
            );

            Order approving = new Order(
                    buyer,
                    group,
                    1,
                    1000,
                    1000,
                    LocalDateTime.now().plusMinutes(15)
            );

            em.persist(waiting);
            em.persist(approving);
            em.flush();

            return new Long[]{
                    groupId,
                    waiting.getId(),
                    approving.getId()
            };
        });

        Long groupId = ids[0];
        Long waitingOrderId = ids[1];
        Long approvingOrderId = ids[2];

        // 다른 주문이 먼저 승인 처리를 시작한 상황을 만듭니다.
        Prepared otherPrepared = payments.prepare(
                approvingOrderId,
                fixture.buyerId()
        );

        String otherPaymentKey = "other-payment-" + UUID.randomUUID();

        Attempt started = transactions.begin(
                approvingOrderId,
                fixture.buyerId(),
                new ConfirmRequest(
                        otherPaymentKey,
                        otherPrepared.orderRef(),
                        (long) otherPrepared.amount()
                )
        );

        assertThat(started.confirmationAllowed()).isTrue();

        // 원래 주문 2개의 결제가 먼저 완료되어 목표에 도달합니다.
        when(gateway.confirm(any()))
                .thenReturn(receipt("DONE", prepared.amount()));

        Result first = confirm(prepared.amount());

        assertThat(first.status()).isEqualTo("PAID");

        tx.executeWithoutResult(status -> {
            GroupPurchase group = em.find(GroupPurchase.class, groupId);
            Order waiting = em.find(Order.class, waitingOrderId);
            Order approving = em.find(Order.class, approvingOrderId);

            assertThat(group.getStatus())
                    .isEqualTo(GroupPurchase.Status.SUCCESS);

            assertThat(group.getCurrentQuantity()).isEqualTo(2);

            assertThat(waiting.getStatus())
                    .isEqualTo(Order.Status.EXPIRED);

            assertThat(waiting.getCancelReason())
                    .isEqualTo("공동구매 목표 달성으로 모집이 종료되었습니다.");

            assertThat(approving.getStatus())
                    .isEqualTo(Order.Status.PAYMENT_PENDING);
        });

        // 성공한 모집에는 새 예약을 만들 수 없어야 합니다.
        assertThatThrownBy(() -> reservations.reserveOrder(
                fixture.buyerId(),
                groupId,
                1
        )).isInstanceOf(ResponseStatusException.class);

        // 만료된 일반 예약도 결제를 새로 시작할 수 없어야 합니다.
        assertThatThrownBy(() -> payments.prepare(
                waitingOrderId,
                fixture.buyerId()
        )).isInstanceOf(ResponseStatusException.class);

        // 이미 승인 중이던 다른 주문은 조회로 결제 완료를 확인합니다.
        Receipt otherReceipt = new Receipt(
                otherPaymentKey,
                otherPrepared.orderRef(),
                otherPrepared.amount(),
                "KRW",
                "DONE"
        );

        when(gateway.find(otherPaymentKey))
                .thenReturn(otherReceipt);

        Result recovered = payments.retry(
                approvingOrderId,
                fixture.buyerId()
        );

        assertThat(recovered.status()).isEqualTo("PAID");

        // 같은 결과를 다시 확인해도 수량이 중복 증가하면 안 됩니다.
        payments.retry(approvingOrderId, fixture.buyerId());

        tx.executeWithoutResult(status -> {
            GroupPurchase group = em.find(GroupPurchase.class, groupId);
            Order approving = em.find(Order.class, approvingOrderId);

            assertThat(group.getStatus())
                    .isEqualTo(GroupPurchase.Status.SUCCESS);

            assertThat(group.getCurrentQuantity()).isEqualTo(3);

            assertThat(approving.getStatus())
                    .isEqualTo(Order.Status.PAID);
        });

        verify(gateway, times(1)).confirm(any());
    }

    @Test
    void 판매자_수정은_유효예약의_최대수량과_마감을_보호한다() {
        GroupPurchaseForm form = tx.execute(s -> GroupPurchaseForm.from(
                em.find(Order.class, fixture.orderId()).getGroupPurchase()));
        Long groupId = tx.execute(s -> em.find(Order.class, fixture.orderId()).getGroupPurchaseId());
        Long storeId = tx.execute(s -> em.find(GroupPurchase.class, groupId).getStore().getId());
        form.setMaxQuantity(1);
        assertThatThrownBy(() -> sellerGroups.updateGroupPurchase(storeId, groupId, fixture.sellerId(), form))
                .isInstanceOf(ResponseStatusException.class).hasMessageContaining("최대 모집 수량");
        form.setMaxQuantity(20);
        form.setEndAt(LocalDateTime.now().plusMinutes(1));
        assertThatThrownBy(() -> sellerGroups.updateGroupPurchase(storeId, groupId, fixture.sellerId(), form))
                .isInstanceOf(ResponseStatusException.class).hasMessageContaining("예약의 만료");
    }

    private Result confirm(long amount) {
        return payments.confirm(
                fixture.orderId(),
                fixture.buyerId(),
                request(amount)
        );
    }

    private ConfirmRequest request(long amount) {
        return new ConfirmRequest(
                paymentKey,
                prepared.orderRef(),
                amount
        );
    }

    private Receipt receipt(String status, long amount) {
        return new Receipt(
                paymentKey,
                prepared.orderRef(),
                amount,
                "KRW",
                status
        );
    }

    private Snapshot snapshot() {
        return tx.execute(status -> {
            Order order = em.find(Order.class, fixture.orderId());

            return new Snapshot(
                    order.getStatus(),
                    order.getGroupPurchase().getCurrentQuantity()
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
                    "테스트 매장",
                    null,
                    "031-000-0000",
                    "시흥시",
                    null,
                    null
            );
            em.persist(store);

            Product product = new Product(
                    store,
                    "테스트 상품",
                    "식품",
                    2000,
                    "개",
                    "테스트 상품 설명",
                    null
            );
            em.persist(product);

            LocalDateTime now = LocalDateTime.now();

            GroupPurchase group = new GroupPurchase(
                    store,
                    product,
                    "테스트 공동구매",
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

            Order order = new Order(
                    buyer,
                    group,
                    2,
                    1000,
                    2000,
                    now.plusMinutes(15)
            );
            em.persist(order);
            em.flush();

            return new Fixture(
                    order.getId(),
                    buyer.getId(),
                    seller.getId()
            );
        });
    }

    record Fixture(
            Long orderId,
            Long buyerId,
            Long sellerId
    ) {
    }

    record Snapshot(
            Order.Status status,
            int currentQuantity
    ) {
    }
}