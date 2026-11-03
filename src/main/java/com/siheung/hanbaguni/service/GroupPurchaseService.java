package com.siheung.hanbaguni.service;

import com.siheung.hanbaguni.dto.GroupPurchaseForm;
import com.siheung.hanbaguni.entity.GroupPurchase;
import com.siheung.hanbaguni.entity.Product;
import com.siheung.hanbaguni.entity.Store;
import com.siheung.hanbaguni.repository.GroupPurchaseRepository;
import com.siheung.hanbaguni.repository.OrderRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Objects;

@Service
@Transactional(readOnly = true)
public class GroupPurchaseService {

    private final GroupPurchaseRepository groupPurchaseRepository;
    private final StoreService storeService;
    private final ProductService productService;
    private final OrderRepository orderRepository;

    public GroupPurchaseService(
            GroupPurchaseRepository groupPurchaseRepository,
            StoreService storeService,
            ProductService productService,
            OrderRepository orderRepository) {
        this.groupPurchaseRepository = groupPurchaseRepository;
        this.storeService = storeService;
        this.productService = productService;
        this.orderRepository = orderRepository;
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

    public GroupPurchase getWithStoreAndOwnershipCheck(Long storeId, Long id, Long sellerId) {
        GroupPurchase gp = getWithOwnershipCheck(id, sellerId);
        if (!gp.getStore().getId().equals(storeId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "해당 매장에 속한 공동구매 모집이 아닙니다.");
        }
        return gp;
    }

    @Transactional
    public Long createGroupPurchase(Long storeId, Long sellerId, GroupPurchaseForm form) {
        Store store = storeService.getStoreWithOwnershipCheck(storeId, sellerId);
        Product product = productService.getProductWithStoreAndOwnershipCheck(storeId, form.getProductId(), sellerId);

        if (form.getStatus() != null && form.getStatus() != GroupPurchase.Status.DRAFT
                && form.getStatus() != GroupPurchase.Status.OPEN) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "새 모집은 초안 또는 게시 상태로만 만들 수 있습니다.");
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
    public void updateGroupPurchase(Long storeId, Long id, Long sellerId, GroupPurchaseForm form) {
        GroupPurchase gp = lockForSeller(storeId, id, sellerId);

        boolean targetChanged = !Objects.equals(gp.getTargetQuantity(), form.getTargetQuantity());
        validateSellerChange(gp, form.getStatus(), targetChanged);
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
    public void publishGroupPurchase(Long storeId, Long id, Long sellerId) {
        GroupPurchase gp = lockForSeller(storeId, id, sellerId);
        validateSellerChange(gp, GroupPurchase.Status.OPEN, false);
        gp.publish();
    }

    @Transactional
    public void updateStatus(Long storeId, Long id, Long sellerId, GroupPurchase.Status status) {
        GroupPurchase gp = lockForSeller(storeId, id, sellerId);
        validateSellerChange(gp, status, false);
        gp.updateStatus(status);
    }

    private GroupPurchase lockForSeller(Long storeId, Long id, Long sellerId) {
        GroupPurchase gp = groupPurchaseRepository.findByIdWithLock(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "모집 정보를 찾을 수 없습니다."));
        if (!gp.getStore().isOwnedBy(sellerId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "모집 관리 권한이 없습니다.");
        }
        if (!Objects.equals(gp.getStore().getId(), storeId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "해당 매장의 모집이 아닙니다.");
        }
        return gp;
    }

    private void validateSellerChange(GroupPurchase gp, GroupPurchase.Status requested, boolean targetChanged) {
        boolean statusChanged = requested != null && requested != gp.getStatus();
        if (!statusChanged && !targetChanged) {
            return;
        }
        if (gp.getStatus() == GroupPurchase.Status.SUCCESS || gp.getStatus() == GroupPurchase.Status.FAILED
                || requested == GroupPurchase.Status.SUCCESS || requested == GroupPurchase.Status.FAILED
                || orderRepository.existsByGroupPurchaseId(gp.getId())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "주문 이력이 있거나 결과가 확정된 모집의 목표와 상태는 변경할 수 없습니다. 성공·실패는 자동으로 결정됩니다.");
        }
    }

    @Transactional
    public void deleteGroupPurchase(Long storeId, Long id, Long sellerId) {
        GroupPurchase gp = lockForSeller(storeId, id, sellerId);
        if (gp.getCurrentQuantity() > 0 || orderRepository.existsByGroupPurchaseId(id)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "이미 예약 또는 주문 내역이 있는 공동구매는 삭제할 수 없습니다.");
        }
        groupPurchaseRepository.delete(gp);
    }
}
