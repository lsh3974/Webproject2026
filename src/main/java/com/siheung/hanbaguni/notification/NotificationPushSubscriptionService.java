package com.siheung.hanbaguni.notification;

import com.siheung.hanbaguni.entity.User;
import com.siheung.hanbaguni.repository.PushSubscriptionRepository;
import com.siheung.hanbaguni.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.net.URI;

@Service
@RequiredArgsConstructor
public class NotificationPushSubscriptionService {
    private final PushSubscriptionRepository subscriptions;
    private final UserRepository users;

    @Transactional
    public void subscribe(Long userId, PushSubscriptionRequest request) {
        if (request == null || request.keys() == null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "푸시 구독 정보가 올바르지 않습니다.");
        URI endpoint;
        try { endpoint = URI.create(request.endpoint()); }
        catch (IllegalArgumentException e) { throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "푸시 endpoint가 올바르지 않습니다."); }
        String host = endpoint.getHost() == null ? "" : endpoint.getHost().toLowerCase(java.util.Locale.ROOT);
        boolean allowedHost = host.equals("fcm.googleapis.com")
                || host.equals("updates.push.services.mozilla.com")
                || host.equals("web.push.apple.com") || host.endsWith(".push.apple.com")
                || host.equals("wns.windows.com") || host.endsWith(".notify.windows.com");
        if (!"https".equalsIgnoreCase(endpoint.getScheme()) || !allowedHost
                || endpoint.getUserInfo() != null || (endpoint.getPort() != -1 && endpoint.getPort() != 443)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "지원되는 브라우저 푸시 서비스의 HTTPS endpoint만 등록할 수 있습니다.");
        }
        var existing = subscriptions.findByEndpoint(request.endpoint());
        if (existing.isPresent()) {
            PushSubscription subscription = existing.get();
            if (!subscription.getUser().getId().equals(userId)) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "다른 계정에 등록된 푸시 구독입니다.");
            }
            subscription.updateKeys(request.keys().p256dh(), request.keys().auth());
            subscription.activate();
            return;
        }
        User user = users.findById(userId).orElseThrow();
        subscriptions.save(new PushSubscription(user, request.endpoint(), request.keys().p256dh(), request.keys().auth()));
    }

    @Transactional
    public void unsubscribe(Long userId, String endpoint) {
        if (endpoint == null || endpoint.isBlank()) return;
        subscriptions.findByEndpoint(endpoint).filter(s -> s.getUser().getId().equals(userId))
                .ifPresent(PushSubscription::deactivate);
    }
}
