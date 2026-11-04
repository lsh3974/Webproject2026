package com.siheung.hanbaguni.controller;

import com.siheung.hanbaguni.entity.*;
import com.siheung.hanbaguni.payment.RefundService;
import com.siheung.hanbaguni.payment.TossPaymentGateway;
import com.siheung.hanbaguni.security.LoginUser;
import com.siheung.hanbaguni.service.PickupService;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
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

    @Autowired
    private RefundService refundService;

    @MockitoBean
    private TossPaymentGateway gateway;

    private RequestPostProcessor asUser(User user) {
        return user(new LoginUser(user));
    }

    @Test
    @DisplayName("판매자 수령 목록 화면 및 QR 스캔/코드조회, 수령완료 후 환불 차단 검증")
    void testPickupIndexAndFlow() throws Exception {
        // 1. 판매자 & 매장 생성
        User seller = new User("pickup_seller@test.com", "pass", "판매자A");
        ReflectionTestUtils.setField(seller, "role", User.Role.SELLER);
        em.persist(seller);

        Store store = new Store(seller, "싱싱마트", "123-45-67890", "031-123-4567", "시흥시 정왕동", null, null);
        em.persist(store);

        // 2. 상품 & 공동구매 생성
        Product product = new Product(store, "신선 사과", "과일", 12000, "1박스", "맛있는 사과", null);
        em.persist(product);

        LocalDateTime now = LocalDateTime.now();
        GroupPurchase gp = new GroupPurchase(
                store, product, "사과 공구 1탄",
                10000, 2, 20, 5,
                now.minusDays(2), now.plusDays(1),
                now.plusDays(4), now.plusDays(5),
                "카운터에 문의", "박스 훼손 시 교환 불가", GroupPurchase.Status.OPEN
        );
        em.persist(gp);

        // 3. 구매자 & 결제완료(PAID) 주문 생성
        User buyer = new User("pickup_buyer@test.com", "pass", "구매자B");
        ReflectionTestUtils.setField(buyer, "phoneE164", "010-1234-5678");
        em.persist(buyer);

        Order order = new Order(buyer, gp, 2, 10000, 20000, now.plusMinutes(15));
        ReflectionTestUtils.setField(order, "status", Order.Status.PAID);
        ReflectionTestUtils.setField(order, "paymentKey", "toss_pk_test_123");
        em.persist(order);
        em.flush();

        // 4. 수령증 생성 (초기 상태: PREPARING)
        Pickup pickup = pickupService.createOrGetPickupForOrder(order);
        assertThat(pickup).isNotNull();
        assertThat(pickup.getStatus()).isEqualTo(Pickup.Status.PREPARING);

        // 5. 판매자가 수령 관리 대시보드 접근 확인
        mvc.perform(get("/seller/stores/" + store.getId() + "/pickups")
                        .with(asUser(seller)))
                .andExpect(status().isOk())
                .andExpect(view().name("seller/pickup/index"))
                .andExpect(model().attributeExists("pickups", "groupPurchases", "store"));

        // 6. QR 코드 조회 API 확인 (전화번호 마스킹 확인)
        mvc.perform(get("/seller/stores/" + store.getId() + "/pickups/api/lookup")
                        .param("code", pickup.getPickupCode())
                        .with(asUser(seller)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pickupCode").value(pickup.getPickupCode()))
                .andExpect(jsonPath("$.orderId").value(order.getId()))
                .andExpect(jsonPath("$.customerPhone").value("010-****-5678"));

        // 7. 판매자가 '준비 완료' 처리
        mvc.perform(post("/seller/stores/" + store.getId() + "/pickups/" + pickup.getId() + "/ready")
                        .with(asUser(seller))
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/seller/stores/" + store.getId() + "/pickups"))
                .andExpect(flash().attributeExists("successMessage"));

        em.clear();
        Pickup readyPickup = em.find(Pickup.class, pickup.getId());
        assertThat(readyPickup.getStatus()).isEqualTo(Pickup.Status.READY);

        // 8. 판매자가 '수령 완료' 처리
        mvc.perform(post("/seller/stores/" + store.getId() + "/pickups/" + pickup.getId() + "/complete")
                        .with(asUser(seller))
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/seller/stores/" + store.getId() + "/pickups"))
                .andExpect(flash().attributeExists("successMessage"));

        em.clear();
        Pickup completedPickup = em.find(Pickup.class, pickup.getId());
        assertThat(completedPickup.getStatus()).isEqualTo(Pickup.Status.PICKED_UP);
        assertThat(completedPickup.getPickedUpAt()).isNotNull();

        // 9. 구매자가 주문 상세에서 수령 완료 상태 확인 및 환불 버튼 미노출 확인
        mvc.perform(get("/orders/" + order.getId())
                        .with(asUser(buyer)))
                .andExpect(status().isOk())
                .andExpect(view().name("order/detail"))
                .andExpect(content().string(containsString("수령 완료")))
                .andExpect(content().string(not(containsString("주문 취소 및 환불 요청"))));

        // 10. 수령 완료된 주문에 대해 환불 요청 시 서버 예외(CONFLICT) 발생 검증
        assertThatThrownBy(() -> refundService.cancelMyOrder(order.getId(), buyer.getId(), "단순 변심"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("이미 매장에서 수령 완료된 상품은 주문을 취소하거나 환불받을 수 없습니다");
    }

    @Test
    @DisplayName("다른 매장의 수령 건을 조작하려 하면 예외 및 오류 메시지 처리")
    void testStoreMismatch() throws Exception {
        User seller = new User("seller_mismatch@test.com", "pass", "판매자C");
        ReflectionTestUtils.setField(seller, "role", User.Role.SELLER);
        em.persist(seller);

        Store storeA = new Store(seller, "매장A", "111-22-33331", "031-111-2221", "시흥시", null, null);
        Store storeB = new Store(seller, "매장B", "111-22-33332", "031-111-2222", "시흥시", null, null);
        em.persist(storeA);
        em.persist(storeB);

        Product product = new Product(storeB, "바나나", "과일", 5000, "1송이", "설명", null);
        em.persist(product);

        LocalDateTime now = LocalDateTime.now();
        GroupPurchase gp = new GroupPurchase(
                storeB, product, "바나나 공구",
                4000, 10, 50, 5,
                now.minusDays(1), now.plusDays(3),
                now.plusDays(4), now.plusDays(5),
                "카운터에서 수령", "상세안내", GroupPurchase.Status.OPEN
        );
        em.persist(gp);

        User buyer = new User("buyer_m@test.com", "pass", "구매자M");
        em.persist(buyer);

        Order order = new Order(buyer, gp, 1, 4000, 4000, now.plusMinutes(15));
        ReflectionTestUtils.setField(order, "status", Order.Status.PAID);
        em.persist(order);
        em.flush();

        Pickup pickupB = pickupService.createOrGetPickupForOrder(order);

        // 매장A URL로 매장B의 pickup 처리 요청 시도
        mvc.perform(post("/seller/stores/" + storeA.getId() + "/pickups/" + pickupB.getId() + "/ready")
                        .with(asUser(seller))
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(flash().attribute("errorMessage", containsString("해당 매장의 수령 건이 아닙니다")));
    }
}
