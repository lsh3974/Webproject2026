package com.siheung.hanbaguni.service;

import com.siheung.hanbaguni.entity.*;
import com.siheung.hanbaguni.repository.GroupPurchaseRepository;
import com.siheung.hanbaguni.repository.OrderRepository;
import com.siheung.hanbaguni.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class OrderReservationServiceTest {

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private GroupPurchaseRepository groupPurchaseRepository;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private OrderReservationService orderReservationService;

    private User createCustomer(Long id, String email, String nickname) {
        User user = new User(email, "hash", nickname);
        ReflectionTestUtils.setField(user, "id", id);
        return user;
    }

    private GroupPurchase createOpenGroupPurchase(Long gpId, Long sellerId, int groupPrice, int maxQty, Integer limitPerUser) {
        return createOpenGroupPurchase(gpId, sellerId, groupPrice, maxQty, limitPerUser, LocalDateTime.now().plusDays(2));
    }

    private GroupPurchase createOpenGroupPurchase(Long gpId, Long sellerId, int groupPrice, int maxQty, Integer limitPerUser, LocalDateTime endAt) {
        User seller = createCustomer(sellerId, "seller@test.com", "판매자");
        Store store = new Store(seller, "시흥마트", "123-45-67890", "031-123-4567", "시흥시 정왕동", "101호", "동네마트");
        ReflectionTestUtils.setField(store, "id", 10L);

        Product product = new Product(store, "햇사과", "과일", 15000, "박스", "신선한 사과", null);
        ReflectionTestUtils.setField(product, "id", 100L);

        LocalDateTime now = LocalDateTime.now();
        GroupPurchase gp = new GroupPurchase(
                store,
                product,
                "[공동구매] 햇사과 1박스",
                groupPrice,
                10,
                maxQty,
                limitPerUser,
                now.minusDays(1),
                endAt,
                now.plusDays(3),
                now.plusDays(4),
                "카운터 수령",
                "공동구매 안내",
                GroupPurchase.Status.OPEN
        );
        ReflectionTestUtils.setField(gp, "id", gpId);
        return gp;
    }

    @Test
    @DisplayName("공동구매 수량 예약 정상 생성 (단가 스냅샷, 총액 계산, PENDING 상태 및 만료시각 부여)")
    void reserveOrder_success() {
        Long buyerId = 2L;
        Long gpId = 500L;
        User buyer = createCustomer(buyerId, "buyer@test.com", "구매자1");
        GroupPurchase gp = createOpenGroupPurchase(gpId, 1L, 12000, 20, 5);

        given(userRepository.findById(buyerId)).willReturn(Optional.of(buyer));
        given(groupPurchaseRepository.findByIdWithLock(gpId)).willReturn(Optional.of(gp));
        given(orderRepository.findByGroupPurchaseIdAndStatusAndReservedUntilLessThanEqual(
                eq(gpId), eq(Order.Status.PENDING), any(LocalDateTime.class)))
                .willReturn(List.of());
        given(orderRepository.sumQuantityByGroupPurchaseIdAndStatus(gpId, Order.Status.PAID)).willReturn(5);
        given(orderRepository.sumActivePendingQuantity(eq(gpId), any(LocalDateTime.class))).willReturn(3);
        given(orderRepository.sumUserActiveQuantity(eq(gpId), eq(buyerId), any(LocalDateTime.class))).willReturn(1);
        given(orderRepository.save(any(Order.class))).willAnswer(invocation -> {
            Order saved = invocation.getArgument(0);
            ReflectionTestUtils.setField(saved, "id", 1001L);
            return saved;
        });

        Order result = orderReservationService.reserveOrder(buyerId, gpId, 3);

        assertThat(result.getId()).isEqualTo(1001L);
        assertThat(result.getStatus()).isEqualTo(Order.Status.PENDING);
        assertThat(result.getQuantity()).isEqualTo(3);
        assertThat(result.getUnitPrice()).isEqualTo(12000);
        assertThat(result.getTotalPrice()).isEqualTo(36000);
        assertThat(result.getReservedUntil()).isAfter(LocalDateTime.now().plusMinutes(14));
        verify(orderRepository).save(any(Order.class));
    }

    @Test
    @DisplayName("공동구매 마감 시간이 15분 미만으로 남은 경우 예약 유지 시각은 마감 시각(endAt)으로 제한")
    void reserveOrder_capReservedUntilWithEndAt() {
        Long buyerId = 2L;
        Long gpId = 500L;
        User buyer = createCustomer(buyerId, "buyer@test.com", "구매자1");
        LocalDateTime closingSoon = LocalDateTime.now().plusMinutes(5);
        GroupPurchase gp = createOpenGroupPurchase(gpId, 1L, 12000, 20, 5, closingSoon);

        given(userRepository.findById(buyerId)).willReturn(Optional.of(buyer));
        given(groupPurchaseRepository.findByIdWithLock(gpId)).willReturn(Optional.of(gp));
        given(orderRepository.findByGroupPurchaseIdAndStatusAndReservedUntilLessThanEqual(
                eq(gpId), eq(Order.Status.PENDING), any(LocalDateTime.class)))
                .willReturn(List.of());
        given(orderRepository.sumQuantityByGroupPurchaseIdAndStatus(gpId, Order.Status.PAID)).willReturn(0);
        given(orderRepository.sumActivePendingQuantity(eq(gpId), any(LocalDateTime.class))).willReturn(0);
        given(orderRepository.sumUserActiveQuantity(eq(gpId), eq(buyerId), any(LocalDateTime.class))).willReturn(0);
        given(orderRepository.save(any(Order.class))).willAnswer(invocation -> invocation.getArgument(0));

        Order result = orderReservationService.reserveOrder(buyerId, gpId, 1);

        assertThat(result.getReservedUntil()).isEqualTo(closingSoon);
    }

    @Test
    @DisplayName("확정 수량 + 예약 대기 수량 합계가 최대 모집 수량을 초과하면 409 CONFLICT 예외 발생")
    void reserveOrder_exceedsMaxQuantity_throwsConflict() {
        Long buyerId = 2L;
        Long gpId = 500L;
        User buyer = createCustomer(buyerId, "buyer@test.com", "구매자1");
        GroupPurchase gp = createOpenGroupPurchase(gpId, 1L, 12000, 20, null);

        given(userRepository.findById(buyerId)).willReturn(Optional.of(buyer));
        given(groupPurchaseRepository.findByIdWithLock(gpId)).willReturn(Optional.of(gp));
        given(orderRepository.findByGroupPurchaseIdAndStatusAndReservedUntilLessThanEqual(
                eq(gpId), eq(Order.Status.PENDING), any(LocalDateTime.class)))
                .willReturn(List.of());
        // 확정 15개 + 예약선점 4개 = 19개 선점중 (잔여 1개)
        given(orderRepository.sumQuantityByGroupPurchaseIdAndStatus(gpId, Order.Status.PAID)).willReturn(15);
        given(orderRepository.sumActivePendingQuantity(eq(gpId), any(LocalDateTime.class))).willReturn(4);

        assertThatThrownBy(() -> orderReservationService.reserveOrder(buyerId, gpId, 2))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("409 CONFLICT");
    }

    @Test
    @DisplayName("기한이 지난 PENDING 예약은 자동으로 EXPIRED 전환 후 신규 수량 예약 진행")
    void reserveOrder_expiresOverduePendingBeforeCheckingStock() {
        Long buyerId = 2L;
        Long gpId = 500L;
        User buyer = createCustomer(buyerId, "buyer@test.com", "구매자1");
        User prevUser = createCustomer(3L, "prev@test.com", "이전고객");
        GroupPurchase gp = createOpenGroupPurchase(gpId, 1L, 10000, 10, null);

        Order overdueOrder = new Order(prevUser, gp, 5, 10000, 50000, LocalDateTime.now().minusMinutes(5));
        ReflectionTestUtils.setField(overdueOrder, "id", 900L);

        given(userRepository.findById(buyerId)).willReturn(Optional.of(buyer));
        given(groupPurchaseRepository.findByIdWithLock(gpId)).willReturn(Optional.of(gp));
        given(orderRepository.findByGroupPurchaseIdAndStatusAndReservedUntilLessThanEqual(
                eq(gpId), eq(Order.Status.PENDING), any(LocalDateTime.class)))
                .willReturn(List.of(overdueOrder));
        given(orderRepository.sumQuantityByGroupPurchaseIdAndStatus(gpId, Order.Status.PAID)).willReturn(6);
        given(orderRepository.sumActivePendingQuantity(eq(gpId), any(LocalDateTime.class))).willReturn(0);
        given(orderRepository.save(any(Order.class))).willAnswer(invocation -> invocation.getArgument(0));

        Order saved = orderReservationService.reserveOrder(buyerId, gpId, 4);

        assertThat(overdueOrder.getStatus()).isEqualTo(Order.Status.EXPIRED);
        assertThat(saved.getStatus()).isEqualTo(Order.Status.PENDING);
        assertThat(saved.getQuantity()).isEqualTo(4);
    }

    @Test
    @DisplayName("1인당 구매 제한 수량(limitPerUser) 초과 예약 시 400 BAD_REQUEST 예외 발생")
    void reserveOrder_exceedsLimitPerUser_throwsBadRequest() {
        Long buyerId = 2L;
        Long gpId = 500L;
        User buyer = createCustomer(buyerId, "buyer@test.com", "구매자1");
        GroupPurchase gp = createOpenGroupPurchase(gpId, 1L, 12000, 30, 3);

        given(userRepository.findById(buyerId)).willReturn(Optional.of(buyer));
        given(groupPurchaseRepository.findByIdWithLock(gpId)).willReturn(Optional.of(gp));
        given(orderRepository.findByGroupPurchaseIdAndStatusAndReservedUntilLessThanEqual(
                eq(gpId), eq(Order.Status.PENDING), any(LocalDateTime.class)))
                .willReturn(List.of());
        given(orderRepository.sumQuantityByGroupPurchaseIdAndStatus(gpId, Order.Status.PAID)).willReturn(0);
        given(orderRepository.sumActivePendingQuantity(eq(gpId), any(LocalDateTime.class))).willReturn(2);
        // 이미 본인이 2개를 예약한 상태에서 추가로 2개 요청 (제한 3개 초과)
        given(orderRepository.sumUserActiveQuantity(eq(gpId), eq(buyerId), any(LocalDateTime.class))).willReturn(2);

        assertThatThrownBy(() -> orderReservationService.reserveOrder(buyerId, gpId, 2))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("400 BAD_REQUEST");
    }

    @Test
    @DisplayName("판매자가 본인 매장의 공동구매에 예약 시도 시 400 BAD_REQUEST 차단")
    void reserveOrder_sellerOwnStore_throwsBadRequest() {
        Long sellerId = 1L;
        Long gpId = 500L;
        User seller = createCustomer(sellerId, "seller@test.com", "판매자");
        GroupPurchase gp = createOpenGroupPurchase(gpId, sellerId, 12000, 20, null);

        given(userRepository.findById(sellerId)).willReturn(Optional.of(seller));
        given(groupPurchaseRepository.findByIdWithLock(gpId)).willReturn(Optional.of(gp));

        assertThatThrownBy(() -> orderReservationService.reserveOrder(sellerId, gpId, 1))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("400 BAD_REQUEST");
    }

    @Test
    @DisplayName("타인의 주문 상세 조회 시 403 FORBIDDEN 예외 발생")
    void getOrderWithOwnershipCheck_otherUser_throwsForbidden() {
        Long ownerId = 2L;
        Long otherUserId = 99L;
        User owner = createCustomer(ownerId, "buyer@test.com", "구매자1");
        GroupPurchase gp = createOpenGroupPurchase(500L, 1L, 12000, 20, null);

        Order order = new Order(owner, gp, 2, 12000, 24000, LocalDateTime.now().plusMinutes(15));
        ReflectionTestUtils.setField(order, "id", 10L);

        given(orderRepository.findWithDetailsById(10L)).willReturn(Optional.of(order));

        assertThatThrownBy(() -> orderReservationService.getOrderWithOwnershipCheck(10L, otherUserId))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("403 FORBIDDEN");
    }

    @Test
    @DisplayName("예약 대기(PENDING) 상태의 본인 주문 취소 성공 (비관적 락 조회)")
    void cancelReservation_success() {
        Long ownerId = 2L;
        User owner = createCustomer(ownerId, "buyer@test.com", "구매자1");
        GroupPurchase gp = createOpenGroupPurchase(500L, 1L, 12000, 20, null);

        Order order = new Order(owner, gp, 2, 12000, 24000, LocalDateTime.now().plusMinutes(15));
        ReflectionTestUtils.setField(order, "id", 10L);

        given(orderRepository.findByIdWithLock(10L)).willReturn(Optional.of(order));

        Order cancelled = orderReservationService.cancelReservation(10L, ownerId, "수량 변경");

        assertThat(cancelled.getStatus()).isEqualTo(Order.Status.CANCELLED);
        assertThat(cancelled.getCancelReason()).isEqualTo("수량 변경");
    }

    @Test
    @DisplayName("취소 사유가 255자를 초과할 경우 400 BAD_REQUEST 예외 발생")
    void cancelReservation_cancelReasonTooLong_throwsBadRequest() {
        Long ownerId = 2L;
        String longReason = "a".repeat(256);

        assertThatThrownBy(() -> orderReservationService.cancelReservation(10L, ownerId, longReason))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("400 BAD_REQUEST")
                .hasMessageContaining("취소 사유는 255자 이내여야 합니다.");
    }

    @Test
    @DisplayName("이미 결제 완료(PAID)된 주문을 예약 취소로 요청 시 400 BAD_REQUEST 예외 발생")
    void cancelReservation_alreadyPaid_throwsBadRequest() {
        Long ownerId = 2L;
        User owner = createCustomer(ownerId, "buyer@test.com", "구매자1");
        GroupPurchase gp = createOpenGroupPurchase(500L, 1L, 12000, 20, null);

        Order order = new Order(owner, gp, 2, 12000, 24000, LocalDateTime.now().plusMinutes(15));
        ReflectionTestUtils.setField(order, "id", 10L);
        order.markPaid("pay_key_123");

        given(orderRepository.findByIdWithLock(10L)).willReturn(Optional.of(order));

        assertThatThrownBy(() -> orderReservationService.cancelReservation(10L, ownerId, "취소"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("400 BAD_REQUEST");
    }
}
