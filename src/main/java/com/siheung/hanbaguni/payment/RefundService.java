package com.siheung.hanbaguni.payment;

import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;

import static com.siheung.hanbaguni.payment.RefundData.*;

@Service
public class RefundService {

    private final RefundTransactions transactions;
    private final TossPaymentGateway gateway;

    public RefundService(
            RefundTransactions transactions,
            TossPaymentGateway gateway
    ) {
        this.transactions = transactions;
        this.gateway = gateway;
    }

    public Result cancelMyOrder(
            Long orderId,
            Long userId,
            String reason
    ) {
        gateway.checkTestKey();

        Attempt attempt = transactions.startCustomerRefund(
                orderId,
                userId,
                reason
        );

        return process(attempt);
    }

    public Result retryMyRefund(Long orderId, Long userId) {
        gateway.checkTestKey();

        Attempt attempt = transactions.resumeCustomerRefund(
                orderId,
                userId
        );

        return process(attempt);
    }

    // 이후 모집 마감 작업에서 호출할 내부 기능입니다.
    public Result refundFailedOrder(Long orderId) {
        gateway.checkTestKey();

        Attempt attempt = transactions.startFailedGroupRefund(orderId);

        return process(attempt);
    }

    private Result process(Attempt attempt) {
        if (attempt.refunded()) {
            return new Result(
                    "REFUNDED",
                    "이미 전액 환불이 완료된 주문입니다."
            );
        }

        // 이전 요청에서 환불됐을 수 있으므로 먼저 조회합니다.
        Receipt current;

        try {
            current = gateway.findForRefund(attempt.paymentKey());

        } catch (RestClientException exception) {
            return errorResult(exception);
        }

        if (!RefundTransactions.matches(attempt, current)) {
            return pendingResult(
                    "결제 정보가 주문과 일치하지 않아 확인이 필요합니다."
            );
        }

        if (RefundTransactions.isFullRefund(attempt, current)) {
            return transactions.complete(attempt, current);
        }

        // 전액 결제가 그대로 남아 있는 경우에만 취소를 요청합니다.
        if (!"DONE".equals(current.status())
                || current.balanceAmount() == null
                || current.balanceAmount().longValue()
                != attempt.amount()) {
            return pendingResult(
                    "결제 상태 또는 잔액 확인이 필요합니다. 부분 취소된 결제는 자동 처리하지 않습니다."
            );
        }

        Receipt canceled;

        try {
            canceled = gateway.cancel(attempt);

        } catch (RestClientException exception) {
            // 취소는 됐지만 응답만 유실됐을 수 있으므로 다시 조회합니다.
            return reconcile(attempt);
        }

        if (RefundTransactions.isFullRefund(attempt, canceled)) {
            return transactions.complete(attempt, canceled);
        }

        return reconcile(attempt);
    }

    private Result reconcile(Attempt attempt) {
        Receipt receipt;

        try {
            receipt = gateway.findForRefund(attempt.paymentKey());

        } catch (RestClientException exception) {
            return errorResult(exception);
        }

        if (RefundTransactions.isFullRefund(attempt, receipt)) {
            return transactions.complete(attempt, receipt);
        }

        return pendingResult(
                "전액 환불 완료 여부를 아직 확인하지 못했습니다. 잠시 후 다시 확인해주세요."
        );
    }

    private Result errorResult(RestClientException exception) {
        if (exception instanceof TossPaymentException tossException
                && PaymentErrorPolicy.isConfigurationError(
                tossException
        )) {
            return pendingResult(
                    "환불 서비스 인증 설정에 문제가 있습니다. 운영자 확인이 필요합니다."
            );
        }

        return pendingResult(
                "환불 결과를 확인하지 못했습니다. 잠시 후 다시 확인해주세요."
        );
    }

    private Result pendingResult(String message) {
        return new Result(
                "REFUND_PENDING",
                message
        );
    }
}