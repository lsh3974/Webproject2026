package com.siheung.hanbaguni.service;

import com.siheung.hanbaguni.dto.GroupPurchaseReservationSummary;
import com.siheung.hanbaguni.entity.*;
import com.siheung.hanbaguni.repository.GroupPurchaseRepository;
import com.siheung.hanbaguni.repository.OrderRepository;
import com.siheung.hanbaguni.repository.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.List;

@Service
@Transactional(readOnly = true)
public class OrderReservationService {

    public static final int RESERVATION_HOLD_MINUTES = 15;

    private final OrderRepository orderRepository;
    private final GroupPurchaseRepository groupPurchaseRepository;
    private final UserRepository userRepository;

    public OrderReservationService(
            OrderRepository orderRepository,
            GroupPurchaseRepository groupPurchaseRepository,
            UserRepository userRepository) {
        this.orderRepository = orderRepository;
        this.groupPurchaseRepository = groupPurchaseRepository;
        this.userRepository = userRepository;
    }

    /**
     * 현재 모집 중인 공동구매 목록과 실시간 선점/잔여 수량 요약을 조회합니다.
     */
    public List<GroupPurchaseReservationSummary> getOpenGroupPurchaseSummaries(Long userId) {
        LocalDateTime now = LocalDateTime.now();
        List<GroupPurchase> openList = groupPurchaseRepository.findOpenGroupPurchases(now);

        return openList.stream()
                .map(gp -> buildSummary(gp, userId, now))
                .toList();
    }

    /**
     * 특정 공동구매의 예약 가능 현황 요약을 조회합니다.
     */
    public GroupPurchaseReservationSummary getReservationSummary(Long groupPurchaseId, Long userId) {
        LocalDateTime now = LocalDateTime.now();
        GroupPurchase gp = groupPurchaseRepository.findWithDetailsById(groupPurchaseId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "공동구매 모집 정보를 찾을 수 없습니다."));

