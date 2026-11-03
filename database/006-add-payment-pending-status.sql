USE hanbaguni;

ALTER TABLE orders
    DROP CHECK chk_orders_status,
    ADD CONSTRAINT chk_orders_status
        CHECK (
            status IN (
                'PENDING',
                'PAYMENT_PENDING',
                'PAID',
                'CANCELLED',
                'REFUNDED',
                'EXPIRED'
            )
        );