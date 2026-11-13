package com.siheung.hanbaguni.notification;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.Optional;
import java.time.temporal.ChronoUnit;
import java.time.Duration;

@Component
@RequiredArgsConstructor
public class SolapiSmsNotificationSender implements NotificationChannelSender {
    private final NotificationProperties properties;
    private final RestClient restClient = buildRestClient();

    private static RestClient buildRestClient() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(5));
        factory.setReadTimeout(Duration.ofSeconds(10));
        return RestClient.builder().requestFactory(factory).build();
    }

    @Override public NotificationDelivery.Channel channel() { return NotificationDelivery.Channel.SMS; }

    @Override
    @SuppressWarnings("unchecked")
    public String send(NotificationDispatchData data) {
        var config = properties.getSolapi();
        if (blank(config.getApiKey()) || blank(config.getApiSecret()) || blank(config.getFrom())) {
            throw new NotificationSendException("SOLAPI 설정이 없습니다. SOLAPI_API_KEY, SOLAPI_API_SECRET, SOLAPI_FROM을 설정하세요.", NotificationSendException.Outcome.PERMANENT);
        }
        if (blank(data.recipientPhone())) throw new NotificationSendException("확인된 휴대전화 번호가 없습니다.", NotificationSendException.Outcome.PERMANENT);
        String phone = toSolapiKoreanNumber(data.recipientPhone());
        String link = absoluteUrl(data.targetUrl());
        String messagePrefix = "[시흥한바구니] " + data.title() + "\n" + data.body();
        // 본문이 길어도 서비스 복귀 링크는 끝에 온전히 남깁니다.
        int linkBytes = ("\n" + link).getBytes(StandardCharsets.UTF_8).length;
        String text = truncateUtf8(messagePrefix, Math.max(0, 1800 - linkBytes)) + "\n" + link;
        String date = Instant.now().toString();
        String salt = UUID.randomUUID().toString().replace("-", "");
        String auth;
        try { auth = createAuthHeader(config.getApiKey(), config.getApiSecret(), date, salt); }
        catch (Exception exception) { throw new NotificationSendException("SOLAPI 인증 서명 생성 실패", NotificationSendException.Outcome.PERMANENT, exception); }

        Map<String, Object> message = Map.of(
                "to", phone,
                "from", config.getFrom().replaceAll("\\D", ""),
                "country", "82",
                "text", text,
                "autoTypeDetect", true,
                "customFields", Map.of("notificationDeliveryId", String.valueOf(data.deliveryId()))
        );
        Map<String, Object> request = Map.of("messages", List.of(message), "allowDuplicates", false, "showMessageList", true);
        try {
            Map<String, Object> response = restClient.post()
                    .uri(config.getBaseUrl().replaceAll("/$", "") + "/messages/v4/send-many/detail")
                    .header("Authorization", auth)
                    .header("Content-Type", "application/json")
                    .body(request)
                    .retrieve()
                    .body(Map.class);
            if (response == null) throw new NotificationSendException("SOLAPI 응답이 비어 있습니다.", NotificationSendException.Outcome.UNKNOWN);
            Object failed = response.get("failedMessageList");
            if (failed instanceof List<?> list && !list.isEmpty()) {
                Object first = list.get(0);
                String reason = first instanceof Map<?, ?> m ? String.valueOf(m.get("statusMessage") != null ? m.get("statusMessage") : "발송 접수 실패") : "발송 접수 실패";
                throw new NotificationSendException("SOLAPI 발송 거절: " + reason, NotificationSendException.Outcome.PERMANENT);
            }
            Object messageId = response.get("messageId");
            if (messageId != null) return String.valueOf(messageId);
            Object messages = response.get("messageList");
            if (messages instanceof List<?> list && !list.isEmpty() && list.get(0) instanceof Map<?, ?> m
                    && m.get("messageId") != null) {
                return String.valueOf(m.get("messageId"));
            }
            if (messages instanceof Map<?, ?> map && !map.isEmpty()) {
                Object first = map.values().iterator().next();
                if (first instanceof Map<?, ?> m && m.get("messageId") != null) return String.valueOf(m.get("messageId"));
            }
            Object groupInfo = response.get("groupInfo");
            if (groupInfo instanceof Map<?, ?> m) return String.valueOf(m.get("groupId"));
            throw new NotificationSendException("SOLAPI 응답에서 발송 식별자를 확인하지 못했습니다.", NotificationSendException.Outcome.UNKNOWN);
        } catch (NotificationSendException exception) {
            throw exception;
        } catch (RestClientResponseException exception) {
            HttpStatusCode status = exception.getStatusCode();
            NotificationSendException.Outcome outcome = status.value() == 429
                    ? NotificationSendException.Outcome.RETRYABLE
                    : status.is4xxClientError()
                    ? NotificationSendException.Outcome.PERMANENT : NotificationSendException.Outcome.UNKNOWN;
            throw new NotificationSendException("SOLAPI HTTP " + status.value(), outcome, exception);
        } catch (ResourceAccessException exception) {
            throw new NotificationSendException("SOLAPI 응답을 받지 못했습니다. 접수 여부 확인이 필요합니다.", NotificationSendException.Outcome.UNKNOWN, exception);
        } catch (RuntimeException exception) {
            throw new NotificationSendException("SOLAPI 호출 실패", NotificationSendException.Outcome.UNKNOWN, exception);
        }
    }


    @Override
    public boolean supportsReconciliation() { return true; }

    /**
     * POST 응답이 유실된 경우 최근 발송 내역을 조회해 customFields.notificationDeliveryId로 대조합니다.
     * 조회에서 발견되지 않아도 인덱싱 지연 가능성이 있으므로 재발송하지 않고 UNKNOWN 상태를 유지합니다.
     */
    @Override
    @SuppressWarnings("unchecked")
    public Optional<String> findAccepted(NotificationDispatchData data) {
        var config = properties.getSolapi();
        if (blank(config.getApiKey()) || blank(config.getApiSecret()) || blank(config.getFrom())) {
            throw new NotificationSendException("SOLAPI 조회 설정이 없습니다.", NotificationSendException.Outcome.UNKNOWN);
        }
        String now = Instant.now().truncatedTo(ChronoUnit.SECONDS).toString();
        Instant created = data.deliveryCreatedAt() == null
                ? Instant.now().minus(15, ChronoUnit.MINUTES)
                : data.deliveryCreatedAt().atZone(java.time.ZoneId.systemDefault()).toInstant().minus(5, ChronoUnit.MINUTES);
        String start = created.truncatedTo(ChronoUnit.SECONDS).toString();
        String auth;
        try {
            String salt = UUID.randomUUID().toString().replace("-", "");
            auth = createAuthHeader(config.getApiKey(), config.getApiSecret(), now, salt);
        } catch (Exception exception) {
            throw new NotificationSendException("SOLAPI 조회 서명 생성 실패", NotificationSendException.Outcome.UNKNOWN, exception);
        }
        try {
            Map<String, Object> response = restClient.get()
                    .uri(config.getBaseUrl().replaceAll("/$", "")
                            + "/messages/v4/list?to={to}&from={from}&startDate={start}&endDate={end}&limit=100",
                            toSolapiKoreanNumber(data.recipientPhone()),
                            config.getFrom().replaceAll("\\D", ""), start, now)
                    .header("Authorization", auth)
                    .retrieve().body(Map.class);
            if (response == null || !(response.get("messageList") instanceof Map<?, ?> messages)) {
                return Optional.empty();
            }
            String expected = String.valueOf(data.deliveryId());
            for (Object value : messages.values()) {
                if (!(value instanceof Map<?, ?> message)) continue;
                Object customFields = message.get("customFields");
                if (customFields instanceof Map<?, ?> fields
                        && expected.equals(String.valueOf(fields.get("notificationDeliveryId")))) {
                    Object messageId = message.get("messageId");
                    if (messageId != null) return Optional.of(String.valueOf(messageId));
                }
            }
            return Optional.empty();
        } catch (RestClientResponseException exception) {
            throw new NotificationSendException("SOLAPI 발송 내역 조회 HTTP " + exception.getStatusCode().value(),
                    NotificationSendException.Outcome.UNKNOWN, exception);
        } catch (RuntimeException exception) {
            if (exception instanceof NotificationSendException nse) throw nse;
            throw new NotificationSendException("SOLAPI 발송 내역 조회 실패", NotificationSendException.Outcome.UNKNOWN, exception);
        }
    }


    /** 프로젝트의 phone_e164(+82...)를 SOLAPI 국내 발송 형식(010...)으로 변환합니다. */
    static String toSolapiKoreanNumber(String phone) {
        if (phone == null || phone.isBlank()) return "";
        String digits = phone.replaceAll("\\D", "");
        if (digits.startsWith("82")) {
            digits = digits.substring(2);
            if (!digits.startsWith("0")) digits = "0" + digits;
        }
        return digits;
    }

    /** SOLAPI SMS/LMS 본문이 2,000 UTF-8 bytes를 넘지 않도록 코드 포인트 경계에서 자릅니다. */
    static String truncateUtf8(String value, int maxBytes) {
        if (value == null || maxBytes <= 0) return "";
        StringBuilder result = new StringBuilder();
        int usedBytes = 0;
        for (int offset = 0; offset < value.length();) {
            int codePoint = value.codePointAt(offset);
            String unit = new String(Character.toChars(codePoint));
            int unitBytes = unit.getBytes(StandardCharsets.UTF_8).length;
            if (usedBytes + unitBytes > maxBytes) break;
            result.append(unit);
            usedBytes += unitBytes;
            offset += Character.charCount(codePoint);
        }
        return result.toString();
    }

    static String createAuthHeader(String apiKey, String apiSecret, String date, String salt) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(apiSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        String signature = HexFormat.of().formatHex(mac.doFinal((date + salt).getBytes(StandardCharsets.UTF_8)));
        return "HMAC-SHA256 apiKey=" + apiKey + ", date=" + date + ", salt=" + salt + ", signature=" + signature;
    }
    private String absoluteUrl(String path) { return properties.getBaseUrl().replaceAll("/$", "") + path; }
    private boolean blank(String value) { return value == null || value.isBlank(); }
}