        return buildSummary(gp, userId, now);
    }

    /**
     * 공동구매 수량을 선점하여 예약 대기(PENDING) 주문을 생성합니다.
     * 동시 주문 시 초과 예약을 방지하기 위해 GroupPurchase 행에 비관적 쓰기 락(PESSIMISTIC_WRITE)을 획득합니다.
     */
    @Transactional
    public Order reserveOrder(Long userId, Long groupPurchaseId, Integer quantity) {
        if (quantity == null || quantity < 1) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "주문 수량은 1개 이상이어야 합니다.");
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "회원 정보를 확인할 수 없습니다."));

        if (user.getStatus() != User.Status.ACTIVE) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "활성 상태의 회원만 공동구매에 참여할 수 있습니다.");
        }

        LocalDateTime now = LocalDateTime.now();

        // 1. 비관적 락으로 공동구매 행 잠금 (동시 수량 초과 예약 차단)
        GroupPurchase gp = groupPurchaseRepository.findByIdWithLock(groupPurchaseId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "공동구매 모집 정보를 찾을 수 없습니다."));

        // 2. 모집 상태·기간·매장·상품 및 본인 매장 여부 검증
        String unavailableReason = checkGroupPurchaseAvailability(gp, userId, now);
        if (unavailableReason != null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, unavailableReason);
        }

        // 3. 해당 공동구매의 기한 만료된 PENDING 예약을 먼저 EXPIRED로 전환하여 잔여 수량 확보
        expireOverdueReservationsForGroupPurchase(groupPurchaseId, now);

        // 4. 확정 수량(PAID / current_quantity) + 활성 예약 수량(PENDING) 합산으로 가용 재고 검증
        int confirmedQty = Math.max(
                gp.getCurrentQuantity(),
                orderRepository.sumQuantityByGroupPurchaseIdAndStatus(groupPurchaseId, Order.Status.PAID)
        );
        int pendingQty = orderRepository.sumActivePendingQuantity(groupPurchaseId, now);
        int occupiedQty = confirmedQty + pendingQty;
        int remainingQty = Math.max(0, gp.getMaxQuantity() - occupiedQty);

        if (quantity > remainingQty) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    String.format("남은 예약 가능 수량(%d)을 초과하여 주문할 수 없습니다.", remainingQty)
            );
        }

        // 5. 1인당 구매 제한 수량(limit_per_user) 검증
        if (gp.getLimitPerUser() != null) {
            int userActiveQty = orderRepository.sumUserActiveQuantity(groupPurchaseId, userId, now);
            if (userActiveQty + quantity > gp.getLimitPerUser()) {
                int leftForUser = Math.max(0, gp.getLimitPerUser() - userActiveQty);
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        String.format(
                                "1인당 최대 구매 제한 수량(%d)을 초과할 수 없습니다. (현재 내 유효 주문: %d, 추가 가능: %d)",
                                gp.getLimitPerUser(),
                                userActiveQty,
                                leftForUser
                        )
                );
            }
        }

        // 6. 단가 스냅샷 및 총 결제 예정 금액 서버 계산
        int unitPrice = gp.getGroupPrice();
        int totalPrice;
        try {
            totalPrice = Math.multiplyExact(unitPrice, quantity);
        } catch (ArithmeticException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "주문 금액 계산 범위를 초과했습니다.");
        }

        LocalDateTime calculatedUntil = now.plusMinutes(RESERVATION_HOLD_MINUTES);
        LocalDateTime reservedUntil = calculatedUntil.isAfter(gp.getEndAt()) ? gp.getEndAt() : calculatedUntil;

        Order order = new Order(user, gp, quantity, unitPrice, totalPrice, reservedUntil);
        return orderRepository.save(order);
    }

    /**
     * 로그인한 회원의 주문·예약 목록을 최신순으로 조회합니다.
     * 조회 시점에 예약 기한이 지난 PENDING 주문은 자동으로 EXPIRED 상태로 전환합니다.
     */
    @Transactional
    public List<Order> getMyOrders(Long userId) {
        LocalDateTime now = LocalDateTime.now();
        expireOverdueReservationsForUser(userId, now);
        return orderRepository.findByUserIdOrderByIdDesc(userId);
    }

    /**
     * 소유권을 검증한 뒤 단일 주문·예약 상세 정보를 조회합니다.
     */
    @Transactional
    public Order getOrderWithOwnershipCheck(Long orderId, Long userId) {
        Order order = orderRepository.findWithDetailsById(orderId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "주문 내역을 찾을 수 없습니다."));

        if (!order.isOwnedBy(userId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "본인의 주문 내역만 조회하거나 관리할 수 있습니다.");
        }

        LocalDateTime now = LocalDateTime.now();
        if (order.isExpiredAt(now)) {
            order.expireReservation();
        }

        return order;
    }

    /**
     * 결제 대기(PENDING) 상태의 수량 예약 주문을 취소하고 선점된 수량을 즉시 반환합니다.
     * 동시 취소 및 결제 충돌을 방지하기 위해 Order 행에 비관적 쓰기 락(PESSIMISTIC_WRITE)을 획득합니다.
     */
    @Transactional
    public Order cancelReservation(Long orderId, Long userId, String cancelReason) {
        if (cancelReason != null && cancelReason.length() > 255) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "취소 사유는 255자 이내여야 합니다.");
        }

        Order order = orderRepository.findByIdWithLock(orderId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "주문 내역을 찾을 수 없습니다."));

        if (!order.isOwnedBy(userId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "본인의 주문만 취소할 수 있습니다.");
        }

        LocalDateTime now = LocalDateTime.now();
        if (order.isExpiredAt(now)) {
            order.expireReservation();
            return order;
        }

        if (order.getStatus() != Order.Status.PENDING) {
            String message = switch (order.getStatus()) {
                case PAID -> "이미 결제가 완료된 주문입니다. 결제 취소(환불) 기능을 이용해주세요.";
                case CANCELLED -> "이미 취소된 예약 주문입니다.";
                case EXPIRED -> "이미 예약 시간이 만료된 주문입니다.";
                case REFUNDED -> "이미 환불 처리된 주문입니다.";
                default -> "현재 상태에서는 예약을 취소할 수 없습니다.";
            };
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
        }

        order.cancelReservation(cancelReason);
        return order;
    }

    private void expireOverdueReservationsForGroupPurchase(Long groupPurchaseId, LocalDateTime now) {
        List<Order> expiredTargets = orderRepository
                .findByGroupPurchaseIdAndStatusAndReservedUntilLessThanEqual(
                        groupPurchaseId, Order.Status.PENDING, now);
        for (Order expiredOrder : expiredTargets) {
            expiredOrder.expireReservation();
        }
    }

    private void expireOverdueReservationsForUser(Long userId, LocalDateTime now) {
        List<Order> expiredTargets = orderRepository
                .findByUserIdAndStatusAndReservedUntilLessThanEqual(
                        userId, Order.Status.PENDING, now);
        for (Order expiredOrder : expiredTargets) {
            expiredOrder.expireReservation();
        }
    }

    private GroupPurchaseReservationSummary buildSummary(GroupPurchase gp, Long userId, LocalDateTime now) {
        int confirmedQty = Math.max(
                gp.getCurrentQuantity(),
                orderRepository.sumQuantityByGroupPurchaseIdAndStatus(gp.getId(), Order.Status.PAID)
        );
        int pendingQty = orderRepository.sumActivePendingQuantity(gp.getId(), now);
        int userActiveQty = (userId != null)
                ? orderRepository.sumUserActiveQuantity(gp.getId(), userId, now)
                : 0;

        String unavailableReason = checkGroupPurchaseAvailability(gp, userId, now);
        boolean available = (unavailableReason == null);

        return GroupPurchaseReservationSummary.of(
                gp,
                confirmedQty,
                pendingQty,
                userActiveQty,
                available,
                unavailableReason
        );
    }

    private String checkGroupPurchaseAvailability(GroupPurchase gp, Long userId, LocalDateTime now) {
        if (gp.getStatus() != GroupPurchase.Status.OPEN) {
            return "현재 모집 중(OPEN) 상태가 아닌 공동구매입니다.";
        }
        if (gp.getStore().getStatus() != Store.Status.ACTIVE) {
            return "현재 영업 중인 매장의 공동구매만 참여할 수 있습니다.";
        }
        if (gp.getProduct().getStatus() != Product.Status.ON_SALE) {
            return "현재 판매 중인 상품만 주문 예약할 수 있습니다.";
        }
        if (now.isBefore(gp.getStartAt())) {
            return "아직 모집이 시작되지 않은 공동구매입니다.";
        }
        if (!now.isBefore(gp.getEndAt())) {
            return "모집 기간이 마감된 공동구매입니다.";
        }
        if (userId != null && gp.getStore().isOwnedBy(userId)) {
            return "본인이 운영하는 매장의 공동구매에는 주문 예약을 할 수 없습니다.";
        }
        return null;
    }
}
