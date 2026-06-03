-- V5__create_consumer_group_tables.sql
CREATE TABLE consumer_group (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    name_cn         VARCHAR(128) NOT NULL COMMENT '中文名称',
    name_en         VARCHAR(128) NOT NULL COMMENT '英文名称',
    short_name      VARCHAR(64) NOT NULL COMMENT '简称',
    description     VARCHAR(512) DEFAULT NULL COMMENT '描述',
    status          VARCHAR(16) NOT NULL DEFAULT 'active' COMMENT 'active / disabled',
    created_at      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_short_name (short_name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE consumer_group_member (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    group_id        BIGINT NOT NULL COMMENT '消费者组 ID',
    username        VARCHAR(64) NOT NULL COMMENT '系统用户账号',
    created_at      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_group_user (group_id, username),
    KEY idx_username (username)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE consumer_group_api_grant (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    group_id        BIGINT NOT NULL COMMENT '消费者组 ID',
    resource_type   VARCHAR(32) NOT NULL COMMENT 'route',
    resource_name   VARCHAR(128) NOT NULL COMMENT '路由名称',
    created_at      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_group_resource (group_id, resource_type, resource_name),
    KEY idx_resource (resource_type, resource_name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
