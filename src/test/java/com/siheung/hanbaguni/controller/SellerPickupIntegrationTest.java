package com.siheung.hanbaguni.controller;

import com.siheung.hanbaguni.entity.*;
import com.siheung.hanbaguni.security.LoginUser;
import com.siheung.hanbaguni.service.PickupService;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:pickup_integ_test;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa", "spring.datasource.password=",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.jpa.hibernate.ddl-auto=create-drop"})
@AutoConfigureMockMvc
@Transactional
class SellerPickupIntegrationTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private EntityManager em;

    @Autowired
    private PickupService pickupService;

    private RequestPostProcessor asUser(User user) {
        return user(new LoginUser(user));
    }

    @Test
    @DisplayName("판매자 수령 목록 화면(index.html) 및 QR 스캔/코드조회 정상 렌더링")
    void testPickupIndexAndFlow() throws Exception {
        // 1. 판매자 & 매장 생성
        User seller = new User("pickup_seller@test.com", "pass", "판매자A");
        ReflectionTestUtils.setField(seller, "role", User.Role.SELLER);
        em.persist(seller);

        Store store = new Store(seller, "정왕청과마트", "111-22-33333", "031-111-2222", "시흥시 정왕동", null, null);
        em.persist(store);

        // 2. 상품 및 공동구매 생성
        Product product = new Product(store, "신선한 딸기", "과일", 12000, "1팩", "설명", null);
        em.persist(product);

        LocalDateTime now = LocalDateTime.now();
        GroupPurchase gp = new GroupPurchase(
                store, product, "딸기 공동구매 특가",
                10000, 10, 50, 5,
                now.minusDays(1), now.plusDays(3),
                now.plusDays(4), now.plusDays(5),
                "카운터에서 수령", "상세안내", GroupPurchase.Status.OPEN
        );
        em.persist(gp);

        // 3. 구매자 및 주문 생성 (PAID)
        User buyer = new User("pickup_buyer@test.com", "pass", "구매자B");
        em.persist(buyer);

        Order order = new Order(buyer, gp, 2, 10000, 20000, now.plusMinutes(15));
        em.persist(order);
        em.flush();

        // 4. 결제 완료 상태 설정 및 수령증 생성
        order.bindPaymentKey("toss_pk_test_123");
        ReflectionTestUtils.setField(order, "status", Order.Status.PAID);
        Pickup pickup = pickupService.createOrGetPickupForOrder(order);
        em.flush();

        // 5. 판매자가 수령 관리 화면 조회
        mvc.perform(get("/seller/stores/" + store.getId() + "/pickups")
                        .with(asUser(seller)))
                .andExpect(status().isOk())
                .andExpect(view().name("seller/pickup/index"))
                .andExpect(content().string(containsString("정왕청과마트")))
                .andExpect(content().string(containsString(pickup.getPickupCode())))
                .andExpect(content().string(containsString("신선한 딸기")));

        // 6. 판매자가 수령 코드로 직접 조회
        mvc.perform(get("/seller/stores/" + store.getId() + "/pickups")
                        .param("searchCode", pickup.getPickupCode())
                        .with(asUser(seller)))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString(pickup.getPickupCode())))
                .andExpect(content().string(containsString("준비중")));

        // 7. 판매자가 상품 준비완료(READY) 처리
        mvc.perform(post("/seller/stores/" + store.getId() + "/pickups/" + pickup.getId() + "/ready")
                        .with(asUser(seller))
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/seller/stores/" + store.getId() + "/pickups"))
                .andExpect(flash().attributeExists("successMessage"));

        em.clear();
        Pickup readyPickup = em.find(Pickup.class, pickup.getId());
        assertThat(readyPickup.getStatus()).isEqualTo(Pickup.Status.READY);

        // 8. 판매자가 QR 스캔 또는 수령완료(COMPLETE) 처리
        mvc.perform(post("/seller/stores/" + store.getId() + "/pickups/scan-complete")
                        .param("pickupCode", pickup.getPickupCode())
                        .with(asUser(seller))
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/seller/stores/" + store.getId() + "/pickups"))
                .andExpect(flash().attributeExists("successMessage"));

        em.clear();
        Pickup completedPickup = em.find(Pickup.class, pickup.getId());
        assertThat(completedPickup.getStatus()).isEqualTo(Pickup.Status.PICKED_UP);
        assertThat(completedPickup.getPickedUpAt()).isNotNull();

        // 9. 구매자가 주문 상세에서 수령 완료 상태 확인
        mvc.perform(get("/orders/" + order.getId())
                        .with(asUser(buyer)))
                .andExpect(status().isOk())
                .andExpect(view().name("order/detail"))
                .andExpect(content().string(containsString("수령 완료")));
    }
}
