package com.siheung.hanbaguni.payment;

import org.springframework.web.client.RestClientException;

public class TossPaymentException extends RestClientException {

    public enum Kind {
        HTTP_RESPONSE,
        NETWORK,
        INVALID_RESPONSE
    }

    private final Kind kind;
    private final int httpStatus;
    private final String errorCode;

    public TossPaymentException(
            Kind kind,
            int httpStatus,
            String errorCode
    ) {
        super("Toss 요청 실패: " + kind + ", code=" + errorCode);

        this.kind = kind;
        this.httpStatus = httpStatus;
        this.errorCode = errorCode;
    }

    public Kind getKind() {
        return kind;
    }

    public int getHttpStatus() {
        return httpStatus;
    }

    public String getErrorCode() {
        return errorCode;
    }
}