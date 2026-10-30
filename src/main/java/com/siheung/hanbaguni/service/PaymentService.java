package com.siheung.hanbaguni.service;

import com.siheung.hanbaguni.dto.PaymentCancelRequest;
import com.siheung.hanbaguni.dto.PaymentConfirmRequest;
import com.siheung.hanbaguni.entity.GroupPurchase;
import com.siheung.hanbaguni.entity.Order;
import com.siheung.hanbaguni.repository.GroupPurchaseRepository;
import com.siheung.hanbaguni.repository.OrderRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PaymentService {

    private final OrderRepository orderRepository;
    private final GroupPurchaseRepository groupPurchaseRepository;

    public PaymentService(OrderRepository orderRepository, GroupPurchaseRepository groupPurchaseRepository) {
        this.orderRepository = orderRepository;
        this.groupPurchaseRepository = groupPurchaseRepository;
    }

    // 1. 선결제 승인 로직 (Phase 9)
    @Transactional
    public String confirmPayment(Long orderId, PaymentConfirmRequest request) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 주문입니다: " + orderId));

        if ("PAID".equals(order.getStatus())) {
            throw new IllegalStateException("이미 결제 완료된 주문입니다.");
        }

        GroupPurchase groupPurchase = groupPurchaseRepository.findById(order.getGroupPurchaseId())
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 공동구매 캠페인입니다: " + order.getGroupPurchaseId()));

        // 결제 금액 일치 여부 검증 (주문서의 totalPrice와 요청된 amountKrw 비교)
        if (request.getAmountKrw() != null && !request.getAmountKrw().equals(order.getTotalPrice().longValue())) {
            throw new IllegalArgumentException("결제 금액이 주문 금액과 일치하지 않습니다.");
        }

        // 주문 상태 및 결제 키 업데이트
        order.setStatus("PAID");
        if (request.getProviderPaymentKey() != null) {
            order.setPaymentKey(request.getProviderPaymentKey());
        }
        orderRepository.save(order);

        // 공동구매 참여 수량(current_quantity) 증가
        groupPurchase.addQuantity(order.getQuantity());
        groupPurchaseRepository.save(groupPurchase);

        return "결제가 성공적으로 승인되었습니다.";
    }

    // 2. 결제 취소 및 환불 로직 (Phase 10)
    @Transactional
    public String cancelPayment(Long orderId, PaymentCancelRequest request) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 주문입니다: " + orderId));

        if ("CANCELLED".equals(order.getStatus())) {
            throw new IllegalStateException("이미 취소된 주문입니다.");
        }

        GroupPurchase groupPurchase = groupPurchaseRepository.findById(order.getGroupPurchaseId())
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 공동구매 캠페인입니다: " + order.getGroupPurchaseId()));

        // 주문 상태 및 취소 사유 업데이트
        order.setStatus("CANCELLED");
        if (request.getCancelReason() != null) {
            order.setCancelReason(request.getCancelReason());
        }
        orderRepository.save(order);

        // 공동구매 참여 수량(current_quantity) 원복
        groupPurchase.subtractQuantity(order.getQuantity());
        groupPurchaseRepository.save(groupPurchase);

        return "주문이 취소 및 환불 처리되었습니다. 사유: " + request.getCancelReason();
    }
}