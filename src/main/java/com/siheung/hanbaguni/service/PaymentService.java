package com.siheung.hanbaguni.service;

import com.siheung.hanbaguni.dto.PaymentCancelRequest;
import com.siheung.hanbaguni.dto.PaymentConfirmRequest;
import com.siheung.hanbaguni.entity.GroupPurchase;
import com.siheung.hanbaguni.entity.Order;
import com.siheung.hanbaguni.repository.GroupPurchaseRepository;
import com.siheung.hanbaguni.repository.OrderRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
public class PaymentService {

    private final OrderRepository orderRepository;
    private final GroupPurchaseRepository groupPurchaseRepository;

    public PaymentService(OrderRepository orderRepository, GroupPurchaseRepository groupPurchaseRepository) {
        this.orderRepository = orderRepository;
        this.groupPurchaseRepository = groupPurchaseRepository;
    }

    // 1. 선결제 승인 로직 (Phase 9 - 보안, 동시성, 비즈니스 제약 검증 고도화)
    @Transactional
    public String confirmPayment(Long orderId, PaymentConfirmRequest request, Long currentUserId) {
        // 1) 주문 존재 확인
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 주문입니다: " + orderId));

        // 2) 주문 소유권 검증
        if (!order.getUserId().equals(currentUserId)) {
            throw new IllegalArgumentException("해당 주문에 대한 접근 권한이 없습니다.");
        }

        // 3) 주문 상태 전이 검증 (PENDING 상태만 승인 가능)
        if (!"PENDING".equals(order.getStatus())) {
            if ("PAID".equals(order.getStatus())) {
                throw new IllegalStateException("이미 결제 완료된 주문입니다.");
            }
            throw new IllegalStateException("대기 중인 주문만 결제 승인할 수 있습니다. 현재 상태: " + order.getStatus());
        }

        // 4) 결제 승인 키 검증
        if (request.getProviderPaymentKey() == null || request.getProviderPaymentKey().isBlank()) {
            throw new IllegalArgumentException("결제 승인 키(providerPaymentKey)가 누락되었습니다.");
        }

        // 5) 결제 금액 일치 검증
        if (request.getAmountKrw() == null || !request.getAmountKrw().equals(order.getTotalPrice().longValue())) {
            throw new IllegalArgumentException("결제 금액이 주문 금액과 일치하지 않습니다.");
        }

        // 6) 공동구매 비관적 락(Pessimistic Lock) 조회 (동시성 방어)
        GroupPurchase groupPurchase = groupPurchaseRepository.findByIdWithLock(order.getGroupPurchaseId())
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 공동구매 캠페인입니다: " + order.getGroupPurchaseId()));

        // 7) 공동구매 모집 상태 및 기간 검증
        if (groupPurchase.getStatus() != GroupPurchase.Status.OPEN) {
            throw new IllegalStateException("현재 모집 중이 아닌 공동구매입니다. 상태: " + groupPurchase.getStatus());
        }

        LocalDateTime now = LocalDateTime.now();
        if (now.isBefore(groupPurchase.getStartAt()) || now.isAfter(groupPurchase.getEndAt())) {
            throw new IllegalStateException("공동구매 모집 기간이 아닙니다.");
        }

        // 8) 최대 모집 수량 초과 검증
        int nextQuantity = groupPurchase.getCurrentQuantity() + order.getQuantity();
        if (nextQuantity > groupPurchase.getMaxQuantity()) {
            throw new IllegalStateException("최대 모집 수량을 초과하여 결제할 수 없습니다. (남은 수량 초과)");
        }

        // 9) 1인당 구매 제한 수량 검증
        if (groupPurchase.getLimitPerUser() != null && order.getQuantity() > groupPurchase.getLimitPerUser()) {
            throw new IllegalArgumentException("1인당 구매 제한 수량을 초과했습니다. (제한: " + groupPurchase.getLimitPerUser() + "개)");
        }

        // 10) 주문 상태 및 결제 키 업데이트
        order.setStatus("PAID");
        order.setPaymentKey(request.getProviderPaymentKey());
        orderRepository.save(order);

        // 11) 공동구매 참여 수량 증가
        groupPurchase.addQuantity(order.getQuantity());
        groupPurchaseRepository.save(groupPurchase);

        return "결제가 성공적으로 승인되었습니다.";
    }

    // 2. 결제 취소 및 환불 로직 (Phase 10)
    @Transactional
    public String cancelPayment(Long orderId, PaymentCancelRequest request, Long currentUserId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 주문입니다: " + orderId));

        // 주문 소유권 검증
        if (!order.getUserId().equals(currentUserId)) {
            throw new IllegalArgumentException("해당 주문에 대한 접근 권한이 없습니다.");
        }

        if ("CANCELLED".equals(order.getStatus())) {
            throw new IllegalStateException("이미 취소된 주문입니다.");
        }

        // 미결제(PENDING) 상태에서의 취소는 수량 차감 없이 상태만 변경
        if ("PENDING".equals(order.getStatus())) {
            order.setStatus("CANCELLED");
            order.setCancelReason(request != null ? request.getCancelReason() : "미결제 상태 취소");
            orderRepository.save(order);
            return "미결제 주문이 취소되었습니다.";
        }

        // 결제 완료(PAID) 상태 취소 시 수량 차감 진행
        GroupPurchase groupPurchase = groupPurchaseRepository.findByIdWithLock(order.getGroupPurchaseId())
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 공동구매 캠페인입니다: " + order.getGroupPurchaseId()));

        order.setStatus("CANCELLED");
        if (request != null && request.getCancelReason() != null) {
            order.setCancelReason(request.getCancelReason());
        }
        orderRepository.save(order);

        groupPurchase.subtractQuantity(order.getQuantity());
        groupPurchaseRepository.save(groupPurchase);

        return "주문이 취소 및 환불 처리되었습니다.";
    }
}