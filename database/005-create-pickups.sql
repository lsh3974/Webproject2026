USE hanbaguni;

CREATE TABLE IF NOT EXISTS pickups (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    store_id BIGINT UNSIGNED NOT NULL,
    group_purchase_id BIGINT UNSIGNED NOT NULL,
    user_id BIGINT UNSIGNED NOT NULL,
    pickup_code VARCHAR(32) NOT NULL,
    quantity INT NOT NULL DEFAULT 1,
    status VARCHAR(20) NOT NULL DEFAULT 'PREPARING',
    customer_name VARCHAR(100) NULL,
    customer_phone VARCHAR(20) NULL,
    picked_up_at DATETIME(6) NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
        ON UPDATE CURRENT_TIMESTAMP(6),

    PRIMARY KEY (id),

    CONSTRAINT uq_pickups_pickup_code
        UNIQUE (pickup_code),

    CONSTRAINT fk_pickups_store
        FOREIGN KEY (store_id) REFERENCES stores (id)
        ON DELETE CASCADE ON UPDATE CASCADE,

    CONSTRAINT fk_pickups_group_purchase
        FOREIGN KEY (group_purchase_id) REFERENCES group_purchases (id)
        ON DELETE CASCADE ON UPDATE CASCADE,

    CONSTRAINT fk_pickups_user
        FOREIGN KEY (user_id) REFERENCES users (id)
        ON DELETE CASCADE ON UPDATE CASCADE,

    CONSTRAINT chk_pickups_quantity
        CHECK (quantity >= 1),

    CONSTRAINT chk_pickups_status
        CHECK (status IN ('PREPARING', 'READY', 'PICKED_UP', 'CANCELLED')),

    INDEX idx_pickups_store (store_id),
    INDEX idx_pickups_group_purchase (group_purchase_id),
    INDEX idx_pickups_code (pickup_code),
    INDEX idx_pickups_status (status)
) ENGINE=InnoDB;
