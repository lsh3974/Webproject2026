package com.siheung.hanbaguni.service;

import com.siheung.hanbaguni.dto.GroupPurchaseForm;
import com.siheung.hanbaguni.entity.GroupPurchase;
import com.siheung.hanbaguni.entity.Order;
import com.siheung.hanbaguni.entity.Product;
import com.siheung.hanbaguni.entity.Store;
import com.siheung.hanbaguni.entity.User;
import com.siheung.hanbaguni.repository.GroupPurchaseRepository;
import com.siheung.hanbaguni.repository.OrderRepository;
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
class GroupPurchaseServiceTest {

    @Mock
    private GroupPurchaseRepository groupPurchaseRepository;

    @Mock
    private StoreService storeService;

    @Mock
    private ProductService productService;

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private jakarta.persistence.EntityManager entityManager;

    @InjectMocks
    private GroupPurchaseService groupPurchaseService;

    @Test
    @DisplayName("공동구매 모집 등록 성공 (초안 저장, 1인당 제한수량 지정)")
    void createGroupPurchase_draft_success() {
        // given
        Long sellerId = 1L;
        Long storeId = 10L;
        Long productId = 100L;

        User seller = new User("seller@test.com", "hash", "판매자1");
        ReflectionTestUtils.setField(seller, "id", sellerId);

        Store store = new Store(seller, "시흥마트", "123-45-67890", "031-123-4567", "시흥시", "101호", "소개");
        ReflectionTestUtils.setField(store, "id", storeId);

        Product product = new Product(store, "사과", "과일", 15000, "1박스", "설명", null);
        ReflectionTestUtils.setField(product, "id", productId);

        LocalDateTime now = LocalDateTime.now();
        GroupPurchaseForm form = new GroupPurchaseForm();
        form.setProductId(productId);
        form.setTitle("[공동구매] 사과 1박스");
        form.setGroupPrice(12000);
        form.setTargetQuantity(10);
        form.setMaxQuantity(50);
        form.setLimitPerUser(3);
        form.setStartAt(now);
        form.setEndAt(now.plusDays(3));
        form.setPickupStartAt(now.plusDays(4));
        form.setPickupEndAt(now.plusDays(5));
        form.setPickupNotice("카운터 수령");
        form.setStatus(GroupPurchase.Status.DRAFT);

        GroupPurchase savedGp = new GroupPurchase(
                store, product, form.getTitle(), form.getGroupPrice(),
                form.getTargetQuantity(), form.getMaxQuantity(), form.getLimitPerUser(),
                form.getStartAt(), form.getEndAt(), form.getPickupStartAt(), form.getPickupEndAt(),
                form.getPickupNotice(), null, GroupPurchase.Status.DRAFT
        );
        ReflectionTestUtils.setField(savedGp, "id", 500L);

        given(storeService.getStoreWithOwnershipCheck(storeId, sellerId)).willReturn(store);
        given(productService.getProductWithStoreAndOwnershipCheck(storeId, productId, sellerId)).willReturn(product);
        given(groupPurchaseRepository.save(any(GroupPurchase.class))).willReturn(savedGp);

        // when
        Long gpId = groupPurchaseService.createGroupPurchase(storeId, sellerId, form);

        // then
        assertThat(gpId).isEqualTo(500L);
        verify(groupPurchaseRepository).save(any(GroupPurchase.class));
    }

