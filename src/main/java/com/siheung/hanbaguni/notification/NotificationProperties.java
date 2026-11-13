package com.siheung.hanbaguni.notification;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Getter @Setter
@Component
@ConfigurationProperties(prefix = "hanbaguni.notifications")
public class NotificationProperties {
    private boolean enabled = true;
    private long outboxDelayMs = 3000;
    private long deliveryDelayMs = 5000;
    private String baseUrl = "http://localhost:8080";
    private final Email email = new Email();
    private final Solapi solapi = new Solapi();
    private final Push push = new Push();

    @Getter @Setter public static class Email { private String host = ""; private String from = ""; }
    @Getter @Setter public static class Solapi {
        private String apiKey = "";
        private String apiSecret = "";
        private String from = "";
        private String baseUrl = "https://api.solapi.com";
    }
    @Getter @Setter public static class Push {
        private String publicKey = "";
        private String privateKey = "";
        private String subject = "mailto:admin@example.com";
    }
}
