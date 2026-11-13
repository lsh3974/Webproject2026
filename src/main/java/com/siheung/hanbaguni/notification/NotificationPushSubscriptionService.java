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
            User user = users.findById(userId).orElseThrow();
            // 브라우저 endpoint는 계정이 아니라 브라우저 프로필에 귀속되므로
            // 로그인한 현재 계정으로 소유권을 이전한다.
            subscription.transferTo(user, request.keys().p256dh(), request.keys().auth());
            return;
        }
        User user = users.findById(userId).orElseThrow();
        subscriptions.save(new PushSubscription(user, request.endpoint(), request.keys().p256dh(), request.keys().auth()));
    }

    /**
     * 로그아웃한 브라우저의 구독만 해제한다.
     * 같은 계정으로 등록된 다른 기기의 구독은 유지한다.
     */
    @Transactional
    public void deactivateForUserAndEndpoint(Long userId, String endpoint) {
        if (endpoint == null || endpoint.isBlank()) return;
        subscriptions.findByEndpoint(endpoint)
                .filter(subscription -> subscription.getUser().getId().equals(userId))
                .ifPresent(PushSubscription::deactivate);
    }

    @Transactional
    public void unsubscribe(Long userId, String endpoint) {
        deactivateForUserAndEndpoint(userId, endpoint);
    }
}
