CREATE TABLE IF NOT EXISTS `oauth2_provider` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `name` VARCHAR(64) NOT NULL,
    `provider_key` VARCHAR(32) NOT NULL,
    `authorization_url` VARCHAR(512) NOT NULL,
    `token_url` VARCHAR(512) NOT NULL,
    `user_info_url` VARCHAR(512) NOT NULL,
    `scope` VARCHAR(128) DEFAULT NULL,
    `client_id` VARCHAR(256) NOT NULL,
    `client_secret` VARCHAR(512) NOT NULL,
    `icon_url` VARCHAR(512) DEFAULT NULL,
    `enabled` TINYINT NOT NULL DEFAULT 1,
    `is_preset` TINYINT NOT NULL DEFAULT 0,
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_provider_key` (`provider_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
