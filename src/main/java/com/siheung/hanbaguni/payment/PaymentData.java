package com.siheung.hanbaguni.payment;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

public final class PaymentData {

    private PaymentData() {
    }

    // 결제창에 전달할 준비 정보
    public record Prepared(
            Long orderId,
            String orderRef,
            int amount,
            String orderName
    ) {
    }

    // 브라우저에서 서버로 전달하는 승인 요청
    public record ConfirmRequest(
            String paymentKey,
            String orderId,
            Long amount
    ) {
    }

    // 서버 내부의 승인·재확인 처리 정보
    public record Attempt(
            Long orderId,
            Long userId,
            String orderRef,
            String paymentKey,
            int amount,
            boolean paid,
            boolean confirmationAllowed,
            String rejectionCode
    ) {
    }

    // Toss가 반환하는 결제 정보
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Receipt(
            String paymentKey,
            String orderId,
            long totalAmount,
            String currency,
            String status
    ) {
    }

    // 화면에 전달할 처리 결과
    public record Result(
            String status,
            String message
    ) {
    }
}