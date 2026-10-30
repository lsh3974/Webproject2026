package com.siheung.hanbaguni.controller;

import com.siheung.hanbaguni.entity.GroupPurchase;
import com.siheung.hanbaguni.entity.Product;
import com.siheung.hanbaguni.entity.Store;
import com.siheung.hanbaguni.entity.User;
import com.siheung.hanbaguni.security.LoginUser;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:catalog;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa", "spring.datasource.password=",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.jpa.hibernate.ddl-auto=create-drop"})
@AutoConfigureMockMvc
@Transactional
class GroupPurchaseBrowseIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired EntityManager em;

    private RequestPostProcessor member() {
        User member = new User("buyer@example.test", "unused-hash", "구매자");
        return user(new LoginUser(member));
    }

    private GroupPurchase purchase(String title, String shop, String address, String category,
                                   GroupPurchase.Status status, LocalDateTime start, LocalDateTime end) {
        User seller = new User(title + shop + "@example.test", "unused-hash", title + shop);
        em.persist(seller);
        Store store = new Store(seller, shop, null, "031-123-4567", address, null, null);
        em.persist(store);
        Product product = new Product(store, title, category, 5000, "팩", "신선한 상품", null);
        em.persist(product);
        GroupPurchase gp = new GroupPurchase(store, product, title, 3000, 10, 20,
                null, start, end, end.plusDays(1), end.plusDays(2), "매장 수령", "상품 설명", status);
        em.persist(gp);
        em.flush();
        return gp;
    }

    private GroupPurchase open(String title) {
        return purchase(title, "동네마트", "시흥시 정왕동", "채소", GroupPurchase.Status.OPEN,
                LocalDateTime.now().minusDays(1), LocalDateTime.now().plusDays(1));
    }

    @Test void anonymousCanRenderListAndDetailWithoutImage() throws Exception {
        var gp = open("비회원 공개 상품");
        mvc.perform(get("/group-purchases")).andExpect(status().isOk())
                .andExpect(content().string(containsString("비회원 공개 상품")))
                .andExpect(content().string(containsString("href=\"/login\"")))
                .andExpect(content().string(not(containsString("action=\"/logout\""))));
        mvc.perform(get("/group-purchases/" + gp.getId())).andExpect(status().isOk())
                .andExpect(view().name("group-purchases/detail"))
                .andExpect(content().string(containsString("비회원 공개 상품")))
                .andExpect(content().string(containsString("3,000")))
                .andExpect(content().string(containsString("상품 설명")))
                .andExpect(content().string(not(containsString("<img "))))
                .andExpect(content().string(containsString("등록된 상품 사진 없음")));
    }

    @Test void anonymousDetailRendersProductImageFromDatabase() throws Exception {
        var gp = open("이미지 있는 상품");
        var p = gp.getProduct();
        p.update(p.getName(), p.getCategory(), p.getRegularPrice(), p.getUnit(),
                p.getDescription(), "/uploads/products/test-onion.jpg", p.getStatus());
        em.flush();
        em.clear();
        mvc.perform(get("/group-purchases/" + gp.getId())).andExpect(status().isOk())
                .andExpect(content().string(containsString("src=\"/uploads/products/test-onion.jpg\"")))
                .andExpect(content().string(containsString("alt=\"이미지 있는 상품\"")))
                .andExpect(content().string(not(containsString("등록된 상품 사진 없음"))));
    }

    @Test void sellerRoutesStillRequireSellerRole() throws Exception {
        mvc.perform(get("/seller/stores")).andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));
        mvc.perform(get("/seller/stores").with(member())).andExpect(status().isForbidden());
    }

    @Test void publicReadDoesNotOpenWritesOrOrderRoutes() throws Exception {
        for (String path : new String[]{"/group-purchases", "/group-purchases/1", "/group-purchases/1/orders"}) {
            mvc.perform(post(path).with(csrf())).andExpect(status().is3xxRedirection())
                    .andExpect(redirectedUrl("/login"));
        }
        mvc.perform(get("/group-purchases/1/orders")).andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));
        mvc.perform(post("/group-purchases/1/orders")).andExpect(status().isForbidden());
    }

    @Test void rendersActualListAndDetailWithoutExposingSellerAccount() throws Exception {
        var gp = open("햇감자 공동구매");
        open("달큰한 햇양파 함께 담아요");
        open("아삭한 당근 한 팩");
        open("우리 동네 신선한 쌈채소");
        var listResult = mvc.perform(get("/group-purchases").with(member())).andExpect(status().isOk())
                .andExpect(content().string(containsString("햇감자 공동구매")))
                .andExpect(content().string(containsString("3,000")))
                .andExpect(content().string(not(containsString("unused-hash"))))
                .andExpect(content().string(containsString("name=\"_csrf\""))).andReturn();
        var detailResult = mvc.perform(get("/group-purchases/" + gp.getId()).with(member())).andExpect(status().isOk())
                .andExpect(content().string(containsString("시흥시 정왕동")))
                .andExpect(content().string(containsString("주문 신청은 아직 지원하지 않습니다"))).andReturn();
        String previewDirectory = System.getProperty("catalog.previewDir");
        if (previewDirectory != null) {
            Path directory = Files.createDirectories(Path.of(previewDirectory));
            Files.writeString(directory.resolve("index.html"),
                    listResult.getResponse().getContentAsString(StandardCharsets.UTF_8));
            Files.writeString(directory.resolve("detail.html"),
                    detailResult.getResponse().getContentAsString(StandardCharsets.UTF_8));
        }
    }

    @Test void excludesDraftScheduledExpiredAndUnavailableProductsFromListAndDetail() throws Exception {
        var now = LocalDateTime.now();
        var draft = purchase("비공개초안", "초안마트", "시흥시", "채소", GroupPurchase.Status.DRAFT, now.minusDays(1), now.plusDays(1));
        var future = purchase("미래모집", "예약마트", "시흥시", "채소", GroupPurchase.Status.OPEN, now.plusDays(1), now.plusDays(2));
        var expired = purchase("지난모집", "마감마트", "시흥시", "채소", GroupPurchase.Status.OPEN, now.minusDays(2), now.minusDays(1));
        var hidden = open("숨김상품");
        hidden.getProduct().updateStatus(Product.Status.HIDDEN);
        var soldOut = open("품절상품");
        soldOut.getProduct().updateStatus(Product.Status.SOLD_OUT);
        var closed = purchase("종료모집", "종료마트", "시흥시", "채소", GroupPurchase.Status.CLOSED,
                now.minusDays(1), now.plusDays(1));
        em.flush();
        mvc.perform(get("/group-purchases")).andExpect(status().isOk())
                .andExpect(content().string(containsString("조건에 맞는 공동구매가 없어요")));
        for (var gp : new GroupPurchase[]{draft, future, expired, hidden, soldOut, closed}) {
            mvc.perform(get("/group-purchases/" + gp.getId())).andExpect(status().isNotFound());
        }
        mvc.perform(get("/group-purchases/999999").with(member())).andExpect(status().isNotFound());
    }

    @Test void excludesInactiveStores() throws Exception {
        var gp = open("휴점상품");
        var s = gp.getStore();
        s.update(s.getName(), null, s.getPhone(), s.getAddress(), null, null, Store.Status.SUSPENDED);
        em.flush();
        mvc.perform(get("/group-purchases/" + gp.getId())).andExpect(status().isNotFound());
        mvc.perform(get("/group-purchases")).andExpect(status().isOk())
                .andExpect(content().string(not(containsString("휴점상품"))));
    }

    @Test void combinesSearchAddressAndCategoryAndTreatsWildcardsLiterally() throws Exception {
        open("유기농 감자");
        purchase("사과", "과일마트", "시흥시 배곧동", "과일", GroupPurchase.Status.OPEN,
                LocalDateTime.now().minusDays(1), LocalDateTime.now().plusDays(1));
        mvc.perform(get("/group-purchases").with(member()).param("q", "동네마트")
                        .param("district", "정왕").param("category", "채소"))
                .andExpect(status().isOk()).andExpect(content().string(containsString("유기농 감자")))
                .andExpect(content().string(not(containsString("과일마트"))));
        mvc.perform(get("/group-purchases").with(member()).param("q", "%"))
                .andExpect(status().isOk()).andExpect(content().string(containsString("조건에 맞는 공동구매가 없어요")));
    }

    @Test void paginatesAndPreservesFilters() throws Exception {
        for (int i = 0; i < 13; i++) open("공구" + i);
        mvc.perform(get("/group-purchases").with(member()).param("q", "공구").param("district", "정왕"))
                .andExpect(status().isOk()).andExpect(content().string(containsString("다음 →")))
                .andExpect(content().string(containsString("page=1")));
        mvc.perform(get("/group-purchases").with(member()).param("page", "1"))
                .andExpect(status().isOk()).andExpect(content().string(containsString("2 / 2")))
                .andExpect(content().string(not(containsString("다음 →"))));
    }

    @Test void escapesUserContentAndHandlesEmptyNegativeAndOutOfRangePages() throws Exception {
        open("<script>alert(1)</script>");
        mvc.perform(get("/group-purchases").with(member()).param("page", "-1"))
                .andExpect(status().isOk()).andExpect(content().string(containsString("&lt;script&gt;")))
                .andExpect(content().string(not(containsString("<script>alert(1)</script>"))));
        mvc.perform(get("/group-purchases").with(member()).param("page", "99"))
                .andExpect(status().isOk()).andExpect(content().string(containsString("조건에 맞는 공동구매가 없어요")));
    }
}
