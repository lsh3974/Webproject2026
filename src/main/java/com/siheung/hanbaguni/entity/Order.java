package com.siheung.hanbaguni.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "orders")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Order {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "group_purchase_id", nullable = false)
    private GroupPurchase groupPurchase;

    @Column(nullable = false)
    private Integer quantity;

    @Column(name = "unit_price", nullable = false)
    private Integer unitPrice;

    @Column(name = "total_price", nullable = false)
    private Integer totalPrice;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private Status status = Status.PENDING;

    @Column(name = "reserved_until")
    private LocalDateTime reservedUntil;

    @Column(name = "payment_key", length = 255)
    private String paymentKey;

    @Column(name = "cancel_reason", length = 255)
    private String cancelReason;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", insertable = false, updatable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        if (this.createdAt == null) {
            this.createdAt = LocalDateTime.now();
        }
    }

    public Order(
            User user,
            GroupPurchase groupPurchase,
            Integer quantity,
            Integer unitPrice,
            Integer totalPrice,
            LocalDateTime reservedUntil) {
        this.user = user;
        this.groupPurchase = groupPurchase;
        this.quantity = quantity;
        this.unitPrice = unitPrice;
        this.totalPrice = totalPrice;
        this.status = Status.PENDING;
        this.reservedUntil = reservedUntil;
    }

    public Long getUserId() {
        return user != null ? user.getId() : null;
    }

    public Long getGroupPurchaseId() {
        return groupPurchase != null ? groupPurchase.getId() : null;
    }

    public boolean isOwnedBy(Long userId) {
        return this.user != null && this.user.getId() != null && this.user.getId().equals(userId);
    }

    public boolean isExpiredAt(LocalDateTime now) {
        return this.status == Status.PENDING
                && this.reservedUntil != null
                && !this.reservedUntil.isAfter(now);
    }

    public void cancelReservation(String reason) {
        this.status = Status.CANCELLED;
        this.cancelReason = (reason != null && !reason.isBlank())
                ? reason.trim()
                : "고객 요청에 의한 수량 예약 취소";
    }

    public void expireReservation() {
        this.status = Status.EXPIRED;
        if (this.cancelReason == null || this.cancelReason.isBlank()) {
            this.cancelReason = "선결제 예약 시간 만료";
        }
    }

    public void markPaid(String paymentKey) {
        this.status = Status.PAID;
        if (paymentKey != null && !paymentKey.isBlank()) {
            this.paymentKey = paymentKey.trim();
        }
    }

    public enum Status {
        PENDING,
        PAID,
        CANCELLED,
        REFUNDED,
        EXPIRED
    }
}
