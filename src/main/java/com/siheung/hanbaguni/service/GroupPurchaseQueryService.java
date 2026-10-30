package com.siheung.hanbaguni.service;

import com.siheung.hanbaguni.dto.GroupPurchaseView;
import com.siheung.hanbaguni.entity.GroupPurchase;
import com.siheung.hanbaguni.entity.Product;
import com.siheung.hanbaguni.entity.Store;
import com.siheung.hanbaguni.repository.GroupPurchaseRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.Locale;

@Service
@Transactional(readOnly = true)
public class GroupPurchaseQueryService {
    private static final int PAGE_SIZE = 12;
    private final GroupPurchaseRepository repository;

    public GroupPurchaseQueryService(GroupPurchaseRepository repository) {
        this.repository = repository;
    }

    public Page<GroupPurchaseView> search(String q, String district, String category, int page) {
        Specification<GroupPurchase> spec = visibleAt(LocalDateTime.now());
        String query = normalize(q);
        String area = normalize(district);
        String type = normalize(category);
        if (!query.isEmpty()) {
            String pattern = contains(query);
            spec = spec.and((root, cq, cb) -> cb.or(
                    cb.like(cb.lower(root.get("title")), pattern, '!'),
                    cb.like(cb.lower(root.get("product").get("name")), pattern, '!'),
                    cb.like(cb.lower(root.get("store").get("name")), pattern, '!')));
        }
        if (!area.isEmpty()) {
            spec = spec.and((root, cq, cb) ->
                    cb.like(cb.lower(root.get("store").get("address")), contains(area), '!'));
        }
        if (!type.isEmpty()) {
            spec = spec.and((root, cq, cb) ->
                    cb.like(cb.lower(root.get("product").get("category")), contains(type), '!'));
        }
        return repository.findAll(spec,
                PageRequest.of(Math.max(0, page), PAGE_SIZE, Sort.by(Sort.Direction.DESC, "id")))
                .map(GroupPurchaseView::from);
    }

    public GroupPurchaseView detail(Long id) {
        Specification<GroupPurchase> spec = visibleAt(LocalDateTime.now())
                .and((root, cq, cb) -> cb.equal(root.get("id"), id));
        return repository.findOne(spec).map(GroupPurchaseView::from)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "현재 조회할 수 없는 공동구매입니다."));
    }

    // Keep list and detail rules identical, so a guessed ID cannot expose a draft.
    private Specification<GroupPurchase> visibleAt(LocalDateTime now) {
        return (root, cq, cb) -> cb.and(
                cb.equal(root.get("status"), GroupPurchase.Status.OPEN),
                cb.lessThanOrEqualTo(root.get("startAt"), now),
                cb.greaterThan(root.get("endAt"), now),
                cb.equal(root.get("store").get("status"), Store.Status.ACTIVE),
                cb.equal(root.get("product").get("status"), Product.Status.ON_SALE));
    }

    public static String normalize(String value) {
        String result = value == null ? "" : value.strip();
        return result.length() > 100 ? result.substring(0, 100) : result;
    }

    private static String contains(String value) {
        return "%" + value.toLowerCase(Locale.ROOT).replace("!", "!!")
                .replace("%", "!%").replace("_", "!_") + "%";
    }
}
