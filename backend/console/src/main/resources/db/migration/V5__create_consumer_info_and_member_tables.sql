-- V5__create_consumer_info_and_member_tables.sql

-- Console management metadata for consumers (not used by gateway core)
CREATE TABLE consumer_info (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    consumer_name   VARCHAR(128) NOT NULL COMMENT 'Gateway consumer name reference',
    name_cn         VARCHAR(128) NOT NULL COMMENT 'Chinese name',
    name_en         VARCHAR(128) NOT NULL COMMENT 'English name',
    short_name      VARCHAR(64) NOT NULL COMMENT 'Short name',
    description     VARCHAR(512) DEFAULT NULL COMMENT 'Description',
    status          VARCHAR(16) NOT NULL DEFAULT 'active' COMMENT 'active / disabled',
    created_at      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_consumer_name (consumer_name),
    UNIQUE KEY uk_short_name (short_name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Consumer group member mapping (Consumer contains Users)
CREATE TABLE consumer_member (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    consumer_name   VARCHAR(128) NOT NULL COMMENT 'Gateway consumer name',
    username        VARCHAR(64) NOT NULL COMMENT 'System user account',
    created_at      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_consumer_user (consumer_name, username),
    KEY idx_username (username)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
