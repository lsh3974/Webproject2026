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

    @Column(name = "refund_request_id", length = 64, unique = true)
    private String refundRequestId;

    @Column(name = "payment_retry_at")
    private LocalDateTime paymentRetryAt;

    public void deferPaymentRecovery(LocalDateTime now) {
        paymentRetryAt = now.plusMinutes(1);
    }

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


    // 결제창을 다시 열어도 최초 유예 종료 시각을 유지한다.
    public void prepareCheckout(String ref, LocalDateTime now) {
        if (status != Status.PENDING || reservedUntil == null || !reservedUntil.isAfter(now)
                || !groupPurchase.getEndAt().isAfter(now)) {
            throw new IllegalStateException("예약 또는 모집 시간이 만료되었습니다.");
        }
        if (paymentOrderRef != null) {
            return;
        }
        LocalDateTime checkoutUntil = now.plusMinutes(5);
        if (checkoutUntil.isAfter(groupPurchase.getEndAt())) {
            checkoutUntil = groupPurchase.getEndAt();
        }
        if (checkoutUntil.isAfter(reservedUntil)) {
            reservedUntil = checkoutUntil;
        }
        assignPaymentOrderRef(ref);
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

    public void expireForGroupSuccess() {
        if (status != Status.PENDING) {
            throw new IllegalStateException(
                    "승인을 시작하지 않은 예약만 모집 성공으로 만료할 수 있습니다."
            );
        }

        status = Status.EXPIRED;
        cancelReason = "공동구매 목표 달성으로 모집이 종료되었습니다.";
    }

    public boolean beginRefund(String requestId, String reason) {
        if (requestId == null
                || requestId.isBlank()
                || requestId.length() > 64) {
            throw new IllegalArgumentException(
                    "올바른 환불 요청 번호가 필요합니다."
            );
        }

        if (reason == null
                || reason.isBlank()
                || reason.strip().length() > 200) {
            throw new IllegalArgumentException(
                    "환불 사유를 1~200자로 입력해주세요."
            );
        }

        if (status == Status.REFUND_PENDING
                || status == Status.REFUNDED) {
            if (!requestId.equals(refundRequestId)) {
                throw new IllegalStateException(
                        "이미 다른 환불 요청이 등록되어 있습니다."
                );
            }

            return false;
        }

        if (status != Status.PAID) {
            throw new IllegalStateException(
                    "결제 완료 주문만 환불을 시작할 수 있습니다."
            );
        }

        if (paymentKey == null || paymentKey.isBlank()) {
            throw new IllegalStateException(
                    "환불할 결제 정보가 없습니다."
            );
        }

        refundRequestId = requestId;
        cancelReason = reason.strip();
        status = Status.REFUND_PENDING;

        return true;
    }

    public boolean completeRefund(String requestId) {
        if (requestId == null
                || !requestId.equals(refundRequestId)) {
            throw new IllegalStateException(
                    "환불 요청 번호가 일치하지 않습니다."
            );
        }

        if (status == Status.REFUNDED) {
            return false;
        }

        if (status != Status.REFUND_PENDING) {
            throw new IllegalStateException(
                    "환불 처리 중인 주문만 환불 완료로 변경할 수 있습니다."
            );
        }

        status = Status.REFUNDED;
        return true;
    }

    public enum Status {
        PENDING,
        PAYMENT_PENDING,
        PAID,
        REFUND_PENDING,
        CANCELLED,
        REFUNDED,
        EXPIRED
    }
}
