package com.siheung.hanbaguni.service;

import com.siheung.hanbaguni.dto.GroupPurchaseForm;
import com.siheung.hanbaguni.entity.GroupPurchase;
import com.siheung.hanbaguni.entity.Product;
import com.siheung.hanbaguni.entity.Store;
import com.siheung.hanbaguni.repository.GroupPurchaseRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Service
@Transactional(readOnly = true)
public class GroupPurchaseService {

    private final GroupPurchaseRepository groupPurchaseRepository;
    private final StoreService storeService;
    private final ProductService productService;

    public GroupPurchaseService(
            GroupPurchaseRepository groupPurchaseRepository,
            StoreService storeService,
            ProductService productService) {
        this.groupPurchaseRepository = groupPurchaseRepository;
        this.storeService = storeService;
        this.productService = productService;
    }

    public List<GroupPurchase> findByStoreId(Long storeId) {
        return groupPurchaseRepository.findByStoreIdOrderByIdDesc(storeId);
    }

    public GroupPurchase getById(Long id) {
        return groupPurchaseRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "공동구매 모집 정보를 찾을 수 없습니다."));
    }

    public GroupPurchase getWithOwnershipCheck(Long id, Long sellerId) {
        GroupPurchase gp = getById(id);
        if (!gp.getStore().isOwnedBy(sellerId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "해당 공동구매 모집의 관리 권한이 없습니다.");
        }
        return gp;
    }

    @Transactional
    public Long createGroupPurchase(Long storeId, Long sellerId, GroupPurchaseForm form) {
        Store store = storeService.getStoreWithOwnershipCheck(storeId, sellerId);
        Product product = productService.getProductWithOwnershipCheck(form.getProductId(), sellerId);

        if (!product.getStore().getId().equals(storeId)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "해당 매장에 속한 상품만 공동구매로 등록할 수 있습니다.");
        }

        GroupPurchase gp = new GroupPurchase(
                store,
                product,
                form.getTitle().trim(),
                form.getGroupPrice(),
                form.getTargetQuantity(),
                form.getMaxQuantity(),
                form.getLimitPerUser(),
                form.getStartAt(),
                form.getEndAt(),
                form.getPickupStartAt(),
                form.getPickupEndAt(),
                form.getPickupNotice() != null ? form.getPickupNotice().trim() : null,
                form.getDescription(),
                form.getStatus() != null ? form.getStatus() : GroupPurchase.Status.DRAFT
        );

        GroupPurchase saved = groupPurchaseRepository.save(gp);
        return saved.getId();
    }

    @Transactional
    public void updateGroupPurchase(Long id, Long sellerId, GroupPurchaseForm form) {
        GroupPurchase gp = getWithOwnershipCheck(id, sellerId);

        gp.update(
                form.getTitle().trim(),
                form.getGroupPrice(),
                form.getTargetQuantity(),
                form.getMaxQuantity(),
                form.getLimitPerUser(),
                form.getStartAt(),
                form.getEndAt(),
                form.getPickupStartAt(),
                form.getPickupEndAt(),
                form.getPickupNotice() != null ? form.getPickupNotice().trim() : null,
                form.getDescription(),
                form.getStatus()
        );
    }

    @Transactional
    public void publishGroupPurchase(Long id, Long sellerId) {
        GroupPurchase gp = getWithOwnershipCheck(id, sellerId);
        gp.publish();
    }

    @Transactional
    public void updateStatus(Long id, Long sellerId, GroupPurchase.Status status) {
        GroupPurchase gp = getWithOwnershipCheck(id, sellerId);
        gp.updateStatus(status);
    }

    @Transactional
    public void deleteGroupPurchase(Long id, Long sellerId) {
        GroupPurchase gp = getWithOwnershipCheck(id, sellerId);
        if (gp.getCurrentQuantity() > 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "이미 참여자가 있는 공동구매는 삭제할 수 없습니다.");
        }
        groupPurchaseRepository.delete(gp);
    }
}
