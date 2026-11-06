package com.siheung.hanbaguni.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "pickups")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Pickup {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", unique = true)
    private Order order;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "store_id", nullable = false)
    private Store store;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "group_purchase_id", nullable = false)
    private GroupPurchase groupPurchase;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User customer;

    @Column(name = "pickup_code", nullable = false, unique = true, length = 32)
    private String pickupCode;

    @Column(nullable = false)
    private Integer quantity = 1;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Status status = Status.PREPARING;

    @Column(name = "picked_up_at")
    private LocalDateTime pickedUpAt;

    @Column(name = "customer_name", length = 50)
    private String customerName;

    @Column(name = "customer_phone", length = 30)
    private String customerPhone;

    @Column(name = "created_at", insertable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", insertable = false, updatable = false)
    private LocalDateTime updatedAt;

    public Pickup(
            Store store,
            GroupPurchase groupPurchase,
            User customer,
            String pickupCode,
            Integer quantity,
            String customerName,
            String customerPhone) {
        this(null, store, groupPurchase, customer, pickupCode, quantity, customerName, customerPhone);
    }

    public Pickup(
            Order order,
            Store store,
            GroupPurchase groupPurchase,
            User customer,
            String pickupCode,
            Integer quantity,
            String customerName,
            String customerPhone) {
        this.order = order;
        this.store = store;
        this.groupPurchase = groupPurchase;
        this.customer = customer;
        this.pickupCode = pickupCode;
        this.quantity = quantity != null ? quantity : 1;
        this.customerName = customerName;
        this.customerPhone = customerPhone;
        this.status = Status.PREPARING;
    }

    public void markReady() {
        if (this.order != null && this.order.getStatus() != Order.Status.PAID) {
            throw new IllegalStateException("결제 완료(PAID) 상태인 주문만 준비완료로 변경할 수 있습니다.");
        }
        validateGroupStatus();
        if (this.status == Status.PICKED_UP) {
            throw new IllegalStateException("이미 수령 완료된 건은 준비완료로 되돌릴 수 없습니다.");
        }
        if (this.status == Status.CANCELLED) {
            throw new IllegalStateException("취소된 건은 준비완료로 변경할 수 없습니다.");
        }
        this.status = Status.READY;
    }

    public void markPickedUp() {
        if (this.order != null && this.order.getStatus() != Order.Status.PAID) {
            throw new IllegalStateException("결제 완료(PAID) 상태인 주문만 수령 처리할 수 있습니다. (현재 주문 상태: " + this.order.getStatus() + ")");
        }
        validateGroupStatus();
        if (this.status == Status.PICKED_UP) {
            throw new IllegalStateException("이미 수령이 완료된 건입니다.");
        }
        if (this.status == Status.CANCELLED) {
            throw new IllegalStateException("취소된 건은 수령할 수 없습니다.");
        }
        if (this.status != Status.READY) {
            throw new IllegalStateException("준비완료된 건만 수령할 수 있습니다.");
        }
        this.status = Status.PICKED_UP;
        this.pickedUpAt = LocalDateTime.now();
    }

    public boolean canMarkReady() {
        return status == Status.PREPARING && isEligibleForPickup();
    }

    public boolean canComplete() {
        return status == Status.READY && isEligibleForPickup();
    }

    private boolean isEligibleForPickup() {
        return order == null ? groupPurchase.getStatus() != GroupPurchase.Status.FAILED
                : order.getStatus() == Order.Status.PAID && groupPurchase.getStatus() == GroupPurchase.Status.SUCCESS;
    }

    private void validateGroupStatus() {
        if (order != null && groupPurchase.getStatus() != GroupPurchase.Status.SUCCESS) {
            throw new IllegalStateException("모집에 성공한 공동구매만 수령을 진행할 수 있습니다.");
        }
        if (groupPurchase.getStatus() == GroupPurchase.Status.FAILED) {
            throw new IllegalStateException("모집 실패한 공동구매는 수령을 진행할 수 없습니다.");
        }
    }

    public void cancel() {
        if (this.status == Status.PICKED_UP) {
            throw new IllegalStateException("이미 수령 완료된 건은 취소할 수 없습니다.");
        }
        this.status = Status.CANCELLED;
    }

    public enum Status {
        PREPARING, READY, PICKED_UP, CANCELLED
    }
}