    @Test
    @DisplayName("공동구매 모집 등록 성공 (1인당 제한수량 미입력/null 허용)")
    void createGroupPurchase_unlimitedPerUser_success() {
        // given
        Long sellerId = 1L;
        Long storeId = 10L;
        Long productId = 100L;

        User seller = new User("seller@test.com", "hash", "판매자1");
        ReflectionTestUtils.setField(seller, "id", sellerId);

        Store store = new Store(seller, "시흥마트", "123-45-67890", "031-123-4567", "시흥시", "101호", "소개");
        ReflectionTestUtils.setField(store, "id", storeId);

        Product product = new Product(store, "사과", "과일", 15000, "1박스", "설명", null);
        ReflectionTestUtils.setField(product, "id", productId);

        LocalDateTime now = LocalDateTime.now();
        GroupPurchaseForm form = new GroupPurchaseForm();
        form.setProductId(productId);
        form.setTitle("[공동구매] 사과 1박스 (무제한)");
        form.setGroupPrice(12000);
        form.setTargetQuantity(10);
        form.setMaxQuantity(50);
        form.setLimitPerUser(null); // 제한 없음
        form.setStartAt(now);
        form.setEndAt(now.plusDays(3));
        form.setPickupStartAt(now.plusDays(4));
        form.setPickupEndAt(now.plusDays(5));
        form.setStatus(GroupPurchase.Status.OPEN);

        GroupPurchase savedGp = new GroupPurchase(
                store, product, form.getTitle(), form.getGroupPrice(),
                form.getTargetQuantity(), form.getMaxQuantity(), null,
                form.getStartAt(), form.getEndAt(), form.getPickupStartAt(), form.getPickupEndAt(),
                null, null, GroupPurchase.Status.OPEN
        );
        ReflectionTestUtils.setField(savedGp, "id", 501L);

        given(storeService.getStoreWithOwnershipCheck(storeId, sellerId)).willReturn(store);
        given(productService.getProductWithStoreAndOwnershipCheck(storeId, productId, sellerId)).willReturn(product);
        given(groupPurchaseRepository.save(any(GroupPurchase.class))).willReturn(savedGp);

        // when
        Long gpId = groupPurchaseService.createGroupPurchase(storeId, sellerId, form);

        // then
        assertThat(gpId).isEqualTo(501L);
        assertThat(savedGp.getLimitPerUser()).isNull();
        verify(groupPurchaseRepository).save(any(GroupPurchase.class));
    }

    @Test
    @DisplayName("초안 상태의 공동구매를 공개 게시(OPEN)로 전환 성공 (비관적 락 적용)")
    void publishGroupPurchase_success() {
        // given
        Long sellerId = 1L;
        Long storeId = 10L;
        User seller = new User("seller@test.com", "hash", "판매자1");
        ReflectionTestUtils.setField(seller, "id", sellerId);

        Store store = new Store(seller, "시흥마트", "123-45-67890", "031-123-4567", "시흥시", "101호", "소개");
        ReflectionTestUtils.setField(store, "id", storeId);

        Product product = new Product(store, "사과", "과일", 15000, "1박스", "설명", null);
        ReflectionTestUtils.setField(product, "id", 100L);

        LocalDateTime now = LocalDateTime.now();
        GroupPurchase gp = new GroupPurchase(
                store, product, "사과 공구", 12000, 10, 50, 3,
                now, now.plusDays(3), now.plusDays(4), now.plusDays(5),
                "안내", "설명", GroupPurchase.Status.DRAFT
        );
        ReflectionTestUtils.setField(gp, "id", 500L);

        given(groupPurchaseRepository.findByIdWithLock(500L)).willReturn(Optional.of(gp));

        // when
        groupPurchaseService.publishGroupPurchase(storeId, 500L, sellerId);

        // then
        assertThat(gp.getStatus()).isEqualTo(GroupPurchase.Status.OPEN);
    }

    @Test
    @DisplayName("타인의 공동구매 모집 접근/수정 시 403 Forbidden 예외 발생")
    void getWithOwnershipCheck_forbidden() {
        // given
        Long ownerId = 1L;
        Long otherSellerId = 2L;

        User owner = new User("owner@test.com", "hash", "주인");
        ReflectionTestUtils.setField(owner, "id", ownerId);

        Store store = new Store(owner, "시흥마트", "123-45-67890", "031-123-4567", "시흥시", "101호", "소개");
        ReflectionTestUtils.setField(store, "id", 10L);

        Product product = new Product(store, "사과", "과일", 15000, "1박스", "설명", null);

        LocalDateTime now = LocalDateTime.now();
        GroupPurchase gp = new GroupPurchase(
                store, product, "사과 공구", 12000, 10, 50, 3,
                now, now.plusDays(3), now.plusDays(4), now.plusDays(5),
                "안내", "설명", GroupPurchase.Status.DRAFT
        );
        ReflectionTestUtils.setField(gp, "id", 500L);

        given(groupPurchaseRepository.findById(500L)).willReturn(Optional.of(gp));

        // when & then
        assertThatThrownBy(() -> groupPurchaseService.getWithOwnershipCheck(500L, otherSellerId))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("403 FORBIDDEN");
    }

