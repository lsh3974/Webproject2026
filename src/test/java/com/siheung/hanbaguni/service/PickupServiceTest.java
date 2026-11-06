package com.siheung.hanbaguni.service;

import com.siheung.hanbaguni.entity.GroupPurchase;
import com.siheung.hanbaguni.entity.Order;
import com.siheung.hanbaguni.entity.Pickup;
import com.siheung.hanbaguni.entity.Product;
import com.siheung.hanbaguni.entity.Store;
import com.siheung.hanbaguni.entity.User;
import com.siheung.hanbaguni.repository.GroupPurchaseRepository;
import com.siheung.hanbaguni.repository.OrderRepository;
import com.siheung.hanbaguni.repository.PickupRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class PickupServiceTest {

    @Mock
    private PickupRepository pickupRepository;

    @Mock
    private StoreService storeService;

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private GroupPurchaseRepository groupPurchaseRepository;

    @Mock
    private jakarta.persistence.EntityManager entityManager;

    @InjectMocks
    private PickupService pickupService;

    @Test
    @DisplayName("결제 완료(PAID) 주문에 대해 신규 수령증 및 QR 발급 성공")
    void createOrGetPickupForOrder_success() {
        // given
        User customer = new User("customer@test.com", "hash", "구매자1");
        ReflectionTestUtils.setField(customer, "id", 100L);

        User seller = new User("seller@test.com", "hash", "판매자1");
        ReflectionTestUtils.setField(seller, "id", 1L);

        Store store = new Store(seller, "시흥마트", "123-45-67890", "031-123-4567", "시흥시", "101호", "소개");
        ReflectionTestUtils.setField(store, "id", 10L);

        Product product = new Product(store, "사과", "과일", 15000, "1박스", "설명", null);
        LocalDateTime now = LocalDateTime.now();
        GroupPurchase gp = new GroupPurchase(store, product, "사과 공구", 12000, 10, 50, 3, now, now.plusDays(3), now.plusDays(4), now.plusDays(5), "안내", "설명", GroupPurchase.Status.OPEN);

        Order order = new Order(customer, gp, 2, 12000, 24000, now.plusMinutes(15));
        ReflectionTestUtils.setField(order, "id", 500L);
        ReflectionTestUtils.setField(order, "status", Order.Status.PAID);

        ReflectionTestUtils.setField(gp, "id", 333L);
        given(groupPurchaseRepository.findByIdWithLock(333L)).willReturn(Optional.of(gp));
        given(orderRepository.findPaymentOrderWithLock(500L)).willReturn(Optional.of(order));
        given(pickupRepository.findByOrderId(500L)).willReturn(Optional.empty());
        given(pickupRepository.saveAndFlush(any(Pickup.class))).willAnswer(inv -> inv.getArgument(0));

        // when
        Pickup pickup = pickupService.createOrGetPickupForOrder(order);

        // then
        assertThat(pickup).isNotNull();
        assertThat(pickup.getOrder()).isEqualTo(order);
        assertThat(pickup.getQuantity()).isEqualTo(2);
        assertThat(pickup.getStatus()).isEqualTo(Pickup.Status.PREPARING);
        assertThat(pickup.getPickupCode()).startsWith("PK-");
    }

    @Test
    @DisplayName("결제 완료(PAID)가 아닌 주문(PENDING)은 수령증 발급 시 예외 발생")
    void createOrGetPickupForOrder_notPaid_throwsException() {
        // given
        User customer = new User("customer@test.com", "hash", "구매자1");
        Store store = new Store(new User("seller@test.com", "hash", "판매자1"), "시흥마트", null, "031-111-2222", "시흥시", null, null);
        Product product = new Product(store, "사과", "과일", 15000, "1박스", null, null);
        LocalDateTime now = LocalDateTime.now();
        GroupPurchase gp = new GroupPurchase(store, product, "사과 공구", 12000, 10, 50, 3, now, now.plusDays(3), now.plusDays(4), now.plusDays(5), null, null, GroupPurchase.Status.OPEN);

        Order pendingOrder = new Order(customer, gp, 1, 12000, 12000, now.plusMinutes(15));
        ReflectionTestUtils.setField(pendingOrder, "status", Order.Status.PENDING);

        // when & then
        assertThatThrownBy(() -> pickupService.createOrGetPickupForOrder(pendingOrder))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("결제 완료(PAID) 상태인 주문만 수령증을 발급할 수 있습니다.");
    }

    @Test
    @DisplayName("수령 코드 검색 시 타 매장 주문이거나 존재하지 않으면 NOT_FOUND 예외 발생")
    void getByCodeAndStore_notFound_throwsException() {
        // given
        Long sellerId = 1L;
        Long storeId = 10L;
        String pickupCode = "PK-NOT-FOUND";

        given(pickupRepository.findByPickupCodeAndStoreId(pickupCode, storeId)).willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> pickupService.getByCodeAndStore(pickupCode, storeId, sellerId))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("해당 매장의 수령 건을 찾을 수 없습니다");
    }

    @Test
    @DisplayName("타 판매자의 매장 수령 건 조작 시 FORBIDDEN(403) 예외 발생")
    void getByIdWithOwnershipCheck_otherSeller_throwsForbidden() {
        // given
        Long realSellerId = 1L;
        Long otherSellerId = 99L;

        User seller = new User("seller@test.com", "hash", "판매자1");
        ReflectionTestUtils.setField(seller, "id", realSellerId);

        Store store = new Store(seller, "시흥마트", null, "031-111-2222", "시흥시", null, null);
        ReflectionTestUtils.setField(store, "id", 10L);

        Product product = new Product(store, "사과", "과일", 15000, "1박스", null, null);
        LocalDateTime now = LocalDateTime.now();
        GroupPurchase gp = new GroupPurchase(store, product, "사과 공구", 12000, 10, 50, 3, now, now.plusDays(3), now.plusDays(4), now.plusDays(5), null, null, GroupPurchase.Status.OPEN);

        User customer = new User("customer@test.com", "hash", "구매자1");
        Pickup pickup = new Pickup(store, gp, customer, "PK-20261028-1111", 1, "구매자1", "010-1234-5678");

        given(pickupRepository.findById(100L)).willReturn(Optional.of(pickup));

        // when & then
        assertThatThrownBy(() -> pickupService.getByIdWithOwnershipCheck(100L, otherSellerId))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("해당 수령 건에 대한 매장 관리 권한이 없습니다.");
    }

    @Test
    @DisplayName("다른 매장의 수령 ID를 해당 매장 URL로 조작 시 BAD_REQUEST(400) 예외 발생")
    void getByIdWithStoreAndOwnershipCheck_mismatchedStore_throwsBadRequest() {
        // given
        Long sellerId = 1L;
        Long storeA = 10L;
        Long storeB = 20L;

        User seller = new User("seller@test.com", "hash", "판매자1");
        ReflectionTestUtils.setField(seller, "id", sellerId);

        Store store = new Store(seller, "시흥마트 1호점", null, "031-111-2222", "시흥시", null, null);
        ReflectionTestUtils.setField(store, "id", storeA);

        Product product = new Product(store, "사과", "과일", 15000, "1박스", null, null);
        LocalDateTime now = LocalDateTime.now();
        GroupPurchase gp = new GroupPurchase(store, product, "사과 공구", 12000, 10, 50, 3, now, now.plusDays(3), now.plusDays(4), now.plusDays(5), null, null, GroupPurchase.Status.OPEN);

        User customer = new User("customer@test.com", "hash", "구매자1");
        Pickup pickup = new Pickup(store, gp, customer, "PK-20261028-1111", 1, "구매자1", "010-1234-5678");

        given(pickupRepository.findById(100L)).willReturn(Optional.of(pickup));

        // when & then: storeB(20L)로 조작 시도
        assertThatThrownBy(() -> pickupService.getByIdWithStoreAndOwnershipCheck(100L, storeB, sellerId))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("해당 매장의 수령 건이 아닙니다");
    }

    @Test
    @DisplayName("수령 코드 스캔으로 수령완료(PICKED_UP) 확정 성공")
    void markPickedUpByCode_success() {
        // given
        Long sellerId = 1L;
        Long storeId = 10L;
        String pickupCode = "PK-20261028-ABCD";

        User seller = new User("seller@test.com", "hash", "판매자1");
        ReflectionTestUtils.setField(seller, "id", sellerId);

        Store store = new Store(seller, "시흥마트", "123-45-67890", "031-123-4567", "시흥시", "101호", "소개");
        ReflectionTestUtils.setField(store, "id", storeId);

        Product product = new Product(store, "사과", "과일", 15000, "1박스", "설명", null);
        LocalDateTime now = LocalDateTime.now();
        GroupPurchase gp = new GroupPurchase(store, product, "사과 공구", 12000, 10, 50, 3, now, now.plusDays(3), now.plusDays(4), now.plusDays(5), "안내", "설명", GroupPurchase.Status.OPEN);

        User customer = new User("customer@test.com", "hash", "구매자1");
        Pickup pickup = new Pickup(store, gp, customer, pickupCode, 1, "구매자1", "010-1234-5678");
        ReflectionTestUtils.setField(pickup, "id", 555L);
        pickup.markReady(); // READY 상태

        given(storeService.getStoreWithOwnershipCheck(storeId, sellerId)).willReturn(store);
        given(pickupRepository.findByPickupCodeAndStoreId(pickupCode, storeId)).willReturn(Optional.of(pickup));
        ReflectionTestUtils.setField(gp, "id", 333L);
        given(groupPurchaseRepository.findByIdWithLock(333L)).willReturn(Optional.of(gp));
        given(pickupRepository.findByIdWithLock(555L)).willReturn(Optional.of(pickup));

        // when
        Pickup completed = pickupService.markPickedUpByCode(pickupCode, storeId, sellerId);

        // then
        assertThat(completed.getStatus()).isEqualTo(Pickup.Status.PICKED_UP);
        assertThat(completed.getPickedUpAt()).isNotNull();
    }

    @Test
    @DisplayName("주문 연동 수령증 수령완료 시 비관적 락으로 조회한 최신 주문 상태가 PAID가 아니면 CONFLICT 예외 발생")
    void markPickedUp_orderRefunded_throwsConflictException() {
        // given
        Long sellerId = 1L;
        Long storeId = 10L;

        User seller = new User("seller@test.com", "hash", "판매자1");
        ReflectionTestUtils.setField(seller, "id", sellerId);

        Store store = new Store(seller, "시흥마트", "123-45-67890", "031-123-4567", "시흥시", "101호", "소개");
        ReflectionTestUtils.setField(store, "id", storeId);

        Product product = new Product(store, "사과", "과일", 15000, "1박스", "설명", null);
        LocalDateTime now = LocalDateTime.now();
        GroupPurchase gp = new GroupPurchase(store, product, "사과 공구", 12000, 10, 50, 3, now, now.plusDays(3), now.plusDays(4), now.plusDays(5), "안내", "설명", GroupPurchase.Status.OPEN);
        ReflectionTestUtils.setField(gp, "id", 333L);

        User customer = new User("customer@test.com", "hash", "구매자1");
        Order order = new Order(customer, gp, 2, 12000, 24000, now.plusMinutes(15));
        ReflectionTestUtils.setField(order, "id", 777L);
        ReflectionTestUtils.setField(order, "status", Order.Status.REFUNDED); // 환불 완료 상태

        Pickup pickup = new Pickup(order, store, gp, customer, "PK-TEST", 2, "구매자1", "010-1234-5678");
        ReflectionTestUtils.setField(pickup, "id", 1002L);

        given(pickupRepository.findById(1002L)).willReturn(Optional.of(pickup));
        given(groupPurchaseRepository.findByIdWithLock(333L)).willReturn(Optional.of(gp));
        given(orderRepository.findPaymentOrderWithLock(777L)).willReturn(Optional.of(order));

        // when & then: 환불된 주문은 CONFLICT(409) 예외 발생
        assertThatThrownBy(() -> pickupService.markPickedUp(1002L, sellerId))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("결제 완료(PAID) 상태인 주문만 수령 처리할 수 있습니다");
    }

    @Test
    @DisplayName("이미 수령 완료된 건을 다시 수령완료 처리하면 BAD_REQUEST 예외 발생")
    void markPickedUp_alreadyCompleted_throwsException() {
        // given
        Long sellerId = 1L;
        Long storeId = 10L;

        User seller = new User("seller@test.com", "hash", "판매자1");
        ReflectionTestUtils.setField(seller, "id", sellerId);

        Store store = new Store(seller, "시흥마트", "123-45-67890", "031-123-4567", "시흥시", "101호", "소개");
        ReflectionTestUtils.setField(store, "id", storeId);

        Product product = new Product(store, "사과", "과일", 15000, "1박스", "설명", null);
        LocalDateTime now = LocalDateTime.now();
        GroupPurchase gp = new GroupPurchase(store, product, "사과 공구", 12000, 10, 50, 3, now, now.plusDays(3), now.plusDays(4), now.plusDays(5), "안내", "설명", GroupPurchase.Status.OPEN);

        User customer = new User("customer@test.com", "hash", "구매자1");
        Pickup pickup = new Pickup(store, gp, customer, "PK-TEST", 1, "구매자1", "010-1234-5678");
        pickup.markReady();
        pickup.markPickedUp(); // 이미 수령 완료된 상태
        ReflectionTestUtils.setField(pickup, "id", 1001L);

        given(pickupRepository.findById(1001L)).willReturn(Optional.of(pickup));
        ReflectionTestUtils.setField(gp, "id", 333L);
        given(groupPurchaseRepository.findByIdWithLock(333L)).willReturn(Optional.of(gp));
        given(pickupRepository.findByIdWithLock(1001L)).willReturn(Optional.of(pickup));

        // when & then
        assertThatThrownBy(() -> pickupService.markPickedUp(1001L, sellerId))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("이미 수령이 완료된 건입니다.");
    }
}
