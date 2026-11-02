package com.siheung.hanbaguni.payment;

public final class PaymentErrorPolicy {

    private PaymentErrorPolicy() {
    }

    /**
     * 이번 승인 요청이 거절됐다는 근거로 저장할 오류만 허용합니다.
     * 이것만으로 주문을 만료시키지는 않습니다.
     */
    public static boolean isDefiniteRejection(
            TossPaymentException exception
    ) {
        if (exception.getKind()
                != TossPaymentException.Kind.HTTP_RESPONSE) {
            return false;
        }

        int status = exception.getHttpStatus();
        String code = exception.getErrorCode();

        return (status == 400
                && "INVALID_PAYMENT_KEY".equals(code))
                || (status == 404
                && ("NOT_FOUND_PAYMENT".equals(code)
                || "NOT_FOUND_PAYMENT_SESSION".equals(code)));
    }

    /**
     * 결제 조회 API가 명시적으로 결제 정보 없음을 반환했는지 검사합니다.
     * 네트워크 오류, 인증 오류, 일반적인 404는 포함하지 않습니다.
     */
    public static boolean isPaymentNotFound(
            TossPaymentException exception
    ) {
        return exception.getKind()
                == TossPaymentException.Kind.HTTP_RESPONSE
                && exception.getHttpStatus() == 404
                && "NOT_FOUND_PAYMENT".equals(
                exception.getErrorCode()
        );
    }

    /**
     * 결제 실패와 구분해야 하는 상점 인증·설정 오류입니다.
     */
    public static boolean isConfigurationError(
            TossPaymentException exception
    ) {
        if (exception.getKind()
                != TossPaymentException.Kind.HTTP_RESPONSE) {
            return false;
        }

        String code = exception.getErrorCode();

        return "INVALID_API_KEY".equals(code)
                || "UNAUTHORIZED_KEY".equals(code)
                || "INCORRECT_BASIC_AUTH_FORMAT".equals(code);
    }
}