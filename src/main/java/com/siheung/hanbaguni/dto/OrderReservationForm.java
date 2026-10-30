package com.siheung.hanbaguni.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class OrderReservationForm {

    @NotNull(message = "공동구매 모집 정보를 선택해주세요.")
    private Long groupPurchaseId;

    @NotNull(message = "주문 수량을 입력해주세요.")
    @Min(value = 1, message = "주문 수량은 1개 이상이어야 합니다.")
    private Integer quantity = 1;
}
