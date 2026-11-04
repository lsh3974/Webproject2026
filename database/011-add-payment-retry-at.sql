USE hanbaguni;

ALTER TABLE orders
    ADD COLUMN payment_retry_at DATETIME(6) NULL,
    ADD INDEX idx_orders_recovery (status, payment_retry_at, id);
