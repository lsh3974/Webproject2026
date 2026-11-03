package com.siheung.hanbaguni.service;

import com.siheung.hanbaguni.dto.GroupPurchaseForm;
import com.siheung.hanbaguni.entity.GroupPurchase;
import com.siheung.hanbaguni.entity.Order;
import com.siheung.hanbaguni.entity.Product;
import com.siheung.hanbaguni.entity.Store;
import com.siheung.hanbaguni.repository.GroupPurchaseRepository;
import com.siheung.hanbaguni.repository.OrderRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.List;

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
        GroupPurchase gp = groupPurchaseRepository.findByIdWithLock(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "공동구매 모집 정보를 찾을 수 없습니다."));
        validateStoreAndOwnership(gp, storeId, sellerId);

        validateUpdatePolicy(gp, form);

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
        GroupPurchase gp = groupPurchaseRepository.findByIdWithLock(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "공동구매 모집 정보를 찾을 수 없습니다."));
        validateStoreAndOwnership(gp, storeId, sellerId);
        gp.publish();
    }

    @Transactional
    public void updateStatus(Long storeId, Long id, Long sellerId, GroupPurchase.Status status) {
        GroupPurchase gp = groupPurchaseRepository.findByIdWithLock(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "공동구매 모집 정보를 찾을 수 없습니다."));
        validateStoreAndOwnership(gp, storeId, sellerId);

        validateStatusTransition(gp, status);

        gp.updateStatus(status);
    }

    @Transactional
    public void deleteGroupPurchase(Long storeId, Long id, Long sellerId) {
        GroupPurchase gp = groupPurchaseRepository.findByIdWithLock(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "공동구매 모집 정보를 찾을 수 없습니다."));
        validateStoreAndOwnership(gp, storeId, sellerId);

        if (gp.getCurrentQuantity() > 0 || orderRepository.existsByGroupPurchaseId(id)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "이미 예약 또는 주문 내역이 있는 공동구매는 삭제할 수 없습니다.");
        }
        groupPurchaseRepository.delete(gp);
    }

    private void validateStoreAndOwnership(GroupPurchase gp, Long storeId, Long sellerId) {
        if (!gp.getStore().isOwnedBy(sellerId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "해당 공동구매 모집의 관리 권한이 없습니다.");
        }
        if (!gp.getStore().getId().equals(storeId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "해당 매장에 속한 공동구매 모집이 아닙니다.");
        }
    }

    private void validateUpdatePolicy(GroupPurchase gp, GroupPurchaseForm form) {
        LocalDateTime now = LocalDateTime.now();
        int confirmedQty = Math.max(
                gp.getCurrentQuantity(),
                orderRepository.sumQuantityByGroupPurchaseIdAndStatus(gp.getId(), Order.Status.PAID)
        );
        int pendingQty = orderRepository.sumActivePendingQuantity(gp.getId(), now);
        int occupiedQty = confirmedQty + pendingQty;

        // 1. 최대 수량(maxQuantity) 축소 제한: 현재 점유(확정+유효예약) 수량 미만으로 축소 불가
        if (form.getMaxQuantity() < occupiedQty) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    String.format("최대 모집 수량(%d개)은 현재 점유된 수량(%d개) 이상이어야 합니다.",
                            form.getMaxQuantity(), occupiedQty)
            );
        }

        // 2. 1인당 구매 제한(limitPerUser) 축소 제한: 기존 주문자의 활성 수량 미만으로 설정 불가
        if (form.getLimitPerUser() != null) {
            List<Long> activeQuantities = orderRepository.findActiveUserQuantities(gp.getId(), now);
            long maxUserQty = activeQuantities.stream().mapToLong(Long::longValue).max().orElse(0L);
            if (maxUserQty > form.getLimitPerUser()) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        String.format("기존 예약/주문 수량(%d개)이 변경하려는 1인당 구매 제한(%d개)을 초과하는 회원이 있어 변경할 수 없습니다.",
                                maxUserQty, form.getLimitPerUser())
                );
            }
        }

        // 3. 마감 일시(endAt) 변경 시 유효 예약 보호:
        // 결제 대기 중인 유효 예약의 만료 일시보다 이른 시각으로 마감 일시를 앞당길 수 없음
        if (form.getEndAt().isBefore(gp.getEndAt())
                && orderRepository.hasOrdersInStatus(gp.getId(), Order.Status.PAYMENT_PENDING)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "결제 승인 결과를 확인 중인 주문이 있어 마감 시간을 앞당길 수 없습니다.");
        }
        if (pendingQty > 0) {
            orderRepository.findMaxReservedUntil(gp.getId(), now).ifPresent(maxReservedUntil -> {
                if (form.getEndAt().isBefore(maxReservedUntil)) {
                    throw new ResponseStatusException(
                            HttpStatus.BAD_REQUEST,
                            "현재 결제 대기 중인 예약의 만료 일시보다 이른 시각으로 마감 일시를 변경할 수 없습니다."
                    );
                }
            });
        }

        // 4. 상태 변경 검증 (DRAFT, CLOSED 전환 규칙)
        if (form.getStatus() != null && form.getStatus() != gp.getStatus()) {
            validateStatusTransition(gp, form.getStatus());
        }
    }

    private void validateStatusTransition(GroupPurchase gp, GroupPurchase.Status newStatus) {
        if (newStatus == gp.getStatus()) {
            return;
        }

        LocalDateTime now = LocalDateTime.now();
        int confirmedQty = Math.max(
                gp.getCurrentQuantity(),
                orderRepository.sumQuantityByGroupPurchaseIdAndStatus(gp.getId(), Order.Status.PAID)
        );
        int pendingQty = orderRepository.sumActivePendingQuantity(gp.getId(), now);
        int occupiedQty = confirmedQty + pendingQty;

        // DRAFT 상태로의 변경 차단: 이미 예약/주문 이력이 있거나 점유 수량이 존재하는 경우
        if (newStatus == GroupPurchase.Status.DRAFT) {
            if (occupiedQty > 0 || orderRepository.existsByGroupPurchaseId(gp.getId())) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "이미 예약 또는 주문 내역이 존재하는 공동구매는 초안(DRAFT) 상태로 되돌릴 수 없습니다."
                );
            }
        }

        // CLOSED 상태로의 변경 시 유효한 결제 대기 예약 보호:
        // 아직 결제 유효 시간이 남은 예약이 있는 경우, 예약 결제 기회를 보장하기 위해 차단
        if (newStatus == GroupPurchase.Status.CLOSED) {
            if (pendingQty > 0) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        String.format("현재 결제 대기 중인 유효 예약(%d개)이 존재하여 상태를 마감(CLOSED)으로 변경할 수 없습니다.", pendingQty)
                );
            }
        }
    }
}
