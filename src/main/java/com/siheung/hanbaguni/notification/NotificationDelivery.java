package com.siheung.hanbaguni.notification;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

@Entity
@Table(name = "notification_deliveries")
@Getter
@NoArgsConstructor
public class NotificationDelivery {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "notification_id", nullable = false) private Notification notification;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 12) private Channel channel;
    @Column(name = "target_key", nullable = false, length = 100) private String targetKey = "default";
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "push_subscription_id") private PushSubscription pushSubscription;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20) private DeliveryStatus status = DeliveryStatus.PENDING;
    @Column(name = "attempt_count", nullable = false) private int attemptCount;
    @Column(name = "next_attempt_at", nullable = false) private LocalDateTime nextAttemptAt = LocalDateTime.now();
    @Column(name = "locked_until") private LocalDateTime lockedUntil;
    @Column(name = "provider_message_id", length = 160) private String providerMessageId;
    @Column(name = "last_error", length = 1000) private String lastError;
    @Column(name = "sent_at") private LocalDateTime sentAt;
    @Column(name = "created_at", nullable = false, updatable = false) private LocalDateTime createdAt;
    @Column(name = "reconciliation_count", nullable = false) private int reconciliationCount;

    public NotificationDelivery(Notification notification, Channel channel, String targetKey, PushSubscription subscription) {
        this.notification = notification; this.channel = channel; this.targetKey = targetKey;
        this.pushSubscription = subscription; this.status = DeliveryStatus.PENDING;
        this.nextAttemptAt = LocalDateTime.now();
        this.createdAt = LocalDateTime.now();
    }
    public void claim(LocalDateTime now) { status = DeliveryStatus.PROCESSING; attemptCount++; lockedUntil = now.plusMinutes(2); }
    public void sent(String providerId) { status = DeliveryStatus.SENT; providerMessageId = providerId; sentAt = LocalDateTime.now(); lockedUntil = null; lastError = null; }
    public void retry(String error, LocalDateTime next) { status = DeliveryStatus.RETRY; lastError = safe(error); nextAttemptAt = next; lockedUntil = null; }
    public void fail(String error) { status = DeliveryStatus.FAILED; lastError = safe(error); lockedUntil = null; }
    public void unknown(String error) { status = DeliveryStatus.UNKNOWN; lastError = safe(error); lockedUntil = LocalDateTime.now().plusMinutes(1); }
    public void claimReconciliation(LocalDateTime now) { reconciliationCount++; lockedUntil = now.plusMinutes(2); }
    public void deferReconciliation(String error) {
        status = DeliveryStatus.UNKNOWN;
        lastError = safe(error);
        lockedUntil = reconciliationCount >= 3 ? LocalDateTime.of(9999, 12, 31, 23, 59) : LocalDateTime.now().plusMinutes(2);
    }
    public void removeSubscriptionTarget() { status = DeliveryStatus.FAILED; lastError = "브라우저 구독이 만료되었거나 해지되었습니다."; lockedUntil = null; }
    public void skip(String reason) { status = DeliveryStatus.SKIPPED; lastError = safe(reason); lockedUntil = null; }
    private String safe(String value) { if (value == null) return "알 수 없는 오류"; return value.length() > 1000 ? value.substring(0, 1000) : value; }
    public enum Channel { EMAIL, SMS, PUSH }
    public enum DeliveryStatus { PENDING, PROCESSING, RETRY, SENT, FAILED, UNKNOWN, SKIPPED }
}
