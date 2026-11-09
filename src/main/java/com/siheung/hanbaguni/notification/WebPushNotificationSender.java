package com.siheung.hanbaguni.notification;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.apache.http.HttpResponse;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.springframework.stereotype.Component;
import java.security.Security;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class WebPushNotificationSender implements NotificationChannelSender {
    private final NotificationProperties properties;
    private final ObjectMapper objectMapper;

    @Override public NotificationDelivery.Channel channel() { return NotificationDelivery.Channel.PUSH; }

    @Override
    public String send(NotificationDispatchData data) {
        var config = properties.getPush();
        if (blank(config.getPublicKey()) || blank(config.getPrivateKey()) || blank(config.getSubject())) {
            throw new NotificationSendException("VAPID 설정이 없습니다. PUSH_VAPID_PUBLIC_KEY, PUSH_VAPID_PRIVATE_KEY, PUSH_VAPID_SUBJECT를 설정하세요.", NotificationSendException.Outcome.PERMANENT);
        }
        if (blank(data.pushEndpoint()) || blank(data.pushP256dh()) || blank(data.pushAuth())) {
            throw new NotificationSendException("브라우저 구독 키가 올바르지 않습니다.", NotificationSendException.Outcome.PERMANENT);
        }
        try {
            if (Security.getProvider("BC") == null) Security.addProvider(new BouncyCastleProvider());
            // 잠금 화면/공용 브라우저에 거래 정보가 노출되지 않도록 최소 정보만 전송한다.
            byte[] payload = objectMapper.writeValueAsBytes(Map.of(
                    "title", "시흥한바구니 알림",
                    "body", "새 알림이 있어요. 로그인 후 확인해 주세요.",
                    "url", "/notifications"
            ));
            var pushService = new nl.martijndwars.webpush.PushService(
                    config.getPublicKey(), config.getPrivateKey(), config.getSubject());
            var pushNotification = new nl.martijndwars.webpush.Notification(
                    data.pushEndpoint(), data.pushP256dh(), data.pushAuth(), payload, 3600);
            HttpResponse response = pushService.send(pushNotification);
            int status = response.getStatusLine().getStatusCode();
            if (status == 404 || status == 410) {
                throw new NotificationSendException("브라우저 푸시 구독이 만료되었습니다.", NotificationSendException.Outcome.SUBSCRIPTION_GONE);
            }
            if (status < 200 || status >= 300) {
                NotificationSendException.Outcome outcome = status == 429
                        ? NotificationSendException.Outcome.RETRYABLE
                        : status >= 500 ? NotificationSendException.Outcome.UNKNOWN
                        : NotificationSendException.Outcome.PERMANENT;
                throw new NotificationSendException("Push Service HTTP " + status, outcome);
            }
            return "HTTP-" + status;
        } catch (NotificationSendException exception) {
            throw exception;
        } catch (Exception exception) {
            // 요청이 푸시 서비스에 도달했는지 확정할 수 없으므로 같은 구독으로 자동 재전송하지 않습니다.
            throw new NotificationSendException("브라우저 푸시 전송 결과 불명: " + exception.getClass().getSimpleName(),
                    NotificationSendException.Outcome.UNKNOWN, exception);
        }
    }
    private boolean blank(String value) { return value == null || value.isBlank(); }
}
