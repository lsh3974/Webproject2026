package com.siheung.hanbaguni.service;

import com.siheung.hanbaguni.entity.GroupPurchase;
import com.siheung.hanbaguni.entity.Order;
import com.siheung.hanbaguni.entity.Pickup;
import com.siheung.hanbaguni.entity.Product;
import com.siheung.hanbaguni.entity.Store;
import com.siheung.hanbaguni.entity.User;
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

        given(pickupRepository.findByOrderId(500L)).willReturn(Optional.empty());
        given(pickupRepository.existsByPickupCode(any())).willReturn(false);
        given(pickupRepository.saveAndFlush(any(Pickup.class))).willAnswer(invocation -> invocation.getArgument(0));

        // when
        Pickup created = pickupService.createOrGetPickupForOrder(order);

        // then
        assertThat(created).isNotNull();
        assertThat(created.getOrder()).isEqualTo(order);
        assertThat(created.getPickupCode()).startsWith("PK-");
        assertThat(created.getQuantity()).isEqualTo(2);
        assertThat(created.getStatus()).isEqualTo(Pickup.Status.PREPARING);
    }

    @Test
    @DisplayName("결제 미완료 주문에 대해 수령증 발급 시도시 예외 발생")
    void createOrGetPickupForOrder_unpaid_throwsException() {
        // given
        User customer = new User("customer@test.com", "hash", "구매자1");
        Store store = new Store(customer, "시흥마트", "123-45-67890", "031-123-4567", "시흥시", "101호", "소개");
        Product product = new Product(store, "사과", "과일", 15000, "1박스", "설명", null);
        LocalDateTime now = LocalDateTime.now();
        GroupPurchase gp = new GroupPurchase(store, product, "사과 공구", 12000, 10, 50, 3, now, now.plusDays(3), now.plusDays(4), now.plusDays(5), "안내", "설명", GroupPurchase.Status.OPEN);

        Order order = new Order(customer, gp, 2, 12000, 24000, now.plusMinutes(15)); // PENDING 상태

        // when & then
        assertThatThrownBy(() -> pickupService.createOrGetPickupForOrder(order))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("결제 완료(PAID) 상태인 주문만");
    }

    @Test
    @DisplayName("수령 준비완료(READY) 처리 성공")
    void markReady_success() {
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
        Pickup pickup = new Pickup(store, gp, customer, "PK-20261028-TEST", 2, "구매자1", "010-1234-5678");
        ReflectionTestUtils.setField(pickup, "id", 1000L);

        given(pickupRepository.findById(1000L)).willReturn(Optional.of(pickup));

        // when
        pickupService.markReady(1000L, sellerId);

        // then
        assertThat(pickup.getStatus()).isEqualTo(Pickup.Status.READY);
    }

    @Test
    @DisplayName("매장 ID와 수령건의 매장 불일치 시 예외 발생")
    void markReady_storeMismatch_throwsException() {
        // given
        Long sellerId = 1L;
        Long storeId1 = 10L;
        Long storeId2 = 20L;

        User seller = new User("seller@test.com", "hash", "판매자1");
        ReflectionTestUtils.setField(seller, "id", sellerId);

        Store store2 = new Store(seller, "시흥마트2호점", "123-45-67890", "031-123-4567", "시흥시", "101호", "소개");
        ReflectionTestUtils.setField(store2, "id", storeId2);

        Product product = new Product(store2, "사과", "과일", 15000, "1박스", "설명", null);
        LocalDateTime now = LocalDateTime.now();
        GroupPurchase gp = new GroupPurchase(store2, product, "사과 공구", 12000, 10, 50, 3, now, now.plusDays(3), now.plusDays(4), now.plusDays(5), "안내", "설명", GroupPurchase.Status.OPEN);

        User customer = new User("customer@test.com", "hash", "구매자1");
        Pickup pickup = new Pickup(store2, gp, customer, "PK-20261028-TEST", 2, "구매자1", "010-1234-5678");
        ReflectionTestUtils.setField(pickup, "id", 1000L);

        given(pickupRepository.findById(1000L)).willReturn(Optional.of(pickup));

        // when & then
        assertThatThrownBy(() -> pickupService.markReady(1000L, storeId1, sellerId))
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
        pickup.markReady(); // READY 상태

        given(storeService.getStoreWithOwnershipCheck(storeId, sellerId)).willReturn(store);
        given(pickupRepository.findByPickupCodeAndStoreId(pickupCode, storeId)).willReturn(Optional.of(pickup));

        // when
        Pickup completed = pickupService.markPickedUpByCode(pickupCode, storeId, sellerId);

        // then
        assertThat(completed.getStatus()).isEqualTo(Pickup.Status.PICKED_UP);
        assertThat(completed.getPickedUpAt()).isNotNull();
    }

    @Test
    @DisplayName("환불되거나 취소된 주문(REFUNDED)은 수령완료 처리 시 BAD_REQUEST 예외 발생")
    void markPickedUp_orderRefunded_throwsException() {
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
        Order order = new Order(customer, gp, 2, 12000, 24000, now.plusMinutes(15));
        ReflectionTestUtils.setField(order, "status", Order.Status.REFUNDED); // 환불 완료 상태

        Pickup pickup = new Pickup(order, store, gp, customer, "PK-TEST", 2, "구매자1", "010-1234-5678");
        ReflectionTestUtils.setField(pickup, "id", 1002L);

        given(pickupRepository.findById(1002L)).willReturn(Optional.of(pickup));

        // when & then
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
        pickup.markPickedUp(); // 이미 수령 완료된 상태
        ReflectionTestUtils.setField(pickup, "id", 1001L);

        given(pickupRepository.findById(1001L)).willReturn(Optional.of(pickup));

        // when & then
        assertThatThrownBy(() -> pickupService.markPickedUp(1001L, sellerId))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("이미 수령이 완료된 건입니다.");
    }
}
