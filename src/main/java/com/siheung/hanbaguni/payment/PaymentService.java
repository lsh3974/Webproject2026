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
        // 로컬 설정 누락은 주문 상태를 변경하기 전에 검사합니다.
        gateway.checkTestKey();

        return transactions.prepare(orderId, userId);
    }

    public Result confirm(
            Long orderId,
            Long userId,
            ConfirmRequest request
    ) {
        gateway.checkTestKey();

        Attempt attempt = transactions.begin(
                orderId,
                userId,
                request
        );

        if (attempt.paid()) {
            return paidResult();
        }

        if (!attempt.confirmationAllowed()) {
            return reconcile(attempt);
        }

        Receipt receipt;

        try {
            receipt = gateway.confirm(attempt);

        } catch (TossPaymentException exception) {
            if (PaymentErrorPolicy.isConfigurationError(exception)) {
                return configurationProblem();
            }

            if (PaymentErrorPolicy.isDefiniteRejection(exception)) {
                Attempt rejected = transactions.recordRejection(
                        attempt,
                        exception
                );

                return reconcile(rejected);
            }

            // 네트워크 오류, 5xx, 이미 처리된 결제 등은 조회합니다.
            return reconcile(attempt);

        } catch (RestClientException exception) {
            // 분류되지 않은 통신 오류도 실패로 단정하지 않습니다.
            return reconcile(attempt);
        }

        Result result = applyVerifiedReceipt(attempt, receipt);

        if (result != null) {
            return result;
        }

        return reconcile(attempt);
    }

    public Result retry(Long orderId, Long userId) {
        gateway.checkTestKey();

        Attempt attempt = transactions.resume(orderId, userId);

        if (attempt.paid()) {
            return paidResult();
        }

        return reconcile(attempt);
    }

    public void recoverPending(Long orderId) {
        Attempt attempt = transactions.pendingForRecovery(orderId);
        if (attempt != null) {
            gateway.checkTestKey();
            reconcile(attempt);
        }
    }

    private Result reconcile(Attempt attempt) {
        if (attempt.paid()) {
            return paidResult();
        }

        // 먼저 저장된 결제 키로 조회합니다.
        Receipt byKey = null;

        try {
            byKey = gateway.find(attempt.paymentKey());

        } catch (TossPaymentException exception) {
            if (PaymentErrorPolicy.isConfigurationError(exception)) {
                return configurationProblem();
            }

            // 결제 키가 잘못됐거나 조회가 실패해도
            // 서버에 저장한 주문번호로 한 번 더 확인합니다.

        } catch (RestClientException exception) {
            // 주문번호 조회로 이어갑니다.
        }

        Result keyResult = applyVerifiedReceipt(attempt, byKey);

        if (keyResult != null) {
            return keyResult;
        }

        // 브라우저 값이 아닌 DB에 저장한 주문번호로 조회합니다.
        Receipt byOrder;

        try {
            byOrder = gateway.findByOrderId(attempt.orderRef());

        } catch (TossPaymentException exception) {
            if (PaymentErrorPolicy.isConfigurationError(exception)) {
                return configurationProblem();
            }

            if (PaymentErrorPolicy.isPaymentNotFound(exception)
                    && attempt.rejectionCode() != null) {
                return transactions.expireRejectedPayment(
                        attempt,
                        exception
                );
            }

            // 조회 결과가 없더라도 승인 거절 근거가 없으면 유지합니다.
            return pendingResult();

        } catch (RestClientException exception) {
            return pendingResult();
        }

        Result orderResult = applyVerifiedReceipt(attempt, byOrder);

        return orderResult != null
                ? orderResult
                : pendingResult();
    }

    private Result applyVerifiedReceipt(
            Attempt attempt,
            Receipt receipt
    ) {
        if (!PaymentTransactions.matches(attempt, receipt)) {
            return null;
        }

        if ("DONE".equals(receipt.status())) {
            return transactions.complete(attempt, receipt);
        }

        if ("ABORTED".equals(receipt.status())
                || "EXPIRED".equals(receipt.status())) {
            return transactions.fail(attempt, receipt);
        }

        return null;
    }

    private Result paidResult() {
        return new Result(
                "PAID",
                "이미 결제가 완료된 주문입니다."
        );
    }

    private Result pendingResult() {
        return new Result(
                "PAYMENT_PENDING",
                "결제 결과를 아직 확인하지 못했습니다. 잠시 후 다시 확인해주세요. 계속 동일하면 주문번호와 함께 문의해주세요."
        );
    }

    private Result configurationProblem() {
        return new Result(
                "PAYMENT_PENDING",
                "결제 서비스 인증 설정에 문제가 있습니다. 운영자 확인이 필요합니다."
        );
    }
}