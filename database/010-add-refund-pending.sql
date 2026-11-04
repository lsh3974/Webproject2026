USE hanbaguni;

ALTER TABLE orders
    DROP CHECK chk_orders_status,
    ADD CONSTRAINT chk_orders_status
        CHECK (
            status IN (
                'PENDING',
                'PAYMENT_PENDING',
                'PAID',
                'REFUND_PENDING',
                'CANCELLED',
                'REFUNDED',
                'EXPIRED'
            )
        ),
    ADD COLUMN refund_request_id VARCHAR(64) NULL,
    ADD CONSTRAINT uk_orders_refund_request_id
        UNIQUE (refund_request_id);