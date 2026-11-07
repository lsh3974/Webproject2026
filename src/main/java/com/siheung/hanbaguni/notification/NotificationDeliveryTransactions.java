package com.siheung.hanbaguni.notification;

import com.siheung.hanbaguni.repository.NotificationDeliveryRepository;
import com.siheung.hanbaguni.repository.PushSubscriptionRepository;
import com.siheung.hanbaguni.repository.NotificationPreferenceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class NotificationDeliveryTransactions {
    private final NotificationDeliveryRepository deliveries;
    private final PushSubscriptionRepository subscriptions;
    private final NotificationPreferenceRepository preferences;

    @Transactional
    public Optional<Long> claimNext() {
        var due = deliveries.findDueForProcessing(LocalDateTime.now(), PageRequest.of(0, 1));
        if (due.isEmpty()) return Optional.empty();
        NotificationDelivery delivery = due.get(0);
        delivery.claim(LocalDateTime.now());
        return Optional.of(delivery.getId());
    }

    @Transactional(readOnly = true)
    public NotificationDispatchData load(Long id) {
        NotificationDelivery d = deliveries.findById(id).orElseThrow();
        var n = d.getNotification();
        var u = n.getUser();
        var ps = d.getPushSubscription();
        var preference = preferences.findByUserId(u.getId()).orElse(null);
        return new NotificationDispatchData(d.getId(), d.getCreatedAt(), n.getId(), d.getChannel(), d.getAttemptCount(),
                n.getTitle(), n.getBody(), n.getTargetUrl(), u.getEmail(), u.getPhoneE164(), u.getDisplayName(),
                u.getEmailVerifiedAt() != null, u.getPhoneVerifiedAt() != null,
                preference != null && preference.isEmailEnabled(),
                preference != null && preference.isSmsEnabled(),
                preference != null && preference.isPushEnabled(),
                ps != null && ps.isActive(),
                ps != null ? ps.getId() : null, ps != null ? ps.getEndpoint() : null,
                ps != null ? ps.getP256dh() : null, ps != null ? ps.getAuth() : null);
    }

    @Transactional
    public java.util.List<Long> claimUnknownForReconciliation() {
        var rows = deliveries.findUnknownForReconciliation(LocalDateTime.now(), PageRequest.of(0, 50));
        LocalDateTime now = LocalDateTime.now();
        rows.forEach(row -> row.claimReconciliation(now));
        return rows.stream().map(NotificationDelivery::getId).toList();
    }

    @Transactional
    public void deferReconciliation(Long id, String reason) {
        deliveries.findByIdForUpdate(id).orElseThrow().deferReconciliation(reason);
    }

    @Transactional
    public void markSkipped(Long id, String reason) {
        deliveries.findByIdForUpdate(id).orElseThrow().skip(reason);
    }

    @Transactional
    public void markSent(Long id, String providerMessageId) {
        deliveries.findByIdForUpdate(id).orElseThrow().sent(providerMessageId);
    }

    @Transactional
    public void markFailure(Long id, NotificationSendException exception) {
        NotificationDelivery d = deliveries.findByIdForUpdate(id).orElseThrow();
        String message = exception.getMessage();
        switch (exception.getOutcome()) {
            case UNKNOWN -> d.unknown(message);
            case SUBSCRIPTION_GONE -> {
                d.removeSubscriptionTarget();
                if (d.getPushSubscription() != null) {
                    subscriptions.findById(d.getPushSubscription().getId()).ifPresent(PushSubscription::deactivate);
                }
            }
            case PERMANENT -> d.fail(message);
            case RETRYABLE -> {
                if (d.getAttemptCount() >= 5) d.fail(message);
                else {
                    long delaySeconds = Math.min(3600, 30L * (1L << Math.min(6, d.getAttemptCount() - 1)));
                    d.retry(message, LocalDateTime.now().plusSeconds(delaySeconds));
                }
            }
        }
    }

    /** 전송 프로세스가 중단된 PROCESSING 건은 중복 발송 위험 때문에 자동 재전송하지 않습니다. */
    @Transactional
    public void markExpiredClaimsUnknown() {
        for (NotificationDelivery d : deliveries.findExpiredClaims(LocalDateTime.now(), PageRequest.of(0, 100))) {
            d.unknown("발송 처리 중 서버가 종료되어 제공사 접수 여부를 확인할 수 없습니다. 제공사 발송 내역 대조가 필요합니다.");
        }
    }
}
