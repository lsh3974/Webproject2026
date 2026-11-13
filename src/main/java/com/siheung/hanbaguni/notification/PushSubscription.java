package com.siheung.hanbaguni.notification;

import com.siheung.hanbaguni.entity.User;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

@Entity
@Table(name = "notification_push_subscriptions")
@Getter
@NoArgsConstructor
public class PushSubscription {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "user_id", nullable = false) private User user;
    @Column(nullable = false, length = 1200) private String endpoint;
    @Column(nullable = false, length = 200) private String p256dh;
    @Column(name = "auth_secret", nullable = false, length = 100) private String auth;
    @Column(name = "created_at", nullable = false, updatable = false) private LocalDateTime createdAt;
    @Column(name = "last_used_at") private LocalDateTime lastUsedAt;
    @Column(nullable = false) private boolean active = true;

    public PushSubscription(User user, String endpoint, String p256dh, String auth) {
        this.user = user; this.endpoint = endpoint; this.p256dh = p256dh; this.auth = auth;
        this.createdAt = LocalDateTime.now();
    }
    public void updateKeys(String p256dh, String auth) { this.p256dh = p256dh; this.auth = auth; }
    public void transferTo(User user, String p256dh, String auth) {
        this.user = user;
        this.p256dh = p256dh;
        this.auth = auth;
        this.active = true;
    }
    public void markUsed() { this.lastUsedAt = LocalDateTime.now(); }
    public void deactivate() { this.active = false; }
    public void activate() { this.active = true; }
}
