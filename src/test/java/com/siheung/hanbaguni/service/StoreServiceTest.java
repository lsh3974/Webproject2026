package com.siheung.hanbaguni.service;

import com.siheung.hanbaguni.dto.StoreForm;
import com.siheung.hanbaguni.entity.Store;
import com.siheung.hanbaguni.entity.User;
import com.siheung.hanbaguni.repository.StoreRepository;
import com.siheung.hanbaguni.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class StoreServiceTest {

    @Mock
    private StoreRepository storeRepository;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private StoreService storeService;

    @Test
    @DisplayName("매장 생성 성공")
    void createStore_success() {
        // given
        Long sellerId = 1L;
        User seller = new User("seller@test.com", "hash1234", "판매자1");
        ReflectionTestUtils.setField(seller, "id", sellerId);

        StoreForm form = new StoreForm();
        form.setName("시흥마트");
        form.setBusinessNumber("123-45-67890");
        form.setPhone("031-123-4567");
        form.setAddress("경기도 시흥시 산기대학로 237");
        form.setAddressDetail("101호");
        form.setDescription("동네 마트");

        Store savedStore = new Store(seller, form.getName(), form.getBusinessNumber(), form.getPhone(),
                form.getAddress(), form.getAddressDetail(), form.getDescription());
        ReflectionTestUtils.setField(savedStore, "id", 100L);

        given(userRepository.findById(sellerId)).willReturn(Optional.of(seller));
        given(storeRepository.save(any(Store.class))).willReturn(savedStore);

        // when
        Long storeId = storeService.createStore(sellerId, form);

        // then
        assertThat(storeId).isEqualTo(100L);
        verify(storeRepository).save(any(Store.class));
    }

    @Test
    @DisplayName("소유하지 않은 매장 조회/수정 시 403 Forbidden 예외 발생")
    void getStoreWithOwnershipCheck_forbidden() {
        // given
        Long ownerId = 1L;
        Long otherUserId = 2L;
        User owner = new User("seller@test.com", "hash1234", "판매자1");
        ReflectionTestUtils.setField(owner, "id", ownerId);

        Store store = new Store(owner, "시흥마트", "123-45-67890", "031-123-4567", "시흥시", "101호", "소개");
        ReflectionTestUtils.setField(store, "id", 100L);

        given(storeRepository.findById(100L)).willReturn(Optional.of(store));

        // when & then
        assertThatThrownBy(() -> storeService.getStoreWithOwnershipCheck(100L, otherUserId))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("403 FORBIDDEN");
    }

    @Test
    @DisplayName("매장 정보 수정 성공")
    void updateStore_success() {
        // given
        Long sellerId = 1L;
        User seller = new User("seller@test.com", "hash1234", "판매자1");
        ReflectionTestUtils.setField(seller, "id", sellerId);

        Store store = new Store(seller, "시흥마트", "123-45-67890", "031-123-4567", "시흥시", "101호", "소개");
        ReflectionTestUtils.setField(store, "id", 100L);

        StoreForm updateForm = new StoreForm();
        updateForm.setName("새로운 시흥마트");
        updateForm.setPhone("031-999-9999");
        updateForm.setAddress("시흥시 정왕동");
        updateForm.setStatus(Store.Status.ACTIVE);

        given(storeRepository.findById(100L)).willReturn(Optional.of(store));

        // when
        storeService.updateStore(100L, sellerId, updateForm);

        // then
        assertThat(store.getName()).isEqualTo("새로운 시흥마트");
        assertThat(store.getPhone()).isEqualTo("031-999-9999");
    }
}
