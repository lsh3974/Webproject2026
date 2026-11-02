USE hanbaguni;

ALTER TABLE orders
    ADD COLUMN payment_order_ref VARCHAR(64) NULL,
    ADD CONSTRAINT uk_orders_payment_order_ref
        UNIQUE (payment_order_ref);