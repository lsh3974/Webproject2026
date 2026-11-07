-- 12번 알림 기능: transactional outbox, 웹 알림함, 채널별 발송 상태, 사용자 설정, 브라우저 구독
-- 기존 DB의 001~012 적용을 완료한 뒤 한 번만 실행합니다.

ALTER TABLE users
    ADD COLUMN phone_verified_at DATETIME(6) NULL;

CREATE TABLE notification_events (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    event_key VARCHAR(191) NOT NULL,
    event_type VARCHAR(40) NOT NULL,
    recipient_user_id BIGINT UNSIGNED NOT NULL,
    order_id BIGINT UNSIGNED NULL,
    group_purchase_id BIGINT UNSIGNED NULL,
    title VARCHAR(160) NOT NULL,
    body VARCHAR(1000) NOT NULL,
    target_url VARCHAR(500) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    attempt_count INT NOT NULL DEFAULT 0,
    next_attempt_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    locked_until DATETIME(6) NULL,
    last_error VARCHAR(1000) NULL,
    processed_at DATETIME(6) NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    CONSTRAINT uk_notification_events_event_key UNIQUE (event_key),
    CONSTRAINT fk_notification_events_user FOREIGN KEY (recipient_user_id) REFERENCES users (id),
    CONSTRAINT fk_notification_events_order FOREIGN KEY (order_id) REFERENCES orders (id),
    CONSTRAINT fk_notification_events_group FOREIGN KEY (group_purchase_id) REFERENCES group_purchases (id),
    CONSTRAINT chk_notification_events_status CHECK (status IN ('PENDING', 'PROCESSING', 'PROCESSED', 'FAILED')),
    INDEX idx_notification_events_due (status, next_attempt_at, id),
    INDEX idx_notification_events_recipient (recipient_user_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE notifications (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    event_id BIGINT UNSIGNED NOT NULL,
    event_key VARCHAR(191) NOT NULL,
    user_id BIGINT UNSIGNED NOT NULL,
    event_type VARCHAR(40) NOT NULL,
    title VARCHAR(160) NOT NULL,
    body VARCHAR(1000) NOT NULL,
    target_url VARCHAR(500) NOT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    read_at DATETIME(6) NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_notifications_event UNIQUE (event_id),
    CONSTRAINT uk_notifications_event_key UNIQUE (event_key),
    CONSTRAINT fk_notifications_event FOREIGN KEY (event_id) REFERENCES notification_events (id),
    CONSTRAINT fk_notifications_user FOREIGN KEY (user_id) REFERENCES users (id),
    INDEX idx_notifications_user_unread (user_id, read_at, created_at),
    INDEX idx_notifications_user_list (user_id, created_at, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE notification_preferences (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    user_id BIGINT UNSIGNED NOT NULL,
    email_enabled BOOLEAN NOT NULL DEFAULT FALSE,
    sms_enabled BOOLEAN NOT NULL DEFAULT FALSE,
    push_enabled BOOLEAN NOT NULL DEFAULT FALSE,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    CONSTRAINT uk_notification_preferences_user UNIQUE (user_id),
    CONSTRAINT fk_notification_preferences_user FOREIGN KEY (user_id) REFERENCES users (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE notification_push_subscriptions (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    user_id BIGINT UNSIGNED NOT NULL,
    endpoint VARCHAR(1200) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    p256dh VARCHAR(200) NOT NULL,
    auth_secret VARCHAR(100) NOT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    last_used_at DATETIME(6) NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    PRIMARY KEY (id),
    CONSTRAINT uk_notification_push_endpoint UNIQUE (endpoint),
    CONSTRAINT fk_notification_push_user FOREIGN KEY (user_id) REFERENCES users (id),
    INDEX idx_notification_push_user (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE notification_deliveries (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    notification_id BIGINT UNSIGNED NOT NULL,
    channel VARCHAR(12) NOT NULL,
    target_key VARCHAR(100) NOT NULL DEFAULT 'default',
    push_subscription_id BIGINT UNSIGNED NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    attempt_count INT NOT NULL DEFAULT 0,
    reconciliation_count INT NOT NULL DEFAULT 0,
    next_attempt_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    locked_until DATETIME(6) NULL,
    provider_message_id VARCHAR(160) NULL,
    last_error VARCHAR(1000) NULL,
    sent_at DATETIME(6) NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    CONSTRAINT uk_notification_delivery_target UNIQUE (notification_id, channel, target_key),
    CONSTRAINT fk_notification_delivery_notification FOREIGN KEY (notification_id) REFERENCES notifications (id),
    CONSTRAINT fk_notification_delivery_subscription FOREIGN KEY (push_subscription_id) REFERENCES notification_push_subscriptions (id),
    CONSTRAINT chk_notification_delivery_channel CHECK (channel IN ('EMAIL', 'SMS', 'PUSH')),
    CONSTRAINT chk_notification_delivery_status CHECK (status IN ('PENDING', 'PROCESSING', 'RETRY', 'SENT', 'FAILED', 'UNKNOWN', 'SKIPPED')),
    INDEX idx_notification_delivery_due (status, next_attempt_at, id),
    INDEX idx_notification_delivery_subscription (push_subscription_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