    @Test
    @DisplayName("같은 판매자 소유라도 URL 매장 ID와 공동구매 소속 매장이 다르면 404 NOT_FOUND 발생")
    void getWithStoreAndOwnershipCheck_storeMismatch() {
        // given
        Long sellerId = 1L;
        Long storeAId = 10L;
        Long storeBId = 20L;

        User seller = new User("owner@test.com", "hash", "주인");
        ReflectionTestUtils.setField(seller, "id", sellerId);

        Store storeB = new Store(seller, "시흥2호점", "123-45-67891", "031-123-4568", "시흥시", "201호", "소개");
        ReflectionTestUtils.setField(storeB, "id", storeBId);

        Product product = new Product(storeB, "사과", "과일", 15000, "1박스", "설명", null);
        LocalDateTime now = LocalDateTime.now();
        GroupPurchase gp = new GroupPurchase(
                storeB, product, "사과 공구", 12000, 10, 50, 3,
                now, now.plusDays(3), now.plusDays(4), now.plusDays(5),
                "안내", "설명", GroupPurchase.Status.DRAFT
        );
        ReflectionTestUtils.setField(gp, "id", 500L);

        given(groupPurchaseRepository.findById(500L)).willReturn(Optional.of(gp));

        // when & then
        assertThatThrownBy(() -> groupPurchaseService.getWithStoreAndOwnershipCheck(storeAId, 500L, sellerId))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("404 NOT_FOUND");
    }

    @Test
    @DisplayName("주문 또는 예약 내역이 존재하는 공동구매 삭제 시 400 BAD_REQUEST 예외 발생 (비관적 락 적용)")
    void deleteGroupPurchase_withExistingOrders_throwsBadRequest() {
        // given
        Long sellerId = 1L;
        Long storeId = 10L;
        Long gpId = 500L;

        User seller = new User("owner@test.com", "hash", "주인");
        ReflectionTestUtils.setField(seller, "id", sellerId);

        Store store = new Store(seller, "시흥마트", "123-45-67890", "031-123-4567", "시흥시", "101호", "소개");
        ReflectionTestUtils.setField(store, "id", storeId);

        Product product = new Product(store, "사과", "과일", 15000, "1박스", "설명", null);
        LocalDateTime now = LocalDateTime.now();
        GroupPurchase gp = new GroupPurchase(
                store, product, "사과 공구", 12000, 10, 50, 3,
                now, now.plusDays(3), now.plusDays(4), now.plusDays(5),
                "안내", "설명", GroupPurchase.Status.OPEN
        );
        ReflectionTestUtils.setField(gp, "id", gpId);

        given(groupPurchaseRepository.findByIdWithLock(gpId)).willReturn(Optional.of(gp));
        given(orderRepository.existsByGroupPurchaseId(gpId)).willReturn(true);

        // when & then
        assertThatThrownBy(() -> groupPurchaseService.deleteGroupPurchase(storeId, gpId, sellerId))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("400 BAD_REQUEST")
                .hasMessageContaining("이미 예약 또는 주문 내역이 있는 공동구매는 삭제할 수 없습니다.");
    }

