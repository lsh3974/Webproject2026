USE hanbaguni;

CREATE TABLE IF NOT EXISTS stores (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    seller_id BIGINT UNSIGNED NOT NULL,
    name VARCHAR(100) NOT NULL,
    business_number VARCHAR(30) NULL,
    phone VARCHAR(20) NOT NULL,
    address VARCHAR(255) NOT NULL,
    address_detail VARCHAR(255) NULL,
    description TEXT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
        ON UPDATE CURRENT_TIMESTAMP(6),

    PRIMARY KEY (id),

    CONSTRAINT fk_stores_seller
        FOREIGN KEY (seller_id) REFERENCES users (id)
        ON DELETE RESTRICT ON UPDATE CASCADE,

    CONSTRAINT chk_stores_status
        CHECK (status IN ('ACTIVE', 'CLOSED', 'SUSPENDED')),

    INDEX idx_stores_seller (seller_id)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS products (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    store_id BIGINT UNSIGNED NOT NULL,
    name VARCHAR(150) NOT NULL,
    category VARCHAR(50) NOT NULL,
    regular_price INT NOT NULL,
    unit VARCHAR(50) NOT NULL,
    description TEXT NULL,
    image_url VARCHAR(500) NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'ON_SALE',
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
        ON UPDATE CURRENT_TIMESTAMP(6),

    PRIMARY KEY (id),

    CONSTRAINT fk_products_store
        FOREIGN KEY (store_id) REFERENCES stores (id)
        ON DELETE CASCADE ON UPDATE CASCADE,

    CONSTRAINT chk_products_regular_price
        CHECK (regular_price >= 0),

    CONSTRAINT chk_products_status
        CHECK (status IN ('ON_SALE', 'SOLD_OUT', 'HIDDEN')),

    INDEX idx_products_store (store_id),
    INDEX idx_products_category (category)
) ENGINE=InnoDB;
