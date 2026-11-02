package com.siheung.hanbaguni.payment;

import com.siheung.hanbaguni.entity.GroupPurchase;
import com.siheung.hanbaguni.entity.Order;
import com.siheung.hanbaguni.entity.Product;
import com.siheung.hanbaguni.entity.Store;
import com.siheung.hanbaguni.entity.User;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
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

        verifyNoInteractions(gateway);

        assertThat(snapshot().status()).isEqualTo(Order.Status.PENDING);
    }

    @Test
    void 브라우저에서_변조한_금액은_거절한다() {
        assertThatThrownBy(() -> confirm(1))
                .isInstanceOf(ResponseStatusException.class);

        verifyNoInteractions(gateway);

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

        verifyNoInteractions(gateway);
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

        Result first = confirm(prepared.amount());

        assertThat(first.status()).isEqualTo("PAYMENT_PENDING");
        assertThat(snapshot().status())
                .isEqualTo(Order.Status.PAYMENT_PENDING);
        assertThat(snapshot().currentQuantity()).isZero();

        doReturn(receipt("DONE", prepared.amount()))
                .when(gateway)
                .confirm(any());

        Result retried = payments.retry(
                fixture.orderId(),
                fixture.buyerId()
        );

        assertThat(retried.status()).isEqualTo("PAID");
        assertThat(snapshot().currentQuantity()).isEqualTo(2);
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