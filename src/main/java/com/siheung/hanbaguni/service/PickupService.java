package com.siheung.hanbaguni.service;

import com.siheung.hanbaguni.entity.GroupPurchase;
import com.siheung.hanbaguni.entity.Order;
import com.siheung.hanbaguni.entity.Pickup;
import com.siheung.hanbaguni.entity.Store;
import com.siheung.hanbaguni.entity.User;
import com.siheung.hanbaguni.repository.GroupPurchaseRepository;
import com.siheung.hanbaguni.repository.OrderRepository;
import com.siheung.hanbaguni.repository.PickupRepository;
import jakarta.persistence.EntityManager;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.security.SecureRandom;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class PickupService {

    private static final String CODE_CHARS = "23456789ABCDEFGHJKLMNPQRSTUVWXYZ";
    private static final SecureRandom RANDOM = new SecureRandom();

    private final PickupRepository pickupRepository;
    private final StoreService storeService;
    private final OrderRepository orderRepository;
    private final GroupPurchaseRepository groupPurchaseRepository;
    private final EntityManager entityManager;

    public PickupService(
            PickupRepository pickupRepository,
            StoreService storeService,
            OrderRepository orderRepository,
            GroupPurchaseRepository groupPurchaseRepository) {
        this(pickupRepository, storeService, orderRepository, groupPurchaseRepository, null);
    }

    @Autowired
    public PickupService(
            PickupRepository pickupRepository,
            StoreService storeService,
            OrderRepository orderRepository,
            GroupPurchaseRepository groupPurchaseRepository,
            @Autowired(required = false) EntityManager entityManager) {
        this.pickupRepository = pickupRepository;
        this.storeService = storeService;
        this.orderRepository = orderRepository;
        this.groupPurchaseRepository = groupPurchaseRepository;
        this.entityManager = entityManager;
    }

    public List<Pickup> findByStoreId(Long storeId, Long sellerId) {
        storeService.getStoreWithOwnershipCheck(storeId, sellerId);
        return pickupRepository.findByStoreIdOrderByIdDesc(storeId);
    }

    public Pickup getByCodeAndStore(String pickupCode, Long storeId, Long sellerId) {
        storeService.getStoreWithOwnershipCheck(storeId, sellerId);
        String cleanCode = pickupCode != null ? pickupCode.trim().toUpperCase() : "";
        return pickupRepository.findByPickupCodeAndStoreId(cleanCode, storeId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "해당 매장의 수령 건을 찾을 수 없습니다. (코드: " + cleanCode + ")"));
    }

    public Pickup getByIdWithOwnershipCheck(Long pickupId, Long sellerId) {
        Pickup pickup = pickupRepository.findById(pickupId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "수령 정보를 찾을 수 없습니다."));
        if (!pickup.getStore().isOwnedBy(sellerId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "해당 수령 건에 대한 매장 관리 권한이 없습니다.");
        }
        return pickup;
    }

    public Pickup getByIdWithStoreAndOwnershipCheck(Long pickupId, Long storeId, Long sellerId) {
        Pickup pickup = getByIdWithOwnershipCheck(pickupId, sellerId);
        if (!pickup.getStore().getId().equals(storeId)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "해당 매장의 수령 건이 아닙니다.");
        }
        return pickup;
    }

    public Optional<Pickup> findByOrderId(Long orderId) {
        return pickupRepository.findByOrderId(orderId);
    }

    public Optional<Pickup> findByOrderIdAndCustomerId(Long orderId, Long userId) {
        return pickupRepository.findByOrderIdAndCustomerId(orderId, userId);
    }

    @Transactional
    public synchronized Pickup createOrGetPickupForOrder(Order order) {
        if (order.getStatus() != Order.Status.PAID) {
            throw new IllegalStateException("결제 완료(PAID) 상태인 주문만 수령증을 발급할 수 있습니다.");
        }

        Optional<Pickup> existing = order.getId() != null ? pickupRepository.findByOrderId(order.getId()) : Optional.empty();
        if (existing.isPresent()) {
            return existing.get();
        }

        String pickupCode = generateUniquePickupCode();
        String customerName = order.getUser() != null ? order.getUser().getDisplayName() : "구매자";
        String customerPhone = order.getUser() != null ? order.getUser().getPhoneE164() : "";

        Pickup pickup = new Pickup(
                order,
                order.getGroupPurchase().getStore(),
                order.getGroupPurchase(),
                order.getUser(),
                pickupCode,
                order.getQuantity(),
                customerName,
                customerPhone
        );

        try {
            return pickupRepository.saveAndFlush(pickup);
        } catch (Exception e) {
            return pickupRepository.findByOrderId(order.getId())
                    .orElseThrow(() -> new IllegalStateException("수령증 생성 중 동시성 오류가 발생했습니다.", e));
        }
    }

    @Transactional
    public void markReady(Long pickupId, Long storeId, Long sellerId) {
        Pickup pickup = getByIdWithStoreAndOwnershipCheck(pickupId, storeId, sellerId);
        try {
            pickup.markReady();
            pickupRepository.saveAndFlush(pickup);
        } catch (IllegalStateException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
        }
    }

    @Transactional
    public void markPickedUp(Long pickupId, Long storeId, Long sellerId) {
        Pickup pickup = getByIdWithStoreAndOwnershipCheck(pickupId, storeId, sellerId);
        completePickupWithLock(pickup);
    }

    @Transactional
    public void markReady(Long pickupId, Long sellerId) {
        Pickup pickup = getByIdWithOwnershipCheck(pickupId, sellerId);
        try {
            pickup.markReady();
            pickupRepository.saveAndFlush(pickup);
        } catch (IllegalStateException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
        }
    }

    @Transactional
    public void markPickedUp(Long pickupId, Long sellerId) {
        Pickup pickup = getByIdWithOwnershipCheck(pickupId, sellerId);
        completePickupWithLock(pickup);
    }

    @Transactional
    public Pickup markPickedUpByCode(String pickupCode, Long storeId, Long sellerId) {
        Pickup pickup = getByCodeAndStore(pickupCode, storeId, sellerId);
        completePickupWithLock(pickup);
        return pickup;
    }

    /**
     * 환불 요청(RefundTransactions)과 동일한 잠금 순서를 적용하여
     * 환불과 수령 완료가 동시에 일어나는 경쟁 상태(Race Condition)를 원자적으로 방어합니다:
     * GroupPurchase (비관적 락) -> Order (비관적 락) -> Pickup (비관적 락 및 최신 상태 재조회)
     */
    private void completePickupWithLock(Pickup pickup) {
        if (pickup.getOrder() != null) {
            Long orderId = pickup.getOrder().getId();
            Long groupId = pickup.getGroupPurchase() != null
                    ? pickup.getGroupPurchase().getId()
                    : (pickup.getOrder().getGroupPurchase() != null ? pickup.getOrder().getGroupPurchase().getId() : null);

            // 1. 환불과 동일하게 공동구매(GroupPurchase) 행을 비관적 락으로 먼저 잠금
            if (groupId != null && groupPurchaseRepository != null) {
                groupPurchaseRepository.findByIdWithLock(groupId);
            }

            // 2. 주문(Order) 행을 비관적 락으로 잠금
            if (orderRepository != null) {
                Order lockedOrder = orderRepository.findPaymentOrderWithLock(orderId)
                        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "주문을 찾을 수 없습니다."));

                // 3. 잠금을 획득한 시점의 최신 주문 상태가 PAID인지 엄격히 검증
                if (lockedOrder.getStatus() != Order.Status.PAID) {
                    throw new ResponseStatusException(
                            HttpStatus.CONFLICT,
                            "결제 완료(PAID) 상태인 주문만 수령 처리할 수 있습니다. (현재 주문 상태: " + lockedOrder.getStatus() + ")"
                    );
                }
            }
        }

        // 3. 2-2 피드백 반영: Pickup 행 자체도 비관적 락으로 최신 상태를 재조회하여 중복 수령 완료 경쟁 방어
        Pickup lockedPickup = pickupRepository.findByIdWithLock(pickup.getId())
                .orElse(pickup);

        // 1차 캐시 최신화: 락을 획득하기 위해 대기하다가 깨어났을 때 커밋된 최신 DB 상태로 엔티티를 동기화
        if (entityManager != null && entityManager.contains(lockedPickup)) {
            entityManager.refresh(lockedPickup);
        }

        // 이미 완료되었거나 취소된 건인지 최신 DB 상태 검증
        if (lockedPickup.getStatus() == Pickup.Status.PICKED_UP) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "이미 수령이 완료된 건입니다.");
        }
        if (lockedPickup.getStatus() == Pickup.Status.CANCELLED) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "취소된 건은 수령할 수 없습니다.");
        }

        try {
            lockedPickup.markPickedUp();
            pickupRepository.saveAndFlush(lockedPickup);
        } catch (IllegalStateException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
        }
    }

    @Transactional
    public Pickup createTestPickup(Store store, GroupPurchase groupPurchase, User customer, int quantity, String customerName, String customerPhone) {
        String code = generateUniquePickupCode();
        Pickup pickup = new Pickup(
                null,
                store,
                groupPurchase,
                customer,
                code,
                quantity,
                customerName != null ? customerName : "테스트구매자",
                customerPhone != null ? customerPhone : "010-1234-5678"
        );
        return pickupRepository.save(pickup);
    }

    public String generateUniquePickupCode() {
        String datePrefix = LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE); // YYYYMMDD
        for (int attempt = 0; attempt < 10; attempt++) {
            StringBuilder sb = new StringBuilder("PK-").append(datePrefix).append("-");
            for (int i = 0; i < 4; i++) {
                sb.append(CODE_CHARS.charAt(RANDOM.nextInt(CODE_CHARS.length())));
            }
            String candidate = sb.toString();
            if (!pickupRepository.existsByPickupCode(candidate)) {
                return candidate;
            }
        }
        // 10회 시도 후 충돌 시 고유 식별자를 생성하며 DB 중복 여부를 최종 재확인
        while (true) {
            String fallback = "PK-" + datePrefix + "-" + UUID.randomUUID().toString().replace("-", "").substring(0, 6).toUpperCase();
            if (!pickupRepository.existsByPickupCode(fallback)) {
                return fallback;
            }
        }
    }
}
