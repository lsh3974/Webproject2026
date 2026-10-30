package com.siheung.hanbaguni.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class PaymentCancelRequest {

    private Long cancelAmountKrw; // 환불 요청 금액
    private String cancelReason;  // 취소 및 환불 사유
}