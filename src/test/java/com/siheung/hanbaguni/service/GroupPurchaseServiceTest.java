package com.siheung.hanbaguni.service;

import com.siheung.hanbaguni.dto.GroupPurchaseForm;
import com.siheung.hanbaguni.entity.GroupPurchase;
import com.siheung.hanbaguni.entity.Product;
import com.siheung.hanbaguni.entity.Store;
import com.siheung.hanbaguni.entity.User;
import com.siheung.hanbaguni.repository.GroupPurchaseRepository;
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
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class GroupPurchaseServiceTest {

    @Mock
    private GroupPurchaseRepository groupPurchaseRepository;

    @Mock
    private StoreService storeService;

    @Mock
    private ProductService productService;

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
        given(productService.getProductWithOwnershipCheck(productId, sellerId)).willReturn(product);
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
        given(productService.getProductWithOwnershipCheck(productId, sellerId)).willReturn(product);
        given(groupPurchaseRepository.save(any(GroupPurchase.class))).willReturn(savedGp);

        // when
        Long gpId = groupPurchaseService.createGroupPurchase(storeId, sellerId, form);

        // then
        assertThat(gpId).isEqualTo(501L);
        assertThat(savedGp.getLimitPerUser()).isNull();
        verify(groupPurchaseRepository).save(any(GroupPurchase.class));
    }

    @Test
    @DisplayName("초안 상태의 공동구매를 공개 게시(OPEN)로 전환 성공")
    void publishGroupPurchase_success() {
        // given
        Long sellerId = 1L;
        User seller = new User("seller@test.com", "hash", "판매자1");
        ReflectionTestUtils.setField(seller, "id", sellerId);

        Store store = new Store(seller, "시흥마트", "123-45-67890", "031-123-4567", "시흥시", "101호", "소개");
        ReflectionTestUtils.setField(store, "id", 10L);

        Product product = new Product(store, "사과", "과일", 15000, "1박스", "설명", null);
        ReflectionTestUtils.setField(product, "id", 100L);

        LocalDateTime now = LocalDateTime.now();
        GroupPurchase gp = new GroupPurchase(
                store, product, "사과 공구", 12000, 10, 50, 3,
                now, now.plusDays(3), now.plusDays(4), now.plusDays(5),
                "안내", "설명", GroupPurchase.Status.DRAFT
        );
        ReflectionTestUtils.setField(gp, "id", 500L);

        given(groupPurchaseRepository.findById(500L)).willReturn(Optional.of(gp));

        // when
        groupPurchaseService.publishGroupPurchase(500L, sellerId);

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
}
