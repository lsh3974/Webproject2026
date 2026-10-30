package com.siheung.hanbaguni.controller;

import com.siheung.hanbaguni.entity.GroupPurchase;
import com.siheung.hanbaguni.entity.Order;
import com.siheung.hanbaguni.entity.Product;
import com.siheung.hanbaguni.entity.Store;
import com.siheung.hanbaguni.entity.User;
import com.siheung.hanbaguni.security.LoginUser;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

import static org.hamcrest.Matchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:orders_test;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa", "spring.datasource.password=",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.jpa.hibernate.ddl-auto=create-drop"})
@AutoConfigureMockMvc
@Transactional
class OrderIntegrationTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private EntityManager em;

    private User createSeller(String email, String storeName) {
        User seller = new User(email, "unused", "판매자");
        em.persist(seller);
        Store store = new Store(seller, storeName, null, "031-123-4567", "시흥시 정왕동", null, null);
        em.persist(store);
        return seller;
    }

    private GroupPurchase createOpenGroupPurchase(User seller, String title, int price, int maxQty) {
        Store store = em.createQuery("SELECT s FROM Store s WHERE s.seller = :seller", Store.class)
                .setParameter("seller", seller)
                .getSingleResult();
        Product product = new Product(store, title, "식품", price + 2000, "개", "설명", null);
        em.persist(product);

        LocalDateTime now = LocalDateTime.now();
        GroupPurchase gp = new GroupPurchase(
                store, product, title, price, 10, maxQty, 5,
                now.minusDays(1), now.plusDays(2), now.plusDays(3), now.plusDays(4),
                "카운터 수령", "상세 설명", GroupPurchase.Status.OPEN
        );
        em.persist(gp);
        em.flush();
        return gp;
    }

    private User createBuyer(String email) {
        User buyer = new User(email, "unused", "구매자");
        em.persist(buyer);
        em.flush();
        return buyer;
    }

    private RequestPostProcessor asUser(User user) {
        return user(new LoginUser(user));
    }

    @Test
    @DisplayName("GET /orders/new - 공동구매 미지정 시 select 뷰 정상 렌더링")
    void testShowSelectForm() throws Exception {
        User buyer = createBuyer("buyer1@test.com");
        User seller = createSeller("seller1@test.com", "정왕마트");
        createOpenGroupPurchase(seller, "딸기 1팩", 5000, 20);

        mvc.perform(get("/orders/new").with(asUser(buyer)))
                .andExpect(status().isOk())
                .andExpect(view().name("order/select"))
                .andExpect(content().string(containsString("딸기 1팩")));
    }

    @Test
    @DisplayName("GET /orders/new?groupPurchaseId={id} - new.html 뷰 템플릿 오류 없이 정상 렌더링")
    void testShowReservationForm() throws Exception {
        User buyer = createBuyer("buyer2@test.com");
        User seller = createSeller("seller2@test.com", "정왕청과");
        GroupPurchase gp = createOpenGroupPurchase(seller, "샤인머스캣", 15000, 30);

        mvc.perform(get("/orders/new")
                        .param("groupPurchaseId", gp.getId().toString())
                        .with(asUser(buyer)))
                .andExpect(status().isOk())
                .andExpect(view().name("order/new"))
                .andExpect(content().string(containsString("샤인머스캣")))
                .andExpect(content().string(containsString("15,000")));
    }

    @Test
    @DisplayName("POST /orders/new - HTML 폼을 통한 주문 수량 예약 성공 후 상세 페이지로 리다이렉트")
    void testCreateReservationFormSuccess() throws Exception {
        User buyer = createBuyer("buyer3@test.com");
        User seller = createSeller("seller3@test.com", "시흥상회");
        GroupPurchase gp = createOpenGroupPurchase(seller, "제주감귤", 8000, 50);

        mvc.perform(post("/orders/new")
                        .with(asUser(buyer))
                        .with(csrf())
                        .param("groupPurchaseId", gp.getId().toString())
                        .param("quantity", "2"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("/orders/*"))
                .andExpect(flash().attributeExists("successMessage"));
    }

    @Test
    @DisplayName("GET /orders 및 GET /orders/{id} - 주문 목록 및 상세 뷰 정상 렌더링")
    void testViewOrderListAndDetail() throws Exception {
        User buyer = createBuyer("buyer4@test.com");
        User seller = createSeller("seller4@test.com", "오이도수산");
        GroupPurchase gp = createOpenGroupPurchase(seller, "꽃게 1kg", 20000, 40);

        Order order = new Order(buyer, gp, 2, 20000, 40000, LocalDateTime.now().plusMinutes(15));
        em.persist(order);
        em.flush();

        // 목록 조회
        mvc.perform(get("/orders").with(asUser(buyer)))
                .andExpect(status().isOk())
                .andExpect(view().name("order/list"))
                .andExpect(content().string(containsString("꽃게 1kg")));

        // 상세 조회
        mvc.perform(get("/orders/" + order.getId()).with(asUser(buyer)))
                .andExpect(status().isOk())
                .andExpect(view().name("order/detail"))
                .andExpect(content().string(containsString("꽃게 1kg")))
                .andExpect(content().string(containsString("40,000")));
    }

    @Test
    @DisplayName("POST /orders/{id}/cancel - 수량 예약 취소 성공 및 리다이렉트")
    void testCancelReservationHtml() throws Exception {
        User buyer = createBuyer("buyer5@test.com");
        User seller = createSeller("seller5@test.com", "대야유통");
        GroupPurchase gp = createOpenGroupPurchase(seller, "한우불고기", 25000, 20);

        Order order = new Order(buyer, gp, 1, 25000, 25000, LocalDateTime.now().plusMinutes(15));
        em.persist(order);
        em.flush();

        mvc.perform(post("/orders/" + order.getId() + "/cancel")
                        .with(asUser(buyer))
                        .with(csrf())
                        .param("cancelReason", "단순 변심"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/orders/" + order.getId()))
                .andExpect(flash().attributeExists("successMessage"));
    }

    @Test
    @DisplayName("API POST /api/v1/orders - REST API 수량 예약 생성 및 createdAt 포함 확인")
    void testApiReserveOrder() throws Exception {
        User buyer = createBuyer("buyer6@test.com");
        User seller = createSeller("seller6@test.com", "은행상점");
        GroupPurchase gp = createOpenGroupPurchase(seller, "배추 1망", 9000, 30);

        String jsonBody = String.format("{\"groupPurchaseId\": %d, \"quantity\": 3}", gp.getId());

        mvc.perform(post("/api/v1/orders")
                        .with(asUser(buyer))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonBody))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.orderId").isNumber())
                .andExpect(jsonPath("$.groupPurchaseTitle").value("배추 1망"))
                .andExpect(jsonPath("$.quantity").value(3))
                .andExpect(jsonPath("$.totalPrice").value(27000))
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.createdAt").isNotEmpty());
    }

    @Test
    @DisplayName("API POST /api/v1/orders/{id}/cancel-reservation - REST API 취소 및 글자수 255자 초과 검증")
    void testApiCancelReservation() throws Exception {
        User buyer = createBuyer("buyer7@test.com");
        User seller = createSeller("seller7@test.com", "능곡청과");
        GroupPurchase gp = createOpenGroupPurchase(seller, "수박 1통", 18000, 10);

        Order order = new Order(buyer, gp, 1, 18000, 18000, LocalDateTime.now().plusMinutes(15));
        em.persist(order);
        em.flush();

        // 255자 초과 요청 -> 400 Bad Request
        String tooLongReason = "가".repeat(256);
        String invalidBody = String.format("{\"cancelReason\": \"%s\"}", tooLongReason);

        mvc.perform(post("/api/v1/orders/" + order.getId() + "/cancel-reservation")
                        .with(asUser(buyer))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidBody))
                .andExpect(status().isBadRequest());

        // 정상 취소
        String validBody = "{\"cancelReason\": \"개인 사정으로 취소합니다.\"}";
        mvc.perform(post("/api/v1/orders/" + order.getId() + "/cancel-reservation")
                        .with(asUser(buyer))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"))
                .andExpect(jsonPath("$.cancelReason").value("개인 사정으로 취소합니다."));
    }
}
