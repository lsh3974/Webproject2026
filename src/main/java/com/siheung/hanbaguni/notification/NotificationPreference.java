package com.siheung.hanbaguni.notification;

import com.siheung.hanbaguni.entity.User;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "notification_preferences")
@Getter
@NoArgsConstructor
public class NotificationPreference {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @OneToOne(fetch = FetchType.LAZY) @JoinColumn(name = "user_id", nullable = false, unique = true)
    private User user;
    @Column(name = "email_enabled", nullable = false) private boolean emailEnabled;
    @Column(name = "sms_enabled", nullable = false) private boolean smsEnabled;
    @Column(name = "push_enabled", nullable = false) private boolean pushEnabled;

    public NotificationPreference(User user) { this.user = user; }

    public void update(boolean emailEnabled, boolean smsEnabled, boolean pushEnabled) {
        this.emailEnabled = emailEnabled;
        this.smsEnabled = smsEnabled;
        this.pushEnabled = pushEnabled;
    }
}
