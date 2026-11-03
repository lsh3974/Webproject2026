package com.siheung.hanbaguni.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class OrderCreateRequest {

    @NotNull(message = "groupPurchaseId는 필수입니다.")
    private Long groupPurchaseId;

    @NotNull(message = "주문 수량(quantity)은 필수입니다.")
    @Min(value = 1, message = "주문 수량은 1개 이상이어야 합니다.")
    private Integer quantity;
}
