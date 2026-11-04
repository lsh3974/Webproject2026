package com.siheung.hanbaguni.repository;

import com.siheung.hanbaguni.entity.GroupPurchase;
import com.siheung.hanbaguni.entity.Order;
import com.siheung.hanbaguni.entity.Product;
import com.siheung.hanbaguni.entity.Store;
import com.siheung.hanbaguni.entity.User;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;
import jakarta.persistence.OptimisticLockException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:payment_quantity_test;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
@Transactional
class PaymentPendingQuantityTest {

    @Autowired
    private EntityManager em;

    @Autowired
    private OrderRepository orderRepository;

    @Test
    @DisplayName("예약 기한 이후에도 승인 중 수량은 유지하고 일반 대기 수량은 제외한다")
    void keepsPaymentPendingQuantityAfterReservationDeadline() {
        LocalDateTime start = LocalDateTime.of(2026, 11, 2, 12, 0);
        LocalDateTime deadline = start.plusMinutes(15);

        User seller = new User(
                "seller@example.test", "unused-hash", "판매자"
        );
        User buyer = new User(
                "buyer@example.test", "unused-hash", "구매자"
        );
        em.persist(seller);
        em.persist(buyer);

        Store store = new Store(
                seller, "테스트 매장", null,
                "031-000-0000", "시흥시", null, null
        );
        em.persist(store);

        Product product = new Product(
                store, "테스트 상품", "식품",
                2000, "개", "설명", null
        );
        em.persist(product);

        GroupPurchase gp = new GroupPurchase(
                store, product, "테스트 공동구매",
                1000, 5, 20, 10,
                start.minusHours(1), start.plusDays(1),
                start.plusDays(2), start.plusDays(3),
                null, null, GroupPurchase.Status.OPEN
        );
        em.persist(gp);

        // 2개는 예약 기한 안에 결제 승인을 시작합니다.
        Order approving = new Order(
                buyer, gp, 2, 1000, 2000, deadline
        );
        approving.beginPayment(start.plusMinutes(10));
        em.persist(approving);

        // 3개는 승인 시작 없이 일반 예약 상태로 둡니다.
        Order waiting = new Order(
                buyer, gp, 3, 1000, 3000, deadline
        );
        em.persist(waiting);

        em.flush();

        // 기한 전: 승인 중 2개 + 일반 예약 3개 = 5개
        LocalDateTime beforeDeadline = start.plusMinutes(14);

        assertThat(orderRepository.sumActivePendingQuantity(
                gp.getId(), beforeDeadline
        )).isEqualTo(5);

        assertThat(orderRepository.sumUserActiveQuantity(
                gp.getId(), buyer.getId(), beforeDeadline
        )).isEqualTo(5);

        // 기한 후: 일반 예약 3개는 제외하고 승인 중 2개만 유지
        LocalDateTime afterDeadline = start.plusMinutes(16);

        assertThat(orderRepository.sumActivePendingQuantity(
                gp.getId(), afterDeadline
        )).isEqualTo(2);

        assertThat(orderRepository.sumUserActiveQuantity(
                gp.getId(), buyer.getId(), afterDeadline
        )).isEqualTo(2);

        // 일반 대기 주문의 옛 사본을 남깁니다.
        Long waitingOrderId = waiting.getId();
        Long oldVersion = waiting.getVersion();
        em.detach(waiting);

// 다른 작업이 같은 주문의 결제 승인을 시작했다고 가정합니다.
        Order latestOrder = em.find(Order.class, waitingOrderId);
        latestOrder.beginPayment(beforeDeadline);
        em.flush();

        assertThat(latestOrder.getVersion()).isGreaterThan(oldVersion);

// 다음 저장이 DB의 최신 버전과 비교되도록 캐시를 비웁니다.
        em.clear();

// 옛 사본은 여전히 PENDING이므로 만료 처리하려고 합니다.
        waiting.expireReservation();

// 오래된 버전의 저장은 거절돼야 합니다.
        assertThatThrownBy(() -> {
            em.merge(waiting);
            em.flush();
        }).isInstanceOf(OptimisticLockException.class);
    }


}