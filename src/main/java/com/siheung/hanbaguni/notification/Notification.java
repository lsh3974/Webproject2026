package com.siheung.hanbaguni.notification;

import com.siheung.hanbaguni.entity.User;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

@Entity
@Table(name = "notifications")
@Getter
@NoArgsConstructor
public class Notification {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @OneToOne(fetch = FetchType.LAZY) @JoinColumn(name = "event_id", nullable = false, unique = true)
    private NotificationEvent event;
    @Column(name = "event_key", nullable = false, unique = true, length = 191) private String eventKey;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "user_id", nullable = false) private User user;
    @Enumerated(EnumType.STRING) @Column(name = "event_type", nullable = false, length = 40) private NotificationType eventType;
    @Column(nullable = false, length = 160) private String title;
    @Column(nullable = false, length = 1000) private String body;
    @Column(name = "target_url", nullable = false, length = 500) private String targetUrl;
    @Column(name = "created_at", nullable = false, updatable = false) private LocalDateTime createdAt;
    @Column(name = "read_at") private LocalDateTime readAt;

    public Notification(NotificationEvent event) {
        this.event = event;
        this.eventKey = event.getEventKey();
        this.user = event.getRecipient();
        this.eventType = event.getEventType();
        this.title = event.getTitle();
        this.body = event.getBody();
        this.targetUrl = event.getTargetUrl();
        this.createdAt = LocalDateTime.now();
    }

    public boolean markRead() {
        if (readAt != null) return false;
        readAt = LocalDateTime.now();
        return true;
    }
}
