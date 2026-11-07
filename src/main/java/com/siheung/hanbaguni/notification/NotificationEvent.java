package com.siheung.hanbaguni.notification;

import com.siheung.hanbaguni.entity.User;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

@Entity
@Table(name = "notification_events")
@Getter
@NoArgsConstructor
public class NotificationEvent {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "event_key", nullable = false, unique = true, length = 191)
    private String eventKey;
    @Enumerated(EnumType.STRING) @Column(name = "event_type", nullable = false, length = 40)
    private NotificationType eventType;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "recipient_user_id", nullable = false)
    private User recipient;
    @Column(name = "order_id") private Long orderId;
    @Column(name = "group_purchase_id") private Long groupPurchaseId;
    @Column(nullable = false, length = 160) private String title;
    @Column(nullable = false, length = 1000) private String body;
    @Column(name = "target_url", nullable = false, length = 500) private String targetUrl;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20)
    private EventStatus status = EventStatus.PENDING;
    @Column(name = "attempt_count", nullable = false) private int attemptCount;
    @Column(name = "next_attempt_at", nullable = false) private LocalDateTime nextAttemptAt = LocalDateTime.now();
    @Column(name = "locked_until") private LocalDateTime lockedUntil;
    @Column(name = "last_error", length = 1000) private String lastError;
    @Column(name = "processed_at") private LocalDateTime processedAt;
    @Column(name = "created_at", nullable = false, updatable = false) private LocalDateTime createdAt;

    public NotificationEvent(String eventKey, NotificationType eventType, User recipient, Long orderId,
                             Long groupPurchaseId, String title, String body, String targetUrl) {
        this.eventKey = eventKey;
        this.eventType = eventType;
        this.recipient = recipient;
        this.orderId = orderId;
        this.groupPurchaseId = groupPurchaseId;
        this.title = title;
        this.body = body;
        this.targetUrl = targetUrl;
        this.status = EventStatus.PENDING;
        this.nextAttemptAt = LocalDateTime.now();
        this.createdAt = LocalDateTime.now();
    }

    public void markProcessed() {
        this.status = EventStatus.PROCESSED;
        this.processedAt = LocalDateTime.now();
        this.lockedUntil = null;
        this.lastError = null;
    }

    public enum EventStatus { PENDING, PROCESSING, PROCESSED, FAILED }
}
