package com.siheung.hanbaguni.payment;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

public final class PaymentData {

    private PaymentData() {
    }

    public record Prepared(
            Long orderId,
            String orderRef,
            int amount,
            String orderName
    ) {
    }

    public record ConfirmRequest(
            String paymentKey,
            String orderId,
            Long amount
    ) {
    }

    public record Attempt(
            Long orderId,
            Long userId,
            String orderRef,
            String paymentKey,
            int amount,
            boolean paid
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Receipt(
            String paymentKey,
            String orderId,
            long totalAmount,
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