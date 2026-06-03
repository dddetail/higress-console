-- V4__add_user_role_column.sql
ALTER TABLE `user` ADD COLUMN `role` VARCHAR(32) NOT NULL DEFAULT 'reader'
    COMMENT 'platform_admin / owner / manager / reader' AFTER `status`;

-- Set the initial admin user as platform_admin
UPDATE `user` SET `role` = 'platform_admin' WHERE `type` = 'admin' OR `username` = 'admin';
