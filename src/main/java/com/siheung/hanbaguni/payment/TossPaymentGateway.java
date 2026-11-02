package com.siheung.hanbaguni.payment;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.nio.charset.StandardCharsets;
import java.util.Map;

import static com.siheung.hanbaguni.payment.PaymentData.*;

@Component
public class TossPaymentGateway {

    private final RestClient client;
    private final String secretKey;

    public TossPaymentGateway(
            @Value("${payment.toss.secret-key:}") String secretKey
    ) {
        this.secretKey = secretKey;

        SimpleClientHttpRequestFactory factory =
                new SimpleClientHttpRequestFactory();

        factory.setConnectTimeout(3000);
        factory.setReadTimeout(10000);

        this.client = RestClient.builder()
                .baseUrl("https://api.tosspayments.com")
                .requestFactory(factory)
                .build();
    }

    public Receipt confirm(Attempt attempt) {
        checkTestKey();

        return client.post()
                .uri("/v1/payments/confirm")
                .headers(headers -> headers.setBasicAuth(
                        secretKey,
                        "",
                        StandardCharsets.UTF_8
                ))
                .header("Idempotency-Key", attempt.orderRef())
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of(
                        "paymentKey", attempt.paymentKey(),
                        "orderId", attempt.orderRef(),
                        "amount", attempt.amount()
                ))
                .retrieve()
                .body(Receipt.class);
    }

    public Receipt find(String paymentKey) {
        checkTestKey();

        return client.get()
                .uri("/v1/payments/{paymentKey}", paymentKey)
                .headers(headers -> headers.setBasicAuth(
                        secretKey,
                        "",
                        StandardCharsets.UTF_8
                ))
                .retrieve()
                .body(Receipt.class);
    }

    private void checkTestKey() {
        if (!secretKey.startsWith("test_sk_")) {
            throw new IllegalStateException(
                    "Toss API 개별 연동 테스트 시크릿 키를 설정해주세요."
            );
        }
    }
}