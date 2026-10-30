package com.siheung.hanbaguni.controller;

import com.siheung.hanbaguni.dto.PaymentCancelRequest;
import com.siheung.hanbaguni.dto.PaymentConfirmRequest;
import com.siheung.hanbaguni.security.LoginUser;
import com.siheung.hanbaguni.service.PaymentService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
public class PaymentController {

    private final PaymentService paymentService;

    public PaymentController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    // 1. 선결제 확정 API 엔드포인트 (Phase 9 - 소유권 검증을 위한 LoginUser 추가)
    @PostMapping("/orders/{orderId}/payments/confirm")
    public ResponseEntity<String> confirmPayment(
            @PathVariable Long orderId,
            @RequestBody PaymentConfirmRequest request,
            @AuthenticationPrincipal LoginUser loginUser) {

        String resultMessage = paymentService.confirmPayment(orderId, request, loginUser.getUserId());
        return ResponseEntity.ok(resultMessage);
    }

    // 2. 결제 취소 및 환불 API 엔드포인트 (Phase 10)
    @PostMapping("/orders/{orderId}/payments/cancel")
    public ResponseEntity<String> cancelPayment(
            @PathVariable Long orderId,
            @RequestBody PaymentCancelRequest request,
            @AuthenticationPrincipal LoginUser loginUser) {

        String resultMessage = paymentService.cancelPayment(orderId, request, loginUser.getUserId());
        return ResponseEntity.ok(resultMessage);
    }
}