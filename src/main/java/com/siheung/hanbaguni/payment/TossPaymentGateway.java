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

    // 결제 승인
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

    // 결제 키로 승인 결과 조회
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

    // 주문번호로 승인 결과 조회
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

    // DB에 저장한 주문 금액 전액을 취소 요청합니다.
    public RefundData.Receipt cancel(RefundData.Attempt attempt) {
        checkTestKey();

        if (attempt.amount() <= 0
                || attempt.paymentKey() == null
                || attempt.paymentKey().isBlank()
                || attempt.refundRequestId() == null
                || attempt.refundRequestId().isBlank()
                || attempt.refundRequestId().length() > 64
                || attempt.reason() == null
                || attempt.reason().isBlank()
                || attempt.reason().length() > 200) {
            throw new IllegalArgumentException(
                    "환불 요청 정보가 올바르지 않습니다."
            );
        }

        return execute(() -> client.post()
                .uri(
                        "/v1/payments/{paymentKey}/cancel",
                        attempt.paymentKey()
                )
                .headers(headers -> headers.setBasicAuth(
                        secretKey,
                        "",
                        StandardCharsets.UTF_8
                ))
                .header(
                        "Idempotency-Key",
                        attempt.refundRequestId()
                )
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of(
                        "cancelReason", attempt.reason(),
                        "cancelAmount", attempt.amount()
                ))
                .retrieve()
                .body(RefundData.Receipt.class));
    }

    // 환불 확인용 조회: 최초 금액과 남은 금액을 함께 받습니다.
    public RefundData.Receipt findForRefund(String paymentKey) {
        checkTestKey();

        return execute(() -> client.get()
                .uri("/v1/payments/{paymentKey}", paymentKey)
                .headers(headers -> headers.setBasicAuth(
                        secretKey,
                        "",
                        StandardCharsets.UTF_8
                ))
                .retrieve()
                .body(RefundData.Receipt.class));
    }

    public void checkTestKey() {
        if (!secretKey.startsWith("test_sk_")) {
            throw new IllegalStateException(
                    "Toss API 개별 연동 테스트 시크릿 키를 설정해주세요."
            );
        }
    }

    // 결제 응답과 환불 응답에 공통으로 사용하는 통신 처리입니다.
    private <T> T execute(Supplier<T> request) {
        T response;

        try {
            response = request.get();

        } catch (RestClientResponseException exception) {
            throw new TossPaymentException(
                    HTTP_RESPONSE,
                    exception.getStatusCode().value(),
                    readErrorCode(exception)
            );

        } catch (ResourceAccessException exception) {
            throw new TossPaymentException(
                    NETWORK,
                    0,
                    "NETWORK_ERROR"
            );

        } catch (RestClientException exception) {
            throw new TossPaymentException(
                    INVALID_RESPONSE,
                    0,
                    "UNREADABLE_RESPONSE"
            );
        }

        if (response == null) {
            throw new TossPaymentException(
                    INVALID_RESPONSE,
                    0,
                    "EMPTY_RESPONSE"
            );
        }

        return response;
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