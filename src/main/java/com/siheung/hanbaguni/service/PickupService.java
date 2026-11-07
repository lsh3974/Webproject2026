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
import jakarta.persistence.LockModeType;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.security.SecureRandom;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;

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
    private final com.siheung.hanbaguni.notification.NotificationEventService notificationEvents;

    public PickupService(
            PickupRepository pickupRepository,
            StoreService storeService,
            OrderRepository orderRepository,
            GroupPurchaseRepository groupPurchaseRepository,
            EntityManager entityManager,
            com.siheung.hanbaguni.notification.NotificationEventService notificationEvents) {
        this.pickupRepository = pickupRepository;
        this.storeService = storeService;
        this.orderRepository = orderRepository;
        this.groupPurchaseRepository = groupPurchaseRepository;
        this.entityManager = entityManager;
        this.notificationEvents = notificationEvents;
    }

    public List<Pickup> findByStoreId(Long storeId, Long sellerId) {
        storeService.getStoreWithOwnershipCheck(storeId, sellerId);
        return pickupRepository.findByStoreIdOrderByIdDesc(storeId);
    }

    public Pickup getByCodeAndStore(String pickupCode, Long storeId, Long sellerId) {
        storeService.getStoreWithOwnershipCheck(storeId, sellerId);
        String cleanCode = pickupCode != null ? pickupCode.trim().toUpperCase(Locale.ROOT) : "";
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

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public Pickup createOrGetPickupForOrder(Order order) {
        if (order.getStatus() != Order.Status.PAID) {
            throw new IllegalStateException("결제 완료(PAID) 상태인 주문만 수령증을 발급할 수 있습니다.");
        }

        // 결제 완료 및 환불 처리와 같은 모집 → 주문 순서로 잠급니다.
        lockGroup(order.getGroupPurchaseId());
        Order lockedOrder = lockOrder(order.getId());
        Optional<Pickup> existing = pickupRepository.findByOrderId(lockedOrder.getId());
        if (existing.isPresent()) {
            return existing.get();
        }
        if (lockedOrder.getStatus() != Order.Status.PAID) {
            // 구매자 상세를 읽은 뒤 환불 상태가 바뀐 경우 새로 발급하지 않습니다.
            return null;
        }

        User customer = lockedOrder.getUser();
        GroupPurchase group = lockedOrder.getGroupPurchase();
        // 주문 ID를 코드에 포함하여 서로 다른 주문 사이의 DB 유일성 충돌도 방지합니다.
        String code = "PK-" + randomCodePart(8) + "-"
                + Long.toUnsignedString(lockedOrder.getId(), 36).toUpperCase(Locale.ROOT);
        return pickupRepository.saveAndFlush(new Pickup(lockedOrder, group.getStore(), group,
                customer, code, lockedOrder.getQuantity(), customer.getDisplayName(), customer.getPhoneE164()));
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void markReady(Long pickupId, Long storeId, Long sellerId) {
        transition(getByIdWithStoreAndOwnershipCheck(pickupId, storeId, sellerId), Pickup::markReady);
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void markPickedUp(Long pickupId, Long storeId, Long sellerId) {
        transition(getByIdWithStoreAndOwnershipCheck(pickupId, storeId, sellerId), Pickup::markPickedUp);
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void markReady(Long pickupId, Long sellerId) {
        transition(getByIdWithOwnershipCheck(pickupId, sellerId), Pickup::markReady);
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void markPickedUp(Long pickupId, Long sellerId) {
        transition(getByIdWithOwnershipCheck(pickupId, sellerId), Pickup::markPickedUp);
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public Pickup markPickedUpByCode(String pickupCode, Long storeId, Long sellerId) {
        return transition(getByCodeAndStore(pickupCode, storeId, sellerId), Pickup::markPickedUp);
    }

    private Pickup transition(Pickup pickup, Consumer<Pickup> change) {
        GroupPurchase group = lockGroup(pickup.getGroupPurchase().getId());
        entityManager.refresh(group, LockModeType.PESSIMISTIC_WRITE);
        if (pickup.getOrder() != null) {
            Order order = lockOrder(pickup.getOrder().getId());
            if (order.getStatus() != Order.Status.PAID) {
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "결제 완료(PAID) 상태인 주문만 수령 처리할 수 있습니다.");
            }
        }

        Pickup locked = pickupRepository.findByIdWithLock(pickup.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "수령 정보를 찾을 수 없습니다."));
        entityManager.refresh(locked, LockModeType.PESSIMISTIC_WRITE);
        Pickup.Status before = locked.getStatus();
        try {
            change.accept(locked);
            if (locked.getOrder() != null && before != locked.getStatus()) {
                if (locked.getStatus() == Pickup.Status.READY) {
                    notificationEvents.pickupReady(locked);
                } else if (locked.getStatus() == Pickup.Status.PICKED_UP) {
                    notificationEvents.pickupCompleted(locked);
                }
            }
        } catch (IllegalStateException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
        }
        return locked;
    }

    private GroupPurchase lockGroup(Long groupId) {
        return groupPurchaseRepository.findByIdWithLock(groupId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "공동구매를 찾을 수 없습니다."));
    }

    private Order lockOrder(Long orderId) {
        Order order = orderRepository.findPaymentOrderWithLock(orderId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "주문을 찾을 수 없습니다."));
        entityManager.refresh(order, LockModeType.PESSIMISTIC_WRITE);
        return order;
    }

    private static String randomCodePart(int length) {
        StringBuilder part = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            part.append(CODE_CHARS.charAt(RANDOM.nextInt(CODE_CHARS.length())));
        }
        return part.toString();
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
