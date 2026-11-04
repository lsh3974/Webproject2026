package com.siheung.hanbaguni.payment;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

public final class RefundData {

    private RefundData() {
    }

    // 서버 DB에서 읽어 구성할 환불 요청 정보입니다.
    public record Attempt(
            Long orderId,
            Long userId,
            String orderRef,
            String paymentKey,
            int amount,
            String refundRequestId,
            String reason,
            boolean refunded
    ) {
    }

    // Toss의 취소 응답 또는 결제 조회 응답입니다.
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Receipt(
            String paymentKey,
            String orderId,
            Long totalAmount,
            Long balanceAmount,
            String currency,
            String status
    ) {
    }

    public record Result(
            String status,
            String message
    ) {
    }
}