    @Test
    @DisplayName("주문/예약 내역이 없고 참여자 수 0인 공동구매 삭제 성공 (비관적 락 적용)")
    void deleteGroupPurchase_success() {
        // given
        Long sellerId = 1L;
        Long storeId = 10L;
        Long gpId = 500L;

        User seller = new User("owner@test.com", "hash", "주인");
        ReflectionTestUtils.setField(seller, "id", sellerId);

        Store store = new Store(seller, "시흥마트", "123-45-67890", "031-123-4567", "시흥시", "101호", "소개");
        ReflectionTestUtils.setField(store, "id", storeId);

        Product product = new Product(store, "사과", "과일", 15000, "1박스", "설명", null);
        LocalDateTime now = LocalDateTime.now();
        GroupPurchase gp = new GroupPurchase(
                store, product, "사과 공구", 12000, 10, 50, 3,
                now, now.plusDays(3), now.plusDays(4), now.plusDays(5),
                "안내", "설명", GroupPurchase.Status.DRAFT
        );
        ReflectionTestUtils.setField(gp, "id", gpId);

        given(groupPurchaseRepository.findByIdWithLock(gpId)).willReturn(Optional.of(gp));
        given(orderRepository.existsByGroupPurchaseId(gpId)).willReturn(false);

        // when
        groupPurchaseService.deleteGroupPurchase(storeId, gpId, sellerId);

        // then
        verify(groupPurchaseRepository).delete(gp);
    }

    @Test
    @DisplayName("공동구매 수정 시 최대 수량을 현재 점유(확정+예약) 수량 미만으로 축소 시 400 BAD_REQUEST 발생")
    void updateGroupPurchase_maxQuantityLessThanOccupied_throwsBadRequest() {
        Long sellerId = 1L;
        Long storeId = 10L;
        Long gpId = 500L;

        User seller = new User("owner@test.com", "hash", "주인");
        ReflectionTestUtils.setField(seller, "id", sellerId);

        Store store = new Store(seller, "시흥마트", "123-45-67890", "031-123-4567", "시흥시", "101호", "소개");
        ReflectionTestUtils.setField(store, "id", storeId);

        Product product = new Product(store, "사과", "과일", 15000, "1박스", "설명", null);
        LocalDateTime now = LocalDateTime.now();
        GroupPurchase gp = new GroupPurchase(
                store, product, "사과 공구", 12000, 10, 50, 5,
                now, now.plusDays(3), now.plusDays(4), now.plusDays(5),
                "안내", "설명", GroupPurchase.Status.OPEN
        );
        ReflectionTestUtils.setField(gp, "id", gpId);

        given(groupPurchaseRepository.findByIdWithLock(gpId)).willReturn(Optional.of(gp));
        given(orderRepository.sumQuantityByGroupPurchaseIdAndStatus(eq(gpId), eq(Order.Status.PAID))).willReturn(10);
        given(orderRepository.sumActivePendingQuantity(eq(gpId), any())).willReturn(15);
        // 총 점유 수량 = 25개

        GroupPurchaseForm form = new GroupPurchaseForm();
        form.setTitle("사과 공구");
        form.setGroupPrice(12000);
        form.setTargetQuantity(10);
        form.setMaxQuantity(20); // 25개보다 작은 20개로 축소 시도
        form.setLimitPerUser(5);
        form.setStartAt(now);
        form.setEndAt(now.plusDays(3));
        form.setPickupStartAt(now.plusDays(4));
        form.setPickupEndAt(now.plusDays(5));
        form.setStatus(GroupPurchase.Status.OPEN);

        assertThatThrownBy(() -> groupPurchaseService.updateGroupPurchase(storeId, gpId, sellerId, form))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("400 BAD_REQUEST")
                .hasMessageContaining("최대 모집 수량(20개)은 현재 점유된 수량(25개) 이상이어야 합니다.");
    }

