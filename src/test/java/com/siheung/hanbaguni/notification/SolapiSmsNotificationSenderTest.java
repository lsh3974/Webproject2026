package com.siheung.hanbaguni.notification;

import org.junit.jupiter.api.Test;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;

import static org.assertj.core.api.Assertions.assertThat;

class SolapiSmsNotificationSenderTest {
    @Test
    void 본문은_한글과_이모지를_중간에서_자르지_않고_UTF8_바이트_제한을_지킨다() {
        String source = "시흥한바구니 알림 😊 ".repeat(300);
        String truncated = SolapiSmsNotificationSender.truncateUtf8(source, 1800);

        assertThat(truncated.getBytes(StandardCharsets.UTF_8).length).isLessThanOrEqualTo(1800);
        assertThat(truncated).isNotEmpty();
        assertThat(Character.isHighSurrogate(truncated.charAt(truncated.length() - 1))).isFalse();
    }

    @Test
    void E164_한국_휴대전화는_SOLAPI_국내_수신번호로_변환된다() {
        assertThat(SolapiSmsNotificationSender.toSolapiKoreanNumber("+821012345678"))
                .isEqualTo("01012345678");
        assertThat(SolapiSmsNotificationSender.toSolapiKoreanNumber("010-1234-5678"))
                .isEqualTo("01012345678");
    }

    @Test
    void SOLAPI_인증_헤더는_HMAC_SHA256으로_생성된다() throws Exception {
        String apiKey = "test-api-key";
        String secret = "test-secret";
        String date = "2026-11-07T01:00:00.000Z";
        String salt = "0123456789abcdef";

        String header = SolapiSmsNotificationSender.createAuthHeader(apiKey, secret, date, salt);
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        String expected = HexFormat.of().formatHex(mac.doFinal((date + salt).getBytes(StandardCharsets.UTF_8)));

        assertThat(header).isEqualTo("HMAC-SHA256 apiKey=" + apiKey + ", date=" + date
                + ", salt=" + salt + ", signature=" + expected);
    }
}
