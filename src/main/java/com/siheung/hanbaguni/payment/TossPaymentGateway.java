package com.siheung.hanbaguni.payment;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.function.Supplier;

import static com.siheung.hanbaguni.payment.PaymentData.*;
import static com.siheung.hanbaguni.payment.TossPaymentException.Kind.*;

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

        return execute(() -> client.post()
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
                .body(Receipt.class));
    }

    public Receipt find(String paymentKey) {
        checkTestKey();

        return execute(() -> client.get()
                .uri("/v1/payments/{paymentKey}", paymentKey)
                .headers(headers -> headers.setBasicAuth(
                        secretKey,
                        "",
                        StandardCharsets.UTF_8
                ))
                .retrieve()
                .body(Receipt.class));
    }

    public Receipt findByOrderId(String orderRef) {
        checkTestKey();

        return execute(() -> client.get()
                .uri("/v1/payments/orders/{orderId}", orderRef)
                .headers(headers -> headers.setBasicAuth(
                        secretKey,
                        "",
                        StandardCharsets.UTF_8
                ))
                .retrieve()
                .body(Receipt.class));
    }

    public void checkTestKey() {
        if (!secretKey.startsWith("test_sk_")) {
            throw new IllegalStateException(
                    "Toss API 개별 연동 테스트 시크릿 키를 설정해주세요."
            );
        }
    }

    private Receipt execute(Supplier<Receipt> request) {
        Receipt receipt;

        try {
            receipt = request.get();

        } catch (RestClientResponseException exception) {
            // Toss가 실제로 반환한 HTTP 상태와 오류 코드를 보존합니다.
            throw new TossPaymentException(
                    HTTP_RESPONSE,
                    exception.getStatusCode().value(),
                    readErrorCode(exception)
            );

        } catch (ResourceAccessException exception) {
            // 시간 초과나 연결 실패는 결제 실패의 증거가 아닙니다.
            throw new TossPaymentException(
                    NETWORK,
                    0,
                    "NETWORK_ERROR"
            );

        } catch (RestClientException exception) {
            // 응답 변환 실패 등은 결과 불확실로 분류합니다.
            throw new TossPaymentException(
                    INVALID_RESPONSE,
                    0,
                    "UNREADABLE_RESPONSE"
            );
        }

        if (receipt == null) {
            throw new TossPaymentException(
                    INVALID_RESPONSE,
                    0,
                    "EMPTY_RESPONSE"
            );
        }

        return receipt;
    }

    private String readErrorCode(
            RestClientResponseException exception
    ) {
        try {
            TossError error =
                    exception.getResponseBodyAs(TossError.class);

            if (error != null
                    && error.code() != null
                    && !error.code().isBlank()) {
                return error.code();
            }

        } catch (RuntimeException ignored) {
            // 오류 본문을 읽지 못해도 HTTP 상태는 보존합니다.
        }

        return "UNKNOWN_ERROR";
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record TossError(String code) {
    }
}