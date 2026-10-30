package com.siheung.hanbaguni.dto;

import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class OrderCancelRequest {

    @Size(max = 255, message = "취소 사유는 255자 이내로 입력해주세요.")
    private String cancelReason;
}
