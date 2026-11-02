package com.siheung.hanbaguni.payment;

import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;

import static com.siheung.hanbaguni.payment.PaymentData.*;

@Service
public class PaymentService {

    private final PaymentTransactions transactions;
    private final TossPaymentGateway gateway;

    public PaymentService(
            PaymentTransactions transactions,
            TossPaymentGateway gateway
    ) {
        this.transactions = transactions;
        this.gateway = gateway;
    }

    public Prepared prepare(Long orderId, Long userId) {
        return transactions.prepare(orderId, userId);
    }

    public Result confirm(
            Long orderId,
            Long userId,
            ConfirmRequest request
    ) {
        Attempt attempt = transactions.begin(
                orderId,
                userId,
                request
        );

        return process(attempt);
    }

    public Result retry(Long orderId, Long userId) {
        Attempt attempt = transactions.resume(orderId, userId);
        return process(attempt);
    }

    private Result process(Attempt attempt) {
        if (attempt.paid()) {
            return new Result("PAID", "이미 결제가 완료된 주문입니다.");
        }

        Receipt receipt;

        try {
            receipt = gateway.confirm(attempt);

        } catch (RestClientException exception) {
            // 승인은 됐지만 응답만 유실됐을 수 있으므로 조회합니다.
            try {
                receipt = gateway.find(attempt.paymentKey());

            } catch (RestClientException lookupException) {
                return pendingResult();
            }
        }

        if (!PaymentTransactions.matches(attempt, receipt)) {
            // 정보가 다르면 임의로 성공이나 실패로 바꾸지 않습니다.
            return pendingResult();
        }

        if ("DONE".equals(receipt.status())) {
            return transactions.complete(attempt, receipt);
        }

        if ("ABORTED".equals(receipt.status())
                || "EXPIRED".equals(receipt.status())) {
            return transactions.fail(attempt, receipt);
        }

        return pendingResult();
    }

    private Result pendingResult() {
        return new Result(
                "PAYMENT_PENDING",
                "결제 결과를 확인 중입니다. 잠시 후 결과를 다시 확인해주세요."
        );
    }
}