    @Test
    @DisplayName("공동구매 수정 시 1인당 구매 제한을 기존 회원의 주문 수량 미만으로 축소 시 400 BAD_REQUEST 발생")
    void updateGroupPurchase_limitPerUserLessThanExistingOrders_throwsBadRequest() {
        Long sellerId = 1L;
        Long storeId = 10L;
        Long gpId = 500L;

        User seller = new User("owner@test.com", "hash", "주인");
        ReflectionTestUtils.setField(seller, "id", sellerId);

        Store store = new Store(seller, "시흥마트", "123-45-67890", "031-123-4567", "시흥시", "101호", "소개");
        ReflectionTestUtils.setField(store, "id", storeId);

        Product product = new Product(store, "사과", "과일", 15000, "1박스", "설명", null);
        LocalDateTime now = LocalDateTime.now();
        GroupPurchase gp = new GroupPurchase(
                store, product, "사과 공구", 12000, 10, 50, 10,
                now, now.plusDays(3), now.plusDays(4), now.plusDays(5),
                "안내", "설명", GroupPurchase.Status.OPEN
        );
        ReflectionTestUtils.setField(gp, "id", gpId);

        given(groupPurchaseRepository.findByIdWithLock(gpId)).willReturn(Optional.of(gp));
        given(orderRepository.sumQuantityByGroupPurchaseIdAndStatus(eq(gpId), eq(Order.Status.PAID))).willReturn(5);
        given(orderRepository.sumActivePendingQuantity(eq(gpId), any())).willReturn(0);
        given(orderRepository.findActiveUserQuantities(eq(gpId), any())).willReturn(List.of(5L, 3L)); // 한 회원이 5개 주문 보유

        GroupPurchaseForm form = new GroupPurchaseForm();
        form.setTitle("사과 공구");
        form.setGroupPrice(12000);
        form.setTargetQuantity(10);
        form.setMaxQuantity(50);
        form.setLimitPerUser(3); // 5개 보유 회원이 있는데 3개로 제한 축소 시도
        form.setStartAt(now);
        form.setEndAt(now.plusDays(3));
        form.setPickupStartAt(now.plusDays(4));
        form.setPickupEndAt(now.plusDays(5));
        form.setStatus(GroupPurchase.Status.OPEN);

        assertThatThrownBy(() -> groupPurchaseService.updateGroupPurchase(storeId, gpId, sellerId, form))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("400 BAD_REQUEST")
                .hasMessageContaining("기존 예약/주문 수량(5개)이 변경하려는 1인당 구매 제한(3개)을 초과하는 회원이 있어");
    }

    @Test
    @DisplayName("공동구매 수정 시 결제 대기 중인 예약의 만료 기한보다 앞당겨 마감 일시 설정 시 400 BAD_REQUEST 발생")
    void updateGroupPurchase_endAtBeforeReservationExpiry_throwsBadRequest() {
        Long sellerId = 1L;
        Long storeId = 10L;
        Long gpId = 500L;

        User seller = new User("owner@test.com", "hash", "주인");
        ReflectionTestUtils.setField(seller, "id", sellerId);

        Store store = new Store(seller, "시흥마트", "123-45-67890", "031-123-4567", "시흥시", "101호", "소개");
        ReflectionTestUtils.setField(store, "id", storeId);

        Product product = new Product(store, "사과", "과일", 15000, "1박스", "설명", null);
        LocalDateTime now = LocalDateTime.now();
        GroupPurchase gp = new GroupPurchase(
                store, product, "사과 공구", 12000, 10, 50, 5,
                now, now.plusDays(3), now.plusDays(4), now.plusDays(5),
                "안내", "설명", GroupPurchase.Status.OPEN
        );
        ReflectionTestUtils.setField(gp, "id", gpId);

        LocalDateTime reservationExpiry = now.plusMinutes(15);
        given(groupPurchaseRepository.findByIdWithLock(gpId)).willReturn(Optional.of(gp));
        given(orderRepository.sumQuantityByGroupPurchaseIdAndStatus(eq(gpId), eq(Order.Status.PAID))).willReturn(0);
        given(orderRepository.sumActivePendingQuantity(eq(gpId), any())).willReturn(2); // 유효 예약 2개 존재
        given(orderRepository.findActiveUserQuantities(eq(gpId), any())).willReturn(List.of(2L));
        given(orderRepository.findMaxReservedUntil(eq(gpId), any())).willReturn(Optional.of(reservationExpiry));

        GroupPurchaseForm form = new GroupPurchaseForm();
        form.setTitle("사과 공구");
        form.setGroupPrice(12000);
        form.setTargetQuantity(10);
        form.setMaxQuantity(50);
        form.setLimitPerUser(5);
        form.setStartAt(now);
        form.setEndAt(now.plusMinutes(10)); // 예약 만료 15분 후보다 이른 10분 후로 앞당김
        form.setPickupStartAt(now.plusDays(4));
        form.setPickupEndAt(now.plusDays(5));
        form.setStatus(GroupPurchase.Status.OPEN);

        assertThatThrownBy(() -> groupPurchaseService.updateGroupPurchase(storeId, gpId, sellerId, form))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("400 BAD_REQUEST")
                .hasMessageContaining("현재 결제 대기 중인 예약의 만료 일시보다 이른 시각으로 마감 일시를 변경할 수 없습니다.");
    }

