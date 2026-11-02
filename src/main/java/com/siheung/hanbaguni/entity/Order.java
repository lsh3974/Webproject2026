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

    @Column(name = "payment_order_ref", length = 64, unique = true)
    private String paymentOrderRef;

    @Column(name = "payment_rejection_code", length = 100)
    private String paymentRejectionCode;

    @Version
    @Column(nullable = false)
    private Long version;

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

    public void beginPayment(LocalDateTime now) {
        if (this.status != Status.PENDING) {
            throw new IllegalStateException(
                    "결제 대기 상태의 주문만 승인을 시작할 수 있습니다."
            );
        }

        if (this.reservedUntil == null || isExpiredAt(now)) {
            throw new IllegalStateException(
                    "예약 시간이 만료되어 결제를 진행할 수 없습니다."
            );
        }

        this.status = Status.PAYMENT_PENDING;
    }

    public void markPaid(String paymentKey) {
        if (status != Status.PAYMENT_PENDING) {
            throw new IllegalStateException(
                    "승인 중인 주문만 결제 완료 처리할 수 있습니다."
            );
        }

        bindPaymentKey(paymentKey);
        status = Status.PAID;
    }


    public void assignPaymentOrderRef(String paymentOrderRef) {
        if (this.paymentOrderRef == null) {
            this.paymentOrderRef = paymentOrderRef;
        }
    }

    public void bindPaymentKey(String paymentKey) {
        if (paymentKey == null || paymentKey.isBlank()) {
            throw new IllegalArgumentException("결제 키가 필요합니다.");
        }

        if (this.paymentKey != null
                && !this.paymentKey.equals(paymentKey)) {
            throw new IllegalStateException(
                    "이미 다른 결제 요청이 진행 중입니다."
            );
        }

        this.paymentKey = paymentKey;
    }

    public void failPayment() {
        if (status != Status.PAYMENT_PENDING) {
            throw new IllegalStateException(
                    "승인 중인 주문만 결제 실패 처리할 수 있습니다."
            );
        }

        status = Status.EXPIRED;
        cancelReason = "결제 승인 실패 또는 결제 유효 시간 만료";
    }
    public void recordPaymentRejection(String code) {
        if (status != Status.PAYMENT_PENDING) {
            throw new IllegalStateException(
                    "결제 승인 확인 중인 주문에만 오류를 기록할 수 있습니다."
            );
        }

        if (code == null || code.isBlank() || code.length() > 100) {
            throw new IllegalArgumentException(
                    "올바른 결제 오류 코드가 필요합니다."
            );
        }

        this.paymentRejectionCode = code;
    }

    public enum Status {
        PENDING,
        PAYMENT_PENDING,
        PAID,
        CANCELLED,
        REFUNDED,
        EXPIRED
    }
}
