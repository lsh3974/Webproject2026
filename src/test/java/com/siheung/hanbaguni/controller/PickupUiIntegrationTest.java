package com.siheung.hanbaguni.controller;

import com.siheung.hanbaguni.entity.*;
import com.siheung.hanbaguni.security.LoginUser;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:pickup_ui;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa", "spring.datasource.password=",
        "spring.datasource.driver-class-name=org.h2.Driver", "spring.jpa.hibernate.ddl-auto=create-drop"})
@AutoConfigureMockMvc
@Transactional
class PickupUiIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired EntityManager em;

    record Fixture(User seller, User buyer, Store store, List<Order> orders, List<Pickup> pickups) {}

    Fixture fixture() {
        User seller = new User("pickup-ui-seller@example.test", "unused", "새봄마켓");
        ReflectionTestUtils.setField(seller, "role", User.Role.SELLER);
        User buyer = new User("pickup-ui-buyer@example.test", "unused", "동네이웃");
        em.persist(seller); em.persist(buyer);
        Store store = new Store(seller, "새봄동네마켓", null, "031-123-4567", "경기도 시흥시 정왕동", "마을상가 1층", "이웃과 함께하는 장보기");
        em.persist(store);
        Product product = new Product(store, "아삭한 제철 사과", "과일", 10900, "1팩", "좋은 먹거리를 이웃과 함께 골라 보세요.", "/images/ui/sample-0.svg");
        em.persist(product);
        LocalDateTime now = LocalDateTime.now();
        GroupPurchase group = new GroupPurchase(store, product, "함께 고른 제철 사과", 8900, 5, 60, null,
                now.minusDays(2), now.minusMinutes(1), now.minusMinutes(1), now.plusDays(3),
                "매장 운영 시간에 방문하여 수령증을 보여 주세요.", product.getDescription(), GroupPurchase.Status.SUCCESS);
        ReflectionTestUtils.setField(group, "currentQuantity", 12);
        em.persist(group);
        List<Order> orders = new ArrayList<>(); List<Pickup> pickups = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            Order order = new Order(buyer, group, i + 1, 8900, 8900 * (i + 1), now.plusMinutes(14));
            order.beginPayment(now); order.markPaid("pickup-ui-payment-" + i);
            em.persist(order);
            Pickup p = new Pickup(order, store, group, buyer, "PK-DEMO-20261108-000" + i, i + 1, "동네이웃", "010-1234-5678");
            if (i == 1 || i == 2) p.markReady();
            if (i == 2) p.markPickedUp();
            if (i == 3) { p.cancel(); ReflectionTestUtils.setField(order, "status", Order.Status.REFUNDED); }
            em.persist(p); orders.add(order); pickups.add(p);
        }
        em.flush();
        return new Fixture(seller, buyer, store, orders, pickups);
    }

    @Test void pickupAndPaymentScreensRenderCurrentContractsAndExportSamples() throws Exception {
        Fixture f = fixture(); String base = "/seller/stores/" + f.store().getId() + "/pickups";
        LocalDateTime now = LocalDateTime.now();
        GroupPurchase open = new GroupPurchase(f.store(), f.orders().get(0).getGroupPurchase().getProduct(), "예약 가능한 제철 사과", 8900, 10, 60, null,
                now.minusDays(1), now.plusDays(1), now.plusDays(2), now.plusDays(4), null, null, GroupPurchase.Status.OPEN);
        em.persist(open);
        Order waiting = new Order(f.buyer(), open, 2, 8900, 17800, now.plusMinutes(14));
        Order approving = new Order(f.buyer(), open, 2, 8900, 17800, now.plusMinutes(14));
        approving.beginPayment(now);
        Order refunding = new Order(f.buyer(), open, 1, 8900, 8900, now.plusMinutes(14));
        ReflectionTestUtils.setField(refunding, "status", Order.Status.REFUND_PENDING);
        em.persist(waiting); em.persist(approving); em.persist(refunding); em.flush();
        String export = System.getProperty("pickup.previewDir");
        Path out = export == null ? null : Files.createDirectories(Path.of(export));
        StringBuilder routes = new StringBuilder();
        Map<String,String> pages = new LinkedHashMap<>();
        pages.put("seller-pickups.html", base);
        pages.put("seller-pickup-search.html", base + "?searchCode=" + f.pickups().get(1).getPickupCode());
        pages.put("seller-pickup-not-found.html", base + "?searchCode=PK-UNKNOWN");
        pages.put("pickup-preparing.html", "/orders/" + f.orders().get(0).getId());
        pages.put("pickup-ready.html", "/orders/" + f.orders().get(1).getId());
        pages.put("pickup-complete.html", "/orders/" + f.orders().get(2).getId());
        pages.put("pickup-cancelled.html", "/orders/" + f.orders().get(3).getId());
        pages.put("order-pickup-list.html", "/orders");
        pages.put("order-payment-pending.html", "/orders/" + approving.getId());
        pages.put("order-refund-pending.html", "/orders/" + refunding.getId());
        pages.put("payment-checkout.html", "/orders/" + waiting.getId() + "/payment");
        pages.put("payment-failed.html", "/orders/" + waiting.getId() + "/payment/fail");
        pages.put("payment-pending.html", "/orders/" + approving.getId() + "/payment");
        pages.put("payment-result.html", "/orders/" + f.orders().get(1).getId() + "/payment/success");
        for (var page : pages.entrySet()) {
            User principal = page.getKey().startsWith("seller-") ? f.seller() : f.buyer();
            var result = mvc.perform(get(page.getValue()).with(user(new LoginUser(principal))))
                    .andExpect(status().isOk()).andExpect(content().string(containsString("/css/market.css"))).andReturn();
            String html = result.getResponse().getContentAsString(StandardCharsets.UTF_8);
            assertThat(html).doesNotContain("<<<<<<<", ">>>>>>>", "api.qrserver.com", "https://cdn.jsdelivr.net/npm/bootstrap");
            if (page.getKey().equals("seller-pickups.html")) {
                assertThat(html).contains("data-pickup-filter", "name=\"_csrf\"", "/js/vendor/html5-qrcode.min.js", "scanResultModal", "scanHiddenCode");
            }
            if (page.getKey().equals("pickup-ready.html")) {
                assertThat(html).contains("id=\"orderPickupQr\"", "id=\"copyPickupCode\"", f.pickups().get(1).getPickupCode());
            }
            if (page.getKey().startsWith("payment-")) {
                assertThat(html).contains("id=\"payment-page\"", "id=\"pay-button\"", "id=\"retry-button\"", "name=\"_csrf\"", "/js/payment.js");
            }
            if (out != null) { Files.writeString(out.resolve(page.getKey()), html); routes.append(page.getValue()).append('\t').append(page.getKey()).append('\n'); }
        }
        Store empty = new Store(f.seller(), "새로 연 동네 매장", null, "031-000-0000", "시흥시", null, null);
        em.persist(empty); em.flush();
        String emptyPath = "/seller/stores/" + empty.getId() + "/pickups";
        var emptyResult = mvc.perform(get(emptyPath).with(user(new LoginUser(f.seller())))).andExpect(status().isOk()).andReturn();
        if (out != null) {
            Files.writeString(out.resolve("seller-pickups-empty.html"), emptyResult.getResponse().getContentAsString(StandardCharsets.UTF_8));
            routes.append(emptyPath).append('\t').append("seller-pickups-empty.html\n");
            Files.writeString(out.resolve("routes.tsv"), routes);
            StringBuilder data = new StringBuilder("[");
            for (Pickup p : f.pickups()) {
                if (data.length() > 1) data.append(',');
                data.append(mvc.perform(get(base + "/api/lookup").param("code", p.getPickupCode()).with(user(new LoginUser(f.seller()))))
                        .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8));
            }
            Files.writeString(out.resolve("pickup-data.json"), data.append(']').toString());
        }
    }

    @Test void receiptQrIsOnlyExposedWhenTheServerAllowsPickup() throws Exception {
        Fixture f = fixture();
        for (int i = 0; i < f.orders().size(); i++) {
            var result = mvc.perform(get("/orders/" + f.orders().get(i).getId()).with(user(new LoginUser(f.buyer()))))
                    .andExpect(status().isOk()).andReturn();
            String html = result.getResponse().getContentAsString(StandardCharsets.UTF_8);
            assertThat(html.contains("id=\"orderPickupQr\"")).isEqualTo(i == 1);
            if (i != 1) assertThat(html).doesNotContain(f.pickups().get(i).getPickupCode());
            if (i == 2) assertThat(html).contains("상품 수령이 완료됐어요.").doesNotContain("name=\"reason\"");
        }
    }

    @Test void styledReadyAndScanFormsRetainCsrfOwnershipAndStateTransitions() throws Exception {
        Fixture f = fixture(); Pickup p = f.pickups().get(0);
        String base = "/seller/stores/" + f.store().getId() + "/pickups";
        mvc.perform(post(base + "/" + p.getId() + "/ready").with(user(new LoginUser(f.seller())))).andExpect(status().isForbidden());
        mvc.perform(get(base).with(user(new LoginUser(f.buyer())))).andExpect(status().isForbidden());
        mvc.perform(post(base + "/" + p.getId() + "/ready").with(user(new LoginUser(f.seller()))).with(csrf()))
                .andExpect(redirectedUrl(base));
        assertThat(p.getStatus()).isEqualTo(Pickup.Status.READY);
        mvc.perform(get(base + "/api/lookup").param("code", p.getPickupCode()).with(user(new LoginUser(f.seller()))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.canComplete").value(true))
                .andExpect(jsonPath("$.customerPhone").value("010-****-5678"));
        mvc.perform(post(base + "/scan-complete").param("pickupCode", p.getPickupCode()).with(user(new LoginUser(f.seller()))).with(csrf()))
                .andExpect(redirectedUrl(base));
        assertThat(p.getStatus()).isEqualTo(Pickup.Status.PICKED_UP);
        mvc.perform(get("/orders/" + f.orders().get(0).getId()).with(user(new LoginUser(f.buyer()))))
                .andExpect(status().isOk()).andExpect(content().string(not(containsString("id=\"orderPickupQr\""))));
    }
}
