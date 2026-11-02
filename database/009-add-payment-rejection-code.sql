USE hanbaguni;

ALTER TABLE orders
    ADD COLUMN payment_rejection_code VARCHAR(100) NULL;