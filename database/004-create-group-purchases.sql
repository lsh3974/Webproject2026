USE hanbaguni;

CREATE TABLE IF NOT EXISTS group_purchases (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    store_id BIGINT UNSIGNED NOT NULL,
    product_id BIGINT UNSIGNED NOT NULL,
    title VARCHAR(150) NOT NULL,
    group_price INT NOT NULL,
    target_quantity INT NOT NULL,
    max_quantity INT NOT NULL,
    current_quantity INT NOT NULL DEFAULT 0,
    limit_per_user INT NULL,
    start_at DATETIME(6) NOT NULL,
    end_at DATETIME(6) NOT NULL,
    pickup_start_at DATETIME(6) NOT NULL,
    pickup_end_at DATETIME(6) NOT NULL,
    pickup_notice VARCHAR(500) NULL,
    description TEXT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
        ON UPDATE CURRENT_TIMESTAMP(6),

    PRIMARY KEY (id),

    CONSTRAINT fk_group_purchases_store
        FOREIGN KEY (store_id) REFERENCES stores (id)
        ON DELETE CASCADE ON UPDATE CASCADE,

    CONSTRAINT fk_group_purchases_product
        FOREIGN KEY (product_id) REFERENCES products (id)
        ON DELETE RESTRICT ON UPDATE CASCADE,

    CONSTRAINT chk_group_purchases_price
        CHECK (group_price >= 0),

    CONSTRAINT chk_group_purchases_quantity
        CHECK (target_quantity >= 1 AND max_quantity >= target_quantity AND current_quantity >= 0 AND (limit_per_user IS NULL OR limit_per_user >= 1)),

    CONSTRAINT chk_group_purchases_status
        CHECK (status IN ('DRAFT', 'OPEN', 'SUCCESS', 'FAILED', 'CLOSED')),

    INDEX idx_group_purchases_store (store_id),
    INDEX idx_group_purchases_product (product_id),
    INDEX idx_group_purchases_status_end (status, end_at)
) ENGINE=InnoDB;
