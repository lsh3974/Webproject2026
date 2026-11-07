package com.siheung.hanbaguni.notification;

import com.siheung.hanbaguni.entity.User;
import com.siheung.hanbaguni.repository.NotificationPreferenceRepository;
import com.siheung.hanbaguni.repository.NotificationRepository;
import com.siheung.hanbaguni.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.util.List;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class NotificationInboxService {
    private final NotificationRepository notifications;
    private final NotificationPreferenceRepository preferences;
    private final UserRepository users;

    @Transactional(readOnly = true)
    public List<NotificationView> list(Long userId) {
        return notifications.findByUserIdOrderByCreatedAtDescIdDesc(userId, PageRequest.of(0, 100))
                .stream().map(NotificationView::from).toList();
    }

    @Transactional(readOnly = true)
    public List<NotificationView> recent(Long userId, int limit) {
        int safeLimit = Math.max(1, Math.min(limit, 10));
        return notifications.findByUserIdOrderByCreatedAtDescIdDesc(userId, PageRequest.of(0, safeLimit))
                .stream().map(NotificationView::from).toList();
    }

    @Transactional(readOnly = true)
    public long unreadCount(Long userId) { return notifications.countByUserIdAndReadAtIsNull(userId); }

    @Transactional
    public String markRead(Long notificationId, Long userId) {
        Notification notification = notifications.findByIdAndUserId(notificationId, userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "알림을 찾을 수 없습니다."));
        notification.markRead();
        return safeLocalPath(notification.getTargetUrl());
    }

    @Transactional
    public void markAllRead(Long userId) {
        notifications.markAllReadForUser(userId, LocalDateTime.now());
    }

    @Transactional
    public NotificationPreferenceForm preferenceForm(Long userId) {
        NotificationPreference preference = preferences.findByUserId(userId).orElseGet(() -> {
            User user = users.findById(userId).orElseThrow();
            return preferences.save(new NotificationPreference(user));
        });
        NotificationPreferenceForm form = new NotificationPreferenceForm();
        form.setEmailEnabled(preference.isEmailEnabled());
        form.setSmsEnabled(preference.isSmsEnabled());
        form.setPushEnabled(preference.isPushEnabled());
        return form;
    }

    @Transactional(readOnly = true)
    public NotificationContactStatus contactStatus(Long userId) {
        User user = users.findById(userId).orElseThrow();
        String email = user.getEmail();
        String maskedPhone = maskPhone(user.getPhoneE164());
        return new NotificationContactStatus(
                user.getEmailVerifiedAt() != null,
                email == null ? "" : email,
                user.getPhoneVerifiedAt() != null,
                maskedPhone
        );
    }

    private String maskPhone(String phone) {
        if (phone == null || phone.isBlank()) return "등록된 번호 없음";
        String digits = phone.replaceAll("\\D", "");
        if (digits.length() < 7) return "등록된 번호";
        return "등록 번호 끝자리 " + digits.substring(digits.length() - 4);
    }

    @Transactional
    public void updatePreferences(Long userId, NotificationPreferenceForm form) {
        NotificationPreference preference = preferences.findByUserId(userId).orElseGet(() -> {
            User user = users.findById(userId).orElseThrow();
            return preferences.save(new NotificationPreference(user));
        });
        preference.update(form.isEmailEnabled(), form.isSmsEnabled(), form.isPushEnabled());
    }

    private String safeLocalPath(String path) {
        return path != null && path.startsWith("/") && !path.startsWith("//") && !path.contains("\\")
                ? path : "/notifications";
    }
}
