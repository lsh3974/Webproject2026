package com.siheung.hanbaguni.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "group_purchases")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class GroupPurchase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "store_id", nullable = false)
    private Store store;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @Column(nullable = false, length = 150)
    private String title;

    @Column(name = "group_price", nullable = false)
    private Integer groupPrice;

    @Column(name = "target_quantity", nullable = false)
    private Integer targetQuantity;

    @Column(name = "max_quantity", nullable = false)
    private Integer maxQuantity;

    @Column(name = "current_quantity", nullable = false)
    private Integer currentQuantity = 0;

    @Column(name = "limit_per_user")
    private Integer limitPerUser;

    @Column(name = "start_at", nullable = false)
    private LocalDateTime startAt;

    @Column(name = "end_at", nullable = false)
    private LocalDateTime endAt;

    @Column(name = "pickup_start_at", nullable = false)
    private LocalDateTime pickupStartAt;

    @Column(name = "pickup_end_at", nullable = false)
    private LocalDateTime pickupEndAt;

    @Column(name = "pickup_notice", length = 500)
    private String pickupNotice;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Status status = Status.DRAFT;

    @Column(name = "created_at", insertable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", insertable = false, updatable = false)
    private LocalDateTime updatedAt;

    public GroupPurchase(
            Store store,
            Product product,
            String title,
            Integer groupPrice,
            Integer targetQuantity,
            Integer maxQuantity,
            Integer limitPerUser,
            LocalDateTime startAt,
            LocalDateTime endAt,
            LocalDateTime pickupStartAt,
            LocalDateTime pickupEndAt,
            String pickupNotice,
            String description,
            Status status) {
        this.store = store;
        this.product = product;
        this.title = title;
        this.groupPrice = groupPrice;
        this.targetQuantity = targetQuantity;
        this.maxQuantity = maxQuantity;
        this.currentQuantity = 0;
        this.limitPerUser = limitPerUser;
        this.startAt = startAt;
        this.endAt = endAt;
        this.pickupStartAt = pickupStartAt;
        this.pickupEndAt = pickupEndAt;
        this.pickupNotice = pickupNotice;
        this.description = description;
        this.status = status != null ? status : Status.DRAFT;
    }

    public void update(
            String title,
            Integer groupPrice,
            Integer targetQuantity,
            Integer maxQuantity,
            Integer limitPerUser,
            LocalDateTime startAt,
            LocalDateTime endAt,
            LocalDateTime pickupStartAt,
            LocalDateTime pickupEndAt,
            String pickupNotice,
            String description,
            Status status) {
        this.title = title;
        this.groupPrice = groupPrice;
        this.targetQuantity = targetQuantity;
        this.maxQuantity = maxQuantity;
        this.limitPerUser = limitPerUser;
        this.startAt = startAt;
        this.endAt = endAt;
        this.pickupStartAt = pickupStartAt;
        this.pickupEndAt = pickupEndAt;
        this.pickupNotice = pickupNotice;
        this.description = description;
        if (status != null) {
            this.status = status;
        }
    }

    public void publish() {
        this.status = Status.OPEN;
    }

    public void updateStatus(Status status) {
        if (status != null) {
            this.status = status;
        }
    }

    // 결제 승인 시 참여 수량 증가
    public void addQuantity(int quantity) {
        this.currentQuantity += quantity;
    }

    // 결제 취소 시 참여 수량 원복 (마이너스 방지)
    public void subtractQuantity(int quantity) {
        this.currentQuantity = Math.max(0, this.currentQuantity - quantity);
    }

    public enum Status {
        DRAFT, OPEN, SUCCESS, FAILED, CLOSED
    }
}
