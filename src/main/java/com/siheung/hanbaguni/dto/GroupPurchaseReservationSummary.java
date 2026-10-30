package com.siheung.hanbaguni.dto;

import com.siheung.hanbaguni.entity.GroupPurchase;

import java.time.LocalDateTime;

public record GroupPurchaseReservationSummary(
        Long groupPurchaseId,
        String title,
        String productName,
        String productImageUrl,
        String category,
        String unit,
        Integer regularPrice,
        Integer groupPrice,
        String storeName,
        String storeAddress,
        String storePhone,
        Integer targetQuantity,
        Integer maxQuantity,
        Integer confirmedQuantity,
        Integer reservedPendingQuantity,
        Integer occupiedQuantity,
        Integer remainingQuantity,
        Integer limitPerUser,
        Integer userActiveQuantity,
        Integer userOrderableMaxQuantity,
        LocalDateTime startAt,
        LocalDateTime endAt,
        LocalDateTime pickupStartAt,
        LocalDateTime pickupEndAt,
        String pickupNotice,
        String description,
        boolean orderable,
        String unavailableReason
) {
    public static GroupPurchaseReservationSummary of(
            GroupPurchase gp,
            int confirmedQuantity,
            int reservedPendingQuantity,
            int userActiveQuantity,
            boolean orderable,
            String unavailableReason) {

        var product = gp.getProduct();
        var store = gp.getStore();

        String fullAddress = store.getAddress();
        if (store.getAddressDetail() != null && !store.getAddressDetail().isBlank()) {
            fullAddress += " " + store.getAddressDetail();
        }

        String desc = gp.getDescription();
        if (desc == null || desc.isBlank()) {
            desc = product.getDescription();
        }

        int occupied = confirmedQuantity + reservedPendingQuantity;
        int remaining = Math.max(0, gp.getMaxQuantity() - occupied);

        int userMax = remaining;
        if (gp.getLimitPerUser() != null) {
            int leftForUser = Math.max(0, gp.getLimitPerUser() - userActiveQuantity);
            userMax = Math.min(remaining, leftForUser);
        }

        boolean canOrder = orderable && userMax >= 1;
        String reason = unavailableReason;
        if (reason == null && !canOrder) {
            if (remaining <= 0) {
                reason = "현재 모집 가능한 모든 수량이 선점되어 예약이 마감되었습니다.";
            } else if (gp.getLimitPerUser() != null && userActiveQuantity >= gp.getLimitPerUser()) {
                reason = "1인당 구매 제한 수량(" + gp.getLimitPerUser() + product.getUnit() + ")을 이미 모두 예약하셨습니다.";
            }
        }

        return new GroupPurchaseReservationSummary(
                gp.getId(),
                gp.getTitle(),
                product.getName(),
                product.getImageUrl(),
                product.getCategory(),
                product.getUnit(),
                product.getRegularPrice(),
                gp.getGroupPrice(),
                store.getName(),
                fullAddress,
                store.getPhone(),
                gp.getTargetQuantity(),
                gp.getMaxQuantity(),
                confirmedQuantity,
                reservedPendingQuantity,
                occupied,
                remaining,
                gp.getLimitPerUser(),
                userActiveQuantity,
                userMax,
                gp.getStartAt(),
                gp.getEndAt(),
                gp.getPickupStartAt(),
                gp.getPickupEndAt(),
                gp.getPickupNotice(),
                desc,
                canOrder,
                reason
        );
    }

    public int progressPercent() {
        if (targetQuantity == null || targetQuantity <= 0) {
            return 0;
        }
        return (int) Math.max(0, Math.min(100, (long) occupiedQuantity * 100 / targetQuantity));
    }
}
