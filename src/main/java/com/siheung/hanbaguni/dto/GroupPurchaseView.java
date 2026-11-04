package com.siheung.hanbaguni.dto;

import com.siheung.hanbaguni.entity.GroupPurchase;
import java.time.LocalDateTime;

/** Customer-facing data only; never expose the seller's User entity to the template. */
public record GroupPurchaseView(
        Long id, String title, String productName, String category, String unit,
        String storeName, String address, String imageUrl, int groupPrice,
        int targetQuantity, int maxQuantity, int currentQuantity,
        LocalDateTime startAt, LocalDateTime endAt,
        LocalDateTime pickupStartAt, LocalDateTime pickupEndAt,
        String pickupNotice, String description) {

    public static GroupPurchaseView from(GroupPurchase gp) {
        var product = gp.getProduct();
        var store = gp.getStore();
        String address = store.getAddress();
        if (store.getAddressDetail() != null && !store.getAddressDetail().isBlank()) {
            address += " " + store.getAddressDetail();
        }
        String description = gp.getDescription();
        if (description == null || description.isBlank()) {
            description = product.getDescription();
        }
        return new GroupPurchaseView(gp.getId(), gp.getTitle(), product.getName(),
                product.getCategory(), product.getUnit(), store.getName(), address,
                product.getImageUrl(), gp.getGroupPrice(),
                gp.getTargetQuantity(), gp.getMaxQuantity(), gp.getCurrentQuantity(),
                gp.getStartAt(), gp.getEndAt(), gp.getPickupStartAt(), gp.getPickupEndAt(),
                gp.getPickupNotice(), description);
    }

    public int getProgressPercent() {
        if (targetQuantity <= 0) return 0;
        return (int) Math.max(0, Math.min(100, (long) currentQuantity * 100 / targetQuantity));
    }
}
