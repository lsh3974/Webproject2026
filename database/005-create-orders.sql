USE hanbaguni;

CREATE TABLE IF NOT EXISTS orders (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    user_id BIGINT UNSIGNED NOT NULL,
    group_purchase_id BIGINT UNSIGNED NOT NULL,
    quantity INT NOT NULL,
    unit_price INT NOT NULL DEFAULT 0,
    total_price INT NOT NULL,
    status VARCHAR(30) NOT NULL DEFAULT 'PENDING', -- PENDING(예약대기), PAID(결제완료), CANCELLED(취소), REFUNDED(환불), EXPIRED(예약만료)
    reserved_until DATETIME(6) NULL,               -- 선결제 완료 기한 (수량 예약 만료 시각)
    payment_key VARCHAR(255) NULL,                 -- 결제 키 (토스페이먼츠 등 외부 PG 연동 대비)
    cancel_reason VARCHAR(255) NULL,               -- 취소 또는 만료 사유
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
        ON UPDATE CURRENT_TIMESTAMP(6),

    PRIMARY KEY (id),

    CONSTRAINT fk_orders_user
        FOREIGN KEY (user_id) REFERENCES users (id)
        ON DELETE RESTRICT ON UPDATE CASCADE,

    CONSTRAINT fk_orders_group_purchase
        FOREIGN KEY (group_purchase_id) REFERENCES group_purchases (id)
        ON DELETE RESTRICT ON UPDATE CASCADE,

    CONSTRAINT chk_orders_quantity
        CHECK (quantity >= 1),

    CONSTRAINT chk_orders_price
        CHECK (unit_price >= 0 AND total_price >= 0),

    CONSTRAINT chk_orders_status
        CHECK (status IN ('PENDING', 'PAID', 'CANCELLED', 'REFUNDED', 'EXPIRED')),

    INDEX idx_orders_user (user_id),
    INDEX idx_orders_group_purchase (group_purchase_id),
    INDEX idx_orders_gp_user_status (group_purchase_id, user_id, status),
    INDEX idx_orders_status_reserved (status, reserved_until)
) ENGINE=InnoDB;
