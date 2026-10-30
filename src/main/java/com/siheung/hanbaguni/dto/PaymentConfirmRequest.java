package com.siheung.hanbaguni.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class PaymentConfirmRequest {

    private String provider;           // 결제 대행사 (예: TOSS_PAYMENTS)[cite: 4]
    private String providerPaymentKey; // 토스가 발급해 준 결제 승인 키[cite: 4]
    private String providerOrderRef;   // 주문 참조 번호[cite: 4]
    private Long amountKrw;            // 결제할 총 금액 (원 단위 정수)[cite: 4]
}