package com.siheung.hanbaguni.notification;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.MailException;
import java.net.ConnectException;
import java.net.UnknownHostException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class EmailNotificationSender implements NotificationChannelSender {
    private final ObjectProvider<JavaMailSender> mailSenderProvider;
    private final NotificationProperties properties;

    @Override public NotificationDelivery.Channel channel() { return NotificationDelivery.Channel.EMAIL; }

    @Override
    public String send(NotificationDispatchData data) {
        JavaMailSender sender = mailSenderProvider.getIfAvailable();
        if (sender == null || blank(properties.getEmail().getHost()) || blank(properties.getEmail().getFrom())) {
            throw new NotificationSendException("SMTP 설정이 없습니다. MAIL_HOST/NOTIFICATION_EMAIL_FROM을 설정하세요.", NotificationSendException.Outcome.PERMANENT);
        }
        if (data.recipientEmail() == null || data.recipientEmail().isBlank()) {
            throw new NotificationSendException("확인된 이메일 주소가 없습니다.", NotificationSendException.Outcome.PERMANENT);
        }
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(properties.getEmail().getFrom());
        message.setTo(data.recipientEmail());
        message.setSubject("[시흥한바구니] " + data.title());
        message.setText(data.body() + "\n\n알림 확인: " + absoluteUrl(data.targetUrl()));
        try {
            sender.send(message);
            return null;
        } catch (MailException exception) {
            // 연결 자체가 성립하지 않은 경우는 제공사 접수 전으로 볼 수 있어 재시도합니다.
            if (hasCause(exception, ConnectException.class) || hasCause(exception, UnknownHostException.class)) {
                throw new NotificationSendException("SMTP 연결 실패: " + exception.getClass().getSimpleName(),
                        NotificationSendException.Outcome.RETRYABLE, exception);
            }
            // DATA 전송 후 응답 유실 등은 접수 여부를 알 수 없으므로 중복 발송하지 않습니다.
            throw new NotificationSendException("SMTP 발송 결과 불명: " + exception.getClass().getSimpleName(),
                    NotificationSendException.Outcome.UNKNOWN, exception);
        }
    }

    private String absoluteUrl(String path) { return properties.getBaseUrl().replaceAll("/$", "") + path; }
    private boolean blank(String value) { return value == null || value.isBlank(); }
    private boolean hasCause(Throwable error, Class<? extends Throwable> type) {
        for (Throwable cause = error; cause != null; cause = cause.getCause()) {
            if (type.isInstance(cause)) return true;
        }
        return false;
    }
}
