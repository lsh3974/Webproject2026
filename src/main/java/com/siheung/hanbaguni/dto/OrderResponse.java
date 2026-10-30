package com.siheung.hanbaguni.dto;

import com.siheung.hanbaguni.entity.Order;

import java.time.LocalDateTime;

public record OrderResponse(
        Long orderId,
        Long groupPurchaseId,
        String groupPurchaseTitle,
        String productName,
        String unit,
        String storeName,
        String storeAddress,
        Integer quantity,
        Integer unitPrice,
        Integer totalPrice,
        String status,
        LocalDateTime reservedUntil,
        String cancelReason,
        LocalDateTime createdAt,
        LocalDateTime pickupStartAt,
        LocalDateTime pickupEndAt
) {
    public static OrderResponse from(Order order) {
        var gp = order.getGroupPurchase();
        var product = gp.getProduct();
        var store = gp.getStore();

        String fullAddress = store.getAddress();
        if (store.getAddressDetail() != null && !store.getAddressDetail().isBlank()) {
            fullAddress += " " + store.getAddressDetail();
        }

        return new OrderResponse(
                order.getId(),
                gp.getId(),
                gp.getTitle(),
                product.getName(),
                product.getUnit(),
                store.getName(),
                fullAddress,
                order.getQuantity(),
                order.getUnitPrice(),
                order.getTotalPrice(),
                order.getStatus().name(),
                order.getReservedUntil(),
                order.getCancelReason(),
                order.getCreatedAt(),
                gp.getPickupStartAt(),
                gp.getPickupEndAt()
        );
    }
}
