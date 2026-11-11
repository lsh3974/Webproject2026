package com.siheung.hanbaguni.controller;

import com.siheung.hanbaguni.entity.GroupPurchase;
import com.siheung.hanbaguni.entity.Product;
import com.siheung.hanbaguni.entity.Order;
import com.siheung.hanbaguni.entity.Store;
import com.siheung.hanbaguni.entity.User;
import com.siheung.hanbaguni.notification.Notification;
import com.siheung.hanbaguni.notification.NotificationEvent;
import com.siheung.hanbaguni.notification.NotificationType;
import com.siheung.hanbaguni.security.LoginUser;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Verifies real Thymeleaf bindings. Optional export uses isolated, illustrative H2 data only. */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:market-ui;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa", "spring.datasource.password=",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.jpa.hibernate.ddl-auto=create-drop"})
@AutoConfigureMockMvc
@Transactional
class MarketUiIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired EntityManager em;

    private User seller() {
        User user = new User("preview-seller@example.test", "unused-hash", "새봄마켓");
        ReflectionTestUtils.setField(user, "role", User.Role.SELLER);
        em.persist(user);
        return user;
    }

    @Test void notificationPagesUseTheMarketHeaderAndPreserveReadAndPreferenceForms() throws Exception {
        User buyer = new User("notification-buyer@example.test", "unused-hash", "동네이웃");
        em.persist(buyer);
        User seller = seller();
        NotificationEvent event = new NotificationEvent("market-ui-notification", NotificationType.PICKUP_READY,
                buyer, null, null, "상품 수령이 가능합니다", "동네 매장에서 상품을 수령해 주세요.", "/orders");
        em.persist(event);
        Notification notification = new Notification(event);
        em.persist(notification);

        var buyerSession = user(new LoginUser(buyer));
        var sellerSession = user(new LoginUser(seller));
        mvc.perform(get("/")).andExpect(status().isOk())
                .andExpect(content().string(not(containsString("notification-bell-button"))));
        mvc.perform(get("/notifications")).andExpect(status().is3xxRedirection());

        MvcResult buyerHome = mvc.perform(get("/").with(buyerSession)).andExpect(status().isOk())
                .andExpect(content().string(containsString("aria-label=\"주 메뉴\""))).andReturn();
        MvcResult sellerHome = mvc.perform(get("/seller/stores").with(sellerSession)).andExpect(status().isOk())
                .andExpect(content().string(containsString("aria-label=\"판매자 메뉴\""))).andReturn();
        MvcResult inbox = mvc.perform(get("/notifications").with(buyerSession)).andExpect(status().isOk())
                .andExpect(content().string(containsString(event.getTitle())))
                .andExpect(content().string(containsString("action=\"/notifications/" + notification.getId() + "/read\"")))
                .andExpect(content().string(containsString("action=\"/notifications/read-all\""))).andReturn();
        MvcResult preferences = mvc.perform(get("/notifications/preferences").with(sellerSession))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("aria-label=\"판매자 메뉴\"")))
                .andExpect(content().string(containsString("id=\"enablePushButton\""))).andReturn();
        for (var page : Map.of("notification-buyer.html", buyerHome, "notification-seller.html", sellerHome,
                "notification-inbox.html", inbox, "notification-preferences.html", preferences).entrySet()) {
            String html = page.getValue().getResponse().getContentAsString(StandardCharsets.UTF_8);
            assertThat(html).contains("/css/market.css", "/css/notifications.css", "notification-csrf-token");
            assertThat(StringUtils.countOccurrencesOf(html, "id=\"notificationPopover\"")).isEqualTo(1);
            assertThat(StringUtils.countOccurrencesOf(html, "/js/notifications.js")).isEqualTo(1);
            String export = System.getProperty("market.previewDir");
            if (export != null) {
                Path output = Files.createDirectories(Path.of(export));
                Files.writeString(output.resolve(page.getKey()), html);
            }
        }
        mvc.perform(post("/notifications/{id}/read", notification.getId()).with(buyerSession).with(csrf()))
                .andExpect(redirectedUrl("/orders"));
        mvc.perform(get("/notifications/api/unread-count").with(buyerSession))
                .andExpect(jsonPath("$.count").value(0));
        mvc.perform(post("/notifications/preferences").param("pushEnabled", "true").with(sellerSession).with(csrf()))
                .andExpect(redirectedUrl("/notifications/preferences"));
        mvc.perform(get("/notifications/preferences").with(sellerSession))
                .andExpect(model().attribute("form", org.hamcrest.Matchers.hasProperty("pushEnabled", org.hamcrest.Matchers.is(true))));
    }

    @Test void buyerAndSellerPagesRenderExistingFormsAndExportReviewScreens() throws Exception {
        User seller = seller();
        Store store = new Store(seller, "새봄동네마켓", "123-45-67890", "031-123-4567",
                "경기도 시흥시 정왕동", "마을상가 1층", "이웃과 함께하는 신선한 장보기");
        em.persist(store);
        String[] names = {"아삭한 제철 사과", "달큰한 햇양파 한 망", "흙내음 가득한 햇당근", "완숙 토마토", "포슬포슬 수미감자", "신선한 초록 브로콜리"};
        String[] category = {"과일", "채소", "채소", "과일", "채소", "채소"};
        int[] prices = {8900, 4900, 3900, 5900, 6900, 3500};
        Product firstProduct = null;
        GroupPurchase firstPurchase = null;
        LocalDateTime now = LocalDateTime.now();
        for (int i = 0; i < names.length; i++) {
            Product p = new Product(store, names[i], category[i], prices[i] + 2000, "1팩",
                    "좋은 먹거리를 필요한 만큼, 가까운 동네 매장에서 만나보세요.", "/images/ui/sample-" + i + ".svg");
            em.persist(p);
            GroupPurchase gp = new GroupPurchase(store, p, names[i], prices[i], 30, 60,
                    6, now.minusDays(1), now.plusDays(2 + i), now.plusDays(4 + i), now.plusDays(6 + i),
                    "매장 운영 시간 안에 방문해 주세요. 수령 시 주문 정보를 확인합니다.", p.getDescription(), GroupPurchase.Status.OPEN);
            ReflectionTestUtils.setField(gp, "currentQuantity", 12 + i * 3);
            em.persist(gp);
            if (i == 0) { firstProduct = p; firstPurchase = gp; }
        }
        User buyer = new User("preview-buyer@example.test", "unused-hash", "동네이웃");
        User emptyBuyer = new User("empty-buyer@example.test", "unused-hash", "새로운이웃");
        em.persist(buyer);
        em.persist(emptyBuyer);
        Order pending = new Order(buyer, firstPurchase, 2, prices[0], prices[0] * 2, now.plusMinutes(14));
        Order cancelled = new Order(buyer, firstPurchase, 1, prices[0], prices[0], now.plusMinutes(14));
        cancelled.cancelReservation("수량을 다시 선택하려고 해요.");
        Order expired = new Order(buyer, firstPurchase, 1, prices[0], prices[0], now.minusMinutes(1));
        expired.expireReservation();
        Order paid = new Order(buyer, firstPurchase, 1, prices[0], prices[0], now.plusMinutes(14));
        paid.beginPayment(now);
        paid.markPaid("market-ui-test-paid");
        Order refunded = new Order(buyer, firstPurchase, 1, prices[0], prices[0], now.plusMinutes(14));
        ReflectionTestUtils.setField(refunded, "status", Order.Status.REFUNDED);
        for (Order order : new Order[]{pending, cancelled, expired, paid, refunded}) em.persist(order);
        em.flush();
        String base = "/seller/stores/" + store.getId();
        Map<String, String> pages = new LinkedHashMap<>();
        pages.put("home.html", "/");
        pages.put("buyer-list.html", "/group-purchases");
        pages.put("buyer-detail.html", "/group-purchases/" + firstPurchase.getId());
        pages.put("login.html", "/login");
        pages.put("signup.html", "/signup");
        pages.put("forbidden.html", "/error");
        pages.put("seller-stores.html", "/seller/stores");
        pages.put("seller-store-new.html", "/seller/stores/new");
        pages.put("seller-store-edit.html", base + "/edit");
        pages.put("seller-products.html", base + "/products");
        pages.put("seller-product-new.html", base + "/products/new");
        pages.put("seller-product-edit.html", base + "/products/" + firstProduct.getId() + "/edit");
        pages.put("seller-campaigns.html", base + "/group-purchases");
        pages.put("seller-campaign-new.html", base + "/group-purchases/new");
        pages.put("seller-campaign-edit.html", base + "/group-purchases/" + firstPurchase.getId() + "/edit");
        pages.put("order-select.html", "/orders/new");
        pages.put("order-new.html", "/orders/new?groupPurchaseId=" + firstPurchase.getId());
        pages.put("order-list.html", "/orders");
        pages.put("order-detail.html", "/orders/" + pending.getId());
        pages.put("order-cancelled.html", "/orders/" + cancelled.getId());
        pages.put("order-expired.html", "/orders/" + expired.getId());
        pages.put("order-paid.html", "/orders/" + paid.getId());
        pages.put("order-refunded.html", "/orders/" + refunded.getId());
        pages.put("order-empty.html", "/orders");
        pages.put("order-unavailable.html", "/orders/new?groupPurchaseId=" + firstPurchase.getId());
        pages.put("buyer-empty.html", "/group-purchases?q=없는상품검증");
        String export = System.getProperty("market.previewDir");
        Path output = export == null ? null : Files.createDirectories(Path.of(export));
        StringBuilder routes = new StringBuilder();
        for (var page : pages.entrySet()) {
            var request = get(page.getValue()).accept(org.springframework.http.MediaType.TEXT_HTML);
            if (page.getKey().equals("seller-campaign-new.html")) request.param("productId", firstProduct.getId().toString());
            if (page.getKey().startsWith("seller-")) request.with(user(new LoginUser(seller)));
            if (page.getKey().startsWith("order-")) request.with(user(new LoginUser(buyer)));
            if (page.getKey().equals("order-empty.html")) request.with(user(new LoginUser(emptyBuyer)));
            if (page.getKey().equals("order-unavailable.html")) request.with(user(new LoginUser(seller)));
            boolean forbidden = page.getKey().equals("forbidden.html");
            if (forbidden) request.with(user(new LoginUser(seller)))
                    .requestAttr(jakarta.servlet.RequestDispatcher.ERROR_STATUS_CODE, 403);
            MvcResult result = mvc.perform(request).andExpect(forbidden ? status().isForbidden() : status().isOk())
                    .andExpect(content().string(containsString("/css/market.css")))
                    .andExpect(content().string(not(containsString("data-theme-choice"))))
                    .andExpect(content().string(not(containsString("unused-hash")))).andReturn();
            String html = result.getResponse().getContentAsString(StandardCharsets.UTF_8);
            if (page.getKey().startsWith("seller-")) {
                org.junit.jupiter.api.Assertions.assertTrue(html.contains("name=\"_csrf\""));
            }
            if (page.getKey().equals("order-new.html")) {
                org.junit.jupiter.api.Assertions.assertTrue(html.contains("action=\"/orders/new\""));
                org.junit.jupiter.api.Assertions.assertTrue(html.contains("name=\"quantity\""));
                org.junit.jupiter.api.Assertions.assertTrue(html.contains("name=\"_csrf\""));
                org.junit.jupiter.api.Assertions.assertTrue(html.contains("max=\"3\""));
            }
            if (page.getKey().equals("order-unavailable.html")) {
                org.junit.jupiter.api.Assertions.assertTrue(html.contains("현재 예약할 수 없습니다"));
            }
            if (page.getKey().equals("order-detail.html")) {
                org.junit.jupiter.api.Assertions.assertTrue(html.contains("action=\"/orders/" + pending.getId() + "/cancel\""));
            }
            if (page.getKey().equals("order-cancelled.html") || page.getKey().equals("order-expired.html")) {
                org.junit.jupiter.api.Assertions.assertFalse(html.contains("수량 예약 취소</button>"));
                org.junit.jupiter.api.Assertions.assertFalse(html.contains("id=\"countdownTimer\""));
            }
            if (output != null) {
                Files.writeString(output.resolve(page.getKey()), html);
                routes.append(page.getValue()).append('\t').append(page.getKey()).append('\n');
            }
        }
        if (output != null) Files.writeString(output.resolve("routes.tsv"), routes);
        mvc.perform(get(base + "/products").with(user(new LoginUser(seller))))
                .andExpect(content().string(containsString("action=\"" + base + "/products/" + firstProduct.getId() + "/status\"")))
                .andExpect(content().string(containsString("name=\"status\"")));
        mvc.perform(get(base + "/products/new").with(user(new LoginUser(seller))))
                .andExpect(content().string(containsString("enctype=\"multipart/form-data\"")))
                .andExpect(content().string(containsString("name=\"imageFile\"")));
    }

    @Test void sellerFormValidationStillShowsErrorsAndDoesNotPersistInvalidProduct() throws Exception {
        User seller = seller();
        Store store = new Store(seller, "검증 매장", null, "031-123-4567", "시흥시", null, null);
        em.persist(store);
        em.flush();
        mvc.perform(post("/seller/stores/" + store.getId() + "/products/new")
                        .with(user(new LoginUser(seller))).with(csrf()).param("name", ""))
                .andExpect(status().isOk())
                .andExpect(model().attributeHasFieldErrors("productForm", "name"))
                .andExpect(content().string(containsString("is-invalid")));
        Long count = em.createQuery("select count(p) from Product p", Long.class).getSingleResult();
        org.junit.jupiter.api.Assertions.assertEquals(0L, count);
    }

    @Test void signedInHeadersAndPublicAssetsRenderWithoutWeakeningSellerAccess() throws Exception {
        User seller = seller();
        User buyer = new User("buyer@example.test", "unused-hash", "구매자테스트");
        em.persist(buyer);
        for (User account : new User[]{buyer, seller}) {
            for (String route : new String[]{"/", "/group-purchases"}) {
                mvc.perform(get(route).with(user(new LoginUser(account))))
                        .andExpect(status().isOk())
                        .andExpect(content().string(containsString(account.getDisplayName())))
                        .andExpect(content().string(containsString("action=\"/logout\"")));
            }
        }
        mvc.perform(get("/seller/stores")).andExpect(status().is3xxRedirection());
        mvc.perform(get("/seller/stores").with(user(new LoginUser(buyer))))
                .andExpect(status().isForbidden());
        for (String asset : new String[]{"/css/market.css", "/css/vendor/bootstrap.min.css",
                "/js/ui.js", "/js/vendor/bootstrap.bundle.min.js", "/images/ui/reference-0.webp"}) {
            mvc.perform(get(asset)).andExpect(status().isOk());
        }
    }

    @Test void emptyListsAndLoginFeedbackRender() throws Exception {
        User seller = seller();
        mvc.perform(get("/seller/stores").with(user(new LoginUser(seller))))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("등록된 매장이 없습니다.")));
        Store store = new Store(seller, "빈 매장", null, "031-123-4567", "시흥시", null, null);
        em.persist(store);
        em.flush();
        String base = "/seller/stores/" + store.getId();
        for (String suffix : new String[]{"/products", "/group-purchases", "/group-purchases/new"}) {
            mvc.perform(get(base + suffix).with(user(new LoginUser(seller))))
                    .andExpect(status().isOk()).andExpect(content().string(containsString("/css/market.css")));
        }
        mvc.perform(get("/group-purchases").param("q", "없는상품"))
                .andExpect(status().isOk()).andExpect(content().string(containsString("ui-empty")));
        mvc.perform(get("/login?error")).andExpect(status().isOk())
                .andExpect(content().string(containsString("로그인할 수 없습니다.")));
        mvc.perform(get("/login?logout")).andExpect(status().isOk())
                .andExpect(content().string(containsString("로그아웃되었습니다.")));
    }

    @Test void sellerFormsKeepCreateEditPublishAndStatusFlow() throws Exception {
        User seller = seller();
        LoginUser principal = new LoginUser(seller);
        mvc.perform(post("/seller/stores/new").with(user(principal)).with(csrf())
                        .param("name", "화면검증매장").param("phone", "031-123-4567").param("address", "시흥시"))
                .andExpect(status().is3xxRedirection());
        Store store = em.createQuery("select s from Store s where s.seller.id = :id", Store.class)
                .setParameter("id", seller.getId()).getSingleResult();
        String base = "/seller/stores/" + store.getId();
        mvc.perform(post(base + "/edit").with(user(principal)).with(csrf())
                        .param("name", "수정매장").param("phone", "031-123-4567")
                        .param("address", "시흥시 정왕동").param("status", "ACTIVE"))
                .andExpect(redirectedUrl("/seller/stores"));
        mvc.perform(post(base + "/products/new").with(user(principal)).with(csrf())
                        .param("name", "사진없는상품").param("category", "채소").param("regularPrice", "5001").param("unit", "1팩"))
                .andExpect(redirectedUrl(base + "/products"));
        Product product = em.createQuery("select p from Product p where p.store.id = :id", Product.class)
                .setParameter("id", store.getId()).getSingleResult();
        String productPath = base + "/products/" + product.getId();
        mvc.perform(post(productPath + "/edit").with(user(principal)).with(csrf())
                        .param("name", "수정상품").param("category", "채소").param("regularPrice", "6000")
                        .param("unit", "1팩").param("status", "ON_SALE"))
                .andExpect(redirectedUrl(base + "/products"));
        var now = LocalDateTime.now().withSecond(0).withNano(0);
        var values = new org.springframework.util.LinkedMultiValueMap<String, String>();
        Map.of("productId", product.getId().toString(), "title", "검증용공동구매", "groupPrice", "4001",
                "targetQuantity", "10", "maxQuantity", "20", "startAt", now.minusHours(1).toString(),
                "endAt", now.plusDays(2).toString(), "pickupStartAt", now.plusDays(3).toString(),
                "pickupEndAt", now.plusDays(4).toString(), "status", "DRAFT").forEach(values::add);
        mvc.perform(post(base + "/group-purchases/new").with(user(principal)).with(csrf()).params(values))
                .andExpect(redirectedUrl(base + "/group-purchases"));
        GroupPurchase gp = em.createQuery("select g from GroupPurchase g where g.store.id = :id", GroupPurchase.class)
                .setParameter("id", store.getId()).getSingleResult();
        String campaignPath = base + "/group-purchases/" + gp.getId();
        values.set("title", "수정공동구매");
        mvc.perform(post(campaignPath + "/edit").with(user(principal)).with(csrf()).params(values))
                .andExpect(redirectedUrl(base + "/group-purchases"));
        mvc.perform(post(campaignPath + "/publish").with(user(principal)).with(csrf()))
                .andExpect(redirectedUrl(base + "/group-purchases"));
        mvc.perform(get("/group-purchases/" + gp.getId())).andExpect(status().isOk())
                .andExpect(content().string(containsString("등록된 상품 사진 없음")))
                .andExpect(content().string(containsString("수정공동구매")));
        mvc.perform(post(productPath + "/status").with(user(principal)).with(csrf()).param("status", "SOLD_OUT"))
                .andExpect(redirectedUrl(base + "/products"));
        mvc.perform(get("/group-purchases/" + gp.getId())).andExpect(status().isNotFound());
        mvc.perform(post(campaignPath + "/status").with(user(principal)).with(csrf()).param("status", "CLOSED"))
                .andExpect(redirectedUrl(base + "/group-purchases"));
        // Existing main's delete query fails independently of these templates.
        // Reproduction and the unverified delete flow are recorded in docs/MARKET-UI.md.
    }

    @Test void invalidSellerFormsRenderErrorsWithoutLosingBindings() throws Exception {
        User seller = seller();
        Store store = new Store(seller, "검증 매장", null, "031-123-4567", "시흥시", null, null);
        em.persist(store);
        Product product = new Product(store, "상품", "채소", 5000, "1팩", null, null);
        em.persist(product);
        var now = LocalDateTime.now();
        GroupPurchase gp = new GroupPurchase(store, product, "모집", 4000, 10, 20, null,
                now, now.plusDays(1), now.plusDays(2), now.plusDays(3), null, null, GroupPurchase.Status.DRAFT);
        em.persist(gp);
        em.flush();
        String base = "/seller/stores/" + store.getId();
        Map<String, String> forms = Map.of("/seller/stores/new", "storeForm", base + "/edit", "storeForm",
                base + "/products/new", "productForm", base + "/products/" + product.getId() + "/edit", "productForm",
                base + "/group-purchases/new", "groupPurchaseForm", base + "/group-purchases/" + gp.getId() + "/edit", "groupPurchaseForm");
        for (var form : forms.entrySet()) {
            mvc.perform(post(form.getKey()).with(user(new LoginUser(seller))).with(csrf()))
                    .andExpect(status().isOk()).andExpect(model().attributeHasErrors(form.getValue()))
                    .andExpect(content().string(containsString("/css/market.css")))
                    .andExpect(content().string(containsString("name=\"_csrf\"")));
        }
        org.junit.jupiter.api.Assertions.assertEquals("검증 매장", store.getName());
        org.junit.jupiter.api.Assertions.assertEquals("상품", product.getName());
        org.junit.jupiter.api.Assertions.assertEquals("모집", gp.getTitle());
    }

    @Test void reservationValidationAndCancellationStillUseExistingActions() throws Exception {
        User seller = seller();
        User buyer = new User("reservation-buyer@example.test", "unused-hash", "예약고객");
        em.persist(buyer);
        Store store = new Store(seller, "예약 매장", null, "031-123-4567", "시흥시", null, null);
        em.persist(store);
        Product product = new Product(store, "햇감자", "채소", 5000, "1팩", null, null);
        em.persist(product);
        var now = LocalDateTime.now();
        GroupPurchase gp = new GroupPurchase(store, product, "햇감자 예약", 4000, 10, 20, 3,
                now.minusDays(1), now.plusDays(1), now.plusDays(2), now.plusDays(3), null, null, GroupPurchase.Status.OPEN);
        em.persist(gp);
        em.flush();
        mvc.perform(post("/orders/new").with(user(new LoginUser(buyer))).with(csrf())
                        .param("groupPurchaseId", gp.getId().toString()).param("quantity", "0"))
                .andExpect(status().isOk()).andExpect(model().attributeHasFieldErrors("orderForm", "quantity"))
                .andExpect(content().string(containsString("/css/market.css")))
                .andExpect(content().string(containsString("name=\"_csrf\"")));
        mvc.perform(post("/orders/new").with(user(new LoginUser(buyer))).with(csrf())
                        .param("groupPurchaseId", gp.getId().toString()).param("quantity", "2"))
                .andExpect(status().is3xxRedirection());
        Order order = em.createQuery("select o from Order o where o.user.id = :id", Order.class)
                .setParameter("id", buyer.getId()).getSingleResult();
        mvc.perform(get("/orders/" + order.getId()).with(user(new LoginUser(buyer))))
                .andExpect(status().isOk()).andExpect(content().string(containsString("8,000")))
                .andExpect(content().string(containsString("countdownTimer")));
        mvc.perform(post("/orders/" + order.getId() + "/cancel").with(user(new LoginUser(buyer))).with(csrf()))
                .andExpect(redirectedUrl("/orders/" + order.getId()));
        org.junit.jupiter.api.Assertions.assertEquals(Order.Status.CANCELLED, order.getStatus());
    }
}
