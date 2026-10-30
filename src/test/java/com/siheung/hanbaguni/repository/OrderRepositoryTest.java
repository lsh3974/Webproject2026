package com.siheung.hanbaguni.repository;

import com.siheung.hanbaguni.entity.Order;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Transactional
class OrderRepositoryTest {

    @Autowired
    private OrderRepository orderRepository;

    @Test
    @DisplayName("회원 ID로 주문 목록 조회 시 JPQL 문법 및 EntityGraph 정상 동작 검증")
    void testFindByUserIdOrderByIdDesc() {
        List<Order> orders = orderRepository.findByUserIdOrderByIdDesc(1L);
        assertThat(orders).isNotNull();
    }

    @Test
    @DisplayName("기한 만료된 PENDING 주문 조회 JPQL 정상 동작 검증")
    void testFindExpiredPendingQueries() {
        LocalDateTime now = LocalDateTime.now();
        List<Order> userExpired = orderRepository
                .findByUserIdAndStatusAndReservedUntilLessThanEqual(1L, Order.Status.PENDING, now);
        assertThat(userExpired).isNotNull();

        List<Order> gpExpired = orderRepository
                .findByGroupPurchaseIdAndStatusAndReservedUntilLessThanEqual(1L, Order.Status.PENDING, now);
        assertThat(gpExpired).isNotNull();
    }
}