    @Test
    @DisplayName("유효한 결제 대기 예약이 있는 경우 CLOSED 상태 변경 시 400 BAD_REQUEST 발생")
    void updateStatus_closedWithPendingReservations_throwsBadRequest() {
        Long sellerId = 1L;
        Long storeId = 10L;
        Long gpId = 500L;

        User seller = new User("owner@test.com", "hash", "주인");
        ReflectionTestUtils.setField(seller, "id", sellerId);

        Store store = new Store(seller, "시흥마트", "123-45-67890", "031-123-4567", "시흥시", "101호", "소개");
        ReflectionTestUtils.setField(store, "id", storeId);

        Product product = new Product(store, "사과", "과일", 15000, "1박스", "설명", null);
        LocalDateTime now = LocalDateTime.now();
        GroupPurchase gp = new GroupPurchase(
                store, product, "사과 공구", 12000, 10, 50, 5,
                now, now.plusDays(3), now.plusDays(4), now.plusDays(5),
                "안내", "설명", GroupPurchase.Status.OPEN
        );
        ReflectionTestUtils.setField(gp, "id", gpId);

        given(groupPurchaseRepository.findByIdWithLock(gpId)).willReturn(Optional.of(gp));
        given(orderRepository.sumQuantityByGroupPurchaseIdAndStatus(eq(gpId), eq(Order.Status.PAID))).willReturn(0);
        given(orderRepository.sumActivePendingQuantity(eq(gpId), any())).willReturn(3); // 결제 대기 3개

        assertThatThrownBy(() -> groupPurchaseService.updateStatus(storeId, gpId, sellerId, GroupPurchase.Status.CLOSED))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("400 BAD_REQUEST")
                .hasMessageContaining("현재 결제 대기 중인 유효 예약(3개)이 존재하여 상태를 마감(CLOSED)으로 변경할 수 없습니다.");
    }

    @Test
    @DisplayName("예약 또는 주문 내역이 있는 경우 DRAFT 상태로 변경 시 400 BAD_REQUEST 발생")
    void updateStatus_draftWithOrders_throwsBadRequest() {
        Long sellerId = 1L;
        Long storeId = 10L;
        Long gpId = 500L;

        User seller = new User("owner@test.com", "hash", "주인");
        ReflectionTestUtils.setField(seller, "id", sellerId);

        Store store = new Store(seller, "시흥마트", "123-45-67890", "031-123-4567", "시흥시", "101호", "소개");
        ReflectionTestUtils.setField(store, "id", storeId);

        Product product = new Product(store, "사과", "과일", 15000, "1박스", "설명", null);
        LocalDateTime now = LocalDateTime.now();
        GroupPurchase gp = new GroupPurchase(
                store, product, "사과 공구", 12000, 10, 50, 5,
                now, now.plusDays(3), now.plusDays(4), now.plusDays(5),
                "안내", "설명", GroupPurchase.Status.OPEN
        );
        ReflectionTestUtils.setField(gp, "id", gpId);

        given(groupPurchaseRepository.findByIdWithLock(gpId)).willReturn(Optional.of(gp));
        given(orderRepository.sumQuantityByGroupPurchaseIdAndStatus(eq(gpId), eq(Order.Status.PAID))).willReturn(2);
        given(orderRepository.sumActivePendingQuantity(eq(gpId), any())).willReturn(0);

        assertThatThrownBy(() -> groupPurchaseService.updateStatus(storeId, gpId, sellerId, GroupPurchase.Status.DRAFT))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("400 BAD_REQUEST")
                .hasMessageContaining("이미 예약 또는 주문 내역이 존재하는 공동구매는 초안(DRAFT) 상태로 되돌릴 수 없습니다.");
    }
}
