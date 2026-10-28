package com.siheung.hanbaguni.service;

import com.siheung.hanbaguni.dto.ProductForm;
import com.siheung.hanbaguni.entity.Product;
import com.siheung.hanbaguni.entity.Store;
import com.siheung.hanbaguni.entity.User;
import com.siheung.hanbaguni.repository.ProductRepository;
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
class ProductServiceTest {

    @Mock
    private ProductRepository productRepository;

    @Mock
    private StoreService storeService;

    @InjectMocks
    private ProductService productService;

    @Test
    @DisplayName("상품 생성 성공")
    void createProduct_success() {
        // given
        Long sellerId = 1L;
        Long storeId = 10L;

        User seller = new User("seller@test.com", "hash", "판매자1");
        ReflectionTestUtils.setField(seller, "id", sellerId);

        Store store = new Store(seller, "시흥마트", "123-45-67890", "031-123-4567", "시흥시", "101호", "소개");
        ReflectionTestUtils.setField(store, "id", storeId);

        ProductForm form = new ProductForm();
        form.setName("사과");
        form.setCategory("과일");
        form.setRegularPrice(10000);
        form.setUnit("1kg");
        form.setDescription("달콤한 사과");

        Product savedProduct = new Product(store, form.getName(), form.getCategory(), form.getRegularPrice(),
                form.getUnit(), form.getDescription(), null);
        ReflectionTestUtils.setField(savedProduct, "id", 200L);

        given(storeService.getStoreWithOwnershipCheck(storeId, sellerId)).willReturn(store);
        given(productRepository.save(any(Product.class))).willReturn(savedProduct);

        // when
        Long productId = productService.createProduct(storeId, sellerId, form);

        // then
        assertThat(productId).isEqualTo(200L);
        verify(productRepository).save(any(Product.class));
    }

    @Test
    @DisplayName("타인의 상품 수정 시 403 Forbidden 예외 발생")
    void getProductWithOwnershipCheck_forbidden() {
        // given
        Long ownerId = 1L;
        Long otherUserId = 2L;

        User owner = new User("owner@test.com", "hash", "주인");
        ReflectionTestUtils.setField(owner, "id", ownerId);

        Store store = new Store(owner, "시흥마트", "123-45-67890", "031-123-4567", "시흥시", "101호", "소개");
        ReflectionTestUtils.setField(store, "id", 10L);

        Product product = new Product(store, "사과", "과일", 10000, "1kg", "설명", null);
        ReflectionTestUtils.setField(product, "id", 200L);

        given(productRepository.findById(200L)).willReturn(Optional.of(product));

        // when & then
        assertThatThrownBy(() -> productService.getProductWithOwnershipCheck(200L, otherUserId))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("403 FORBIDDEN");
    }

    @Test
    @DisplayName("상품 정보 수정 성공")
    void updateProduct_success() {
        // given
        Long sellerId = 1L;

        User seller = new User("owner@test.com", "hash", "주인");
        ReflectionTestUtils.setField(seller, "id", sellerId);

        Store store = new Store(seller, "시흥마트", "123-45-67890", "031-123-4567", "시흥시", "101호", "소개");
        ReflectionTestUtils.setField(store, "id", 10L);

        Product product = new Product(store, "사과", "과일", 10000, "1kg", "설명", null);
        ReflectionTestUtils.setField(product, "id", 200L);

        ProductForm updateForm = new ProductForm();
        updateForm.setName("꿀사과");
        updateForm.setCategory("과일");
        updateForm.setRegularPrice(12000);
        updateForm.setUnit("1.5kg");
        updateForm.setStatus(Product.Status.ON_SALE);

        given(productRepository.findById(200L)).willReturn(Optional.of(product));

        // when
        productService.updateProduct(200L, sellerId, updateForm);

        // then
        assertThat(product.getName()).isEqualTo("꿀사과");
        assertThat(product.getRegularPrice()).isEqualTo(12000);
        assertThat(product.getUnit()).isEqualTo("1.5kg");
    }
}
