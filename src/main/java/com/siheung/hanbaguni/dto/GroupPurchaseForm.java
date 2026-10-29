package com.siheung.hanbaguni.dto;

import com.siheung.hanbaguni.entity.GroupPurchase;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDateTime;

@Getter
@Setter
public class GroupPurchaseForm {

    @NotNull(message = "공동구매 상품을 선택해주세요.")
    private Long productId;

    @NotBlank(message = "공동구매 모집 제목을 입력해주세요.")
    @Size(max = 150, message = "제목은 150자 이하여야 합니다.")
    private String title;

    @NotNull(message = "공동구매 가격을 입력해주세요.")
    @Min(value = 0, message = "가격은 0원 이상이어야 합니다.")
    private Integer groupPrice;

    @NotNull(message = "목표 수량을 입력해주세요.")
    @Min(value = 1, message = "목표 수량은 1개 이상이어야 합니다.")
    private Integer targetQuantity = 10;

    @NotNull(message = "최대 모집 수량을 입력해주세요.")
    @Min(value = 1, message = "최대 모집 수량은 1개 이상이어야 합니다.")
    private Integer maxQuantity = 50;

    @Min(value = 1, message = "1인당 구매 제한 수량은 1개 이상이어야 합니다.")
    private Integer limitPerUser;

    @NotNull(message = "모집 시작 일시를 입력해주세요.")
    @DateTimeFormat(pattern = "yyyy-MM-dd'T'HH:mm")
    private LocalDateTime startAt;

    @NotNull(message = "모집 마감 일시를 입력해주세요.")
    @DateTimeFormat(pattern = "yyyy-MM-dd'T'HH:mm")
    private LocalDateTime endAt;

    @NotNull(message = "수령 시작 일시를 입력해주세요.")
    @DateTimeFormat(pattern = "yyyy-MM-dd'T'HH:mm")
    private LocalDateTime pickupStartAt;

    @NotNull(message = "수령 마감 일시를 입력해주세요.")
    @DateTimeFormat(pattern = "yyyy-MM-dd'T'HH:mm")
    private LocalDateTime pickupEndAt;

    @Size(max = 500, message = "수령 안내사항은 500자 이하여야 합니다.")
    private String pickupNotice;

    private String description;

    private GroupPurchase.Status status = GroupPurchase.Status.DRAFT;

    @AssertTrue(message = "최대 모집 수량은 목표 수량 이상이어야 합니다.")
    public boolean isMaxQuantityValid() {
        if (targetQuantity == null || maxQuantity == null) {
            return true;
        }
        return maxQuantity >= targetQuantity;
    }

    @AssertTrue(message = "모집 마감 일시는 모집 시작 일시 이후여야 합니다.")
    public boolean isRecruitmentPeriodValid() {
        if (startAt == null || endAt == null) {
            return true;
        }
        return endAt.isAfter(startAt);
    }

    @AssertTrue(message = "수령 마감 일시는 수령 시작 일시 이후여야 합니다.")
    public boolean isPickupPeriodValid() {
        if (pickupStartAt == null || pickupEndAt == null) {
            return true;
        }
        return pickupEndAt.isAfter(pickupStartAt);
    }

    @AssertTrue(message = "수령 가능 시작 일시는 모집 시작 일시 이후여야 합니다.")
    public boolean isPickupAfterStartValid() {
        if (startAt == null || pickupStartAt == null) {
            return true;
        }
        return !pickupStartAt.isBefore(startAt);
    }

    public static GroupPurchaseForm from(GroupPurchase gp) {
        GroupPurchaseForm form = new GroupPurchaseForm();
        form.setProductId(gp.getProduct().getId());
        form.setTitle(gp.getTitle());
        form.setGroupPrice(gp.getGroupPrice());
        form.setTargetQuantity(gp.getTargetQuantity());
        form.setMaxQuantity(gp.getMaxQuantity());
        form.setLimitPerUser(gp.getLimitPerUser());
        form.setStartAt(gp.getStartAt());
        form.setEndAt(gp.getEndAt());
        form.setPickupStartAt(gp.getPickupStartAt());
        form.setPickupEndAt(gp.getPickupEndAt());
        form.setPickupNotice(gp.getPickupNotice());
        form.setDescription(gp.getDescription());
        form.setStatus(gp.getStatus());
        return form;
    }
}
