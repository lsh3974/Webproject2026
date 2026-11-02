package com.siheung.hanbaguni.concurrency;

import com.siheung.hanbaguni.entity.GroupPurchase;
import com.siheung.hanbaguni.entity.Order;
import com.siheung.hanbaguni.entity.Product;
import com.siheung.hanbaguni.entity.Store;
import com.siheung.hanbaguni.entity.User;
import com.siheung.hanbaguni.repository.GroupPurchaseRepository;
import com.siheung.hanbaguni.repository.OrderRepository;
import com.siheung.hanbaguni.repository.ProductRepository;
import com.siheung.hanbaguni.repository.StoreRepository;
import com.siheung.hanbaguni.repository.UserRepository;
import com.siheung.hanbaguni.service.GroupPurchaseService;
import com.siheung.hanbaguni.service.OrderReservationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class OrderReservationConcurrencyTest {

    @Autowired
    private OrderReservationService orderReservationService;

    @Autowired
    private GroupPurchaseService groupPurchaseService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private StoreRepository storeRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private GroupPurchaseRepository groupPurchaseRepository;

    @Autowired
    private OrderRepository orderRepository;

    private User seller;
    private Store store;
    private Product product;

    @BeforeEach
    void setUp() {
        orderRepository.deleteAll();
        groupPurchaseRepository.deleteAll();
        productRepository.deleteAll();
        storeRepository.deleteAll();
        userRepository.deleteAll();

        seller = userRepository.save(new User("seller@test.com", "hash", "판매자"));
        store = storeRepository.save(new Store(seller, "싱싱마트", "123-45-67890", "031-111-2222", "시흥시 정왕동", "1층", "소개"));
        product = productRepository.save(new Product(store, "샤인머스캣", "과일", 20000, "1박스", "설명", null));
    }

    @Test
    @DisplayName("동시성 테스트: 잔여 수량이 1개 남았을 때 10명의 사용자가 동시 예약 요청 시 정확히 1명만 성공하고 초과 예약 없음")
    void testConcurrentReservationForLastStock() throws InterruptedException {
        // given: 최대 수량 10개, 이미 9개 선점되어 잔여 수량이 정확히 1개 남은 공동구매 생성
        LocalDateTime now = LocalDateTime.now();
        GroupPurchase gp = groupPurchaseRepository.save(new GroupPurchase(
                store, product, "샤인머스캣 특가", 15000, 5, 10, 5,
                now.minusDays(1), now.plusDays(3), now.plusDays(4), now.plusDays(5),
                "안내", "설명", GroupPurchase.Status.OPEN
        ));

        User existingBuyer = userRepository.save(new User("exist@test.com", "hash", "기존구매자"));
        orderRepository.save(new Order(existingBuyer, gp, 9, 15000, 135000, now.plusMinutes(15)));

        // 10명의 서로 다른 구매자 생성
        int threadCount = 10;
        List<User> buyers = new ArrayList<>();
        for (int i = 0; i < threadCount; i++) {
            buyers.add(userRepository.save(new User("buyer" + i + "@test.com", "hash", "구매자" + i)));
        }

        ExecutorService executorService = Executors.newFixedThreadPool(threadCount);
        CountDownLatch readyLatch = new CountDownLatch(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threadCount);

        AtomicInteger successCount = new AtomicInteger();
        AtomicInteger conflictCount = new AtomicInteger();
        List<Exception> otherExceptions = Collections.synchronizedList(new ArrayList<>());

        // when: 10명의 구매자가 동시에 1개씩 예약 시도
        for (int i = 0; i < threadCount; i++) {
            final User buyer = buyers.get(i);
            executorService.submit(() -> {
                readyLatch.countDown();
                try {
                    startLatch.await();
                    orderReservationService.reserveOrder(buyer.getId(), gp.getId(), 1);
                    successCount.incrementAndGet();
                } catch (ResponseStatusException e) {
                    if (e.getStatusCode().value() == 409) {
                        conflictCount.incrementAndGet();
                    } else {
                        otherExceptions.add(e);
                    }
                } catch (Exception e) {
                    otherExceptions.add(e);
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        readyLatch.await();
        startLatch.countDown(); // 동시 출발
        doneLatch.await();
        executorService.shutdown();

        // then:
        assertThat(otherExceptions).isEmpty();
        assertThat(successCount.get()).isEqualTo(1); // 오직 1명만 마지막 재고 예약 성공
        assertThat(conflictCount.get()).isEqualTo(9); // 나머지 9명은 409 CONFLICT(재고 부족) 발생

        // DB 검증: 총 주문 수량은 9(기존) + 1(신규) = 정확히 10개
        int activePendingQty = orderRepository.sumActivePendingQuantity(gp.getId(), LocalDateTime.now());
        assertThat(activePendingQty).isEqualTo(10);
    }

    @Test
    @DisplayName("동시성 테스트: 동일한 주문에 대해 여러 스레드가 동시 취소 요청 시 정확히 1번만 취소 처리")
    void testConcurrentCancelSameOrder() throws InterruptedException {
        // given: PENDING 주문 1건 생성
        LocalDateTime now = LocalDateTime.now();
        GroupPurchase gp = groupPurchaseRepository.save(new GroupPurchase(
                store, product, "샤인머스캣", 15000, 5, 20, 5,
                now.minusDays(1), now.plusDays(3), now.plusDays(4), now.plusDays(5),
                "안내", "설명", GroupPurchase.Status.OPEN
        ));
        User buyer = userRepository.save(new User("buyer_cancel@test.com", "hash", "취소구매자"));
        Order order = orderRepository.save(new Order(buyer, gp, 2, 15000, 30000, now.plusMinutes(15)));

        int threadCount = 5;
        ExecutorService executorService = Executors.newFixedThreadPool(threadCount);
        CountDownLatch readyLatch = new CountDownLatch(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threadCount);

        AtomicInteger successCount = new AtomicInteger();
        AtomicInteger badRequestCount = new AtomicInteger();
        List<Exception> otherExceptions = Collections.synchronizedList(new ArrayList<>());

        // when: 5개 스레드가 동일한 주문에 대해 동시 취소 요청
        for (int i = 0; i < threadCount; i++) {
            executorService.submit(() -> {
                readyLatch.countDown();
                try {
                    startLatch.await();
                    orderReservationService.cancelReservation(order.getId(), buyer.getId(), "단순 변심");
                    successCount.incrementAndGet();
                } catch (ResponseStatusException e) {
                    if (e.getStatusCode().value() == 400) {
                        badRequestCount.incrementAndGet();
                    } else {
                        otherExceptions.add(e);
                    }
                } catch (Exception e) {
                    otherExceptions.add(e);
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        readyLatch.await();
        startLatch.countDown();
        doneLatch.await();
        executorService.shutdown();

        // then:
        assertThat(otherExceptions).isEmpty();
        assertThat(successCount.get()).isEqualTo(1); // 정확히 1번만 취소 성공
        assertThat(badRequestCount.get()).isEqualTo(4); // 나머지는 이미 취소된 주문으로 400 반환

        Order cancelledOrder = orderRepository.findById(order.getId()).orElseThrow();
        assertThat(cancelledOrder.getStatus()).isEqualTo(Order.Status.CANCELLED);
    }

    @Test
    @DisplayName("동시성 테스트: 공동구매 삭제와 신규 예약이 경합할 때 행 잠금으로 원자성 보장 (데이터 불일치 방지)")
    void testConcurrentDeleteAndReserve() throws InterruptedException {
        LocalDateTime now = LocalDateTime.now();
        GroupPurchase gp = groupPurchaseRepository.save(new GroupPurchase(
                store, product, "경합 테스트 공구", 15000, 5, 20, 5,
                now.minusDays(1), now.plusDays(3), now.plusDays(4), now.plusDays(5),
                "안내", "설명", GroupPurchase.Status.OPEN
        ));
        User buyer = userRepository.save(new User("buyer_race@test.com", "hash", "경합구매자"));

        int threadCount = 2;
        ExecutorService executorService = Executors.newFixedThreadPool(threadCount);
        CountDownLatch readyLatch = new CountDownLatch(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threadCount);

        AtomicInteger deleteSuccess = new AtomicInteger();
        AtomicInteger reserveSuccess = new AtomicInteger();

        // Thread 1: 공동구매 삭제 시도
        executorService.submit(() -> {
            readyLatch.countDown();
            try {
                startLatch.await();
                groupPurchaseService.deleteGroupPurchase(store.getId(), gp.getId(), seller.getId());
                deleteSuccess.incrementAndGet();
            } catch (Exception ignored) {
            } finally {
                doneLatch.countDown();
            }
        });

        // Thread 2: 신규 예약 시도
        executorService.submit(() -> {
            readyLatch.countDown();
            try {
                startLatch.await();
                orderReservationService.reserveOrder(buyer.getId(), gp.getId(), 2);
                reserveSuccess.incrementAndGet();
            } catch (Exception ignored) {
            } finally {
                doneLatch.countDown();
            }
        });

        readyLatch.await();
        startLatch.countDown();
        doneLatch.await();
        executorService.shutdown();

        // then:
        // 둘 다 동시에 성공하여 고아 주문이 생기거나 외래키 제약조건 위반 에러가 나는 일이 없어야 함
        // 1) 예약이 먼저 락을 잡았으면: 예약 성공(1) & 삭제 실패(0)
        // 2) 삭제가 먼저 락을 잡았으면: 삭제 성공(1) & 예약 실패(0)
        assertThat(deleteSuccess.get() + reserveSuccess.get()).isEqualTo(1);
    }
}
