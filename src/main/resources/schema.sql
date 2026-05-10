-- ============================================================
-- 数据库初始化脚本
-- 负责人：同学 C
-- ============================================================
CREATE DATABASE IF NOT EXISTS express_db
    DEFAULT CHARACTER SET utf8mb4
    COLLATE utf8mb4_unicode_ci;

USE express_db;

DROP TABLE IF EXISTS `users`;
CREATE TABLE `users` (
    `id`            BIGINT       NOT NULL AUTO_INCREMENT,
    `name`          VARCHAR(64)  NOT NULL,
    `phone`         VARCHAR(20)  NOT NULL,
    `password`      VARCHAR(256) NOT NULL,
    `avatar`        VARCHAR(512),
    `role`          VARCHAR(20)  NOT NULL DEFAULT 'USER',
    -- 超时机制（仅对 COURIER 有意义，USER 始终为 0/NULL）
    `overdue_count` INT          NOT NULL DEFAULT 0   COMMENT '快递员累计超时次数(达5触发封禁后清零)',
    `ban_until`     DATETIME     NULL                 COMMENT '封禁到期时间(NULL=未封禁)；封禁期间不能抢新单',
    `deleted`       TINYINT      NOT NULL DEFAULT 0,
    `create_time`   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `update_time`   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_phone` (`phone`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户表';

DROP TABLE IF EXISTS `orders`;
CREATE TABLE `orders` (
    `id`               BIGINT         NOT NULL AUTO_INCREMENT,
    `user_id`          BIGINT         NOT NULL,
    `courier_id`       BIGINT,
    `station_address`  VARCHAR(256)   NOT NULL,
    `target_address`   VARCHAR(256)   NOT NULL,
    `pickup_code`      VARCHAR(32)    NOT NULL,
    `amount`           DECIMAL(10, 2) NOT NULL DEFAULT 0.00,
    `status`           TINYINT        NOT NULL DEFAULT 0 COMMENT '0待抢 1配送中 2已完成 3已取消',
    -- 紧急度与时效承诺
    `urgency`          TINYINT        NOT NULL DEFAULT 0 COMMENT '0普通 1紧急',
    `promised_minutes` INT            NULL                COMMENT '快递员抢单时承诺的送达分钟数(NULL=未抢)',
    `grab_time`        DATETIME       NULL                COMMENT '抢单时刻',
    `deadline`         DATETIME       NULL                COMMENT '送达截止时刻 = grab_time + promised_minutes',
    `overdue`          TINYINT        NOT NULL DEFAULT 0 COMMENT '是否已被认定超时(0/1)；用 CAS 0->1 保证只触发一次结算',
    `deleted`          TINYINT        NOT NULL DEFAULT 0,
    `create_time`      DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `update_time`      DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_user_id`     (`user_id`),
    KEY `idx_courier_id`  (`courier_id`),
    KEY `idx_status_time` (`status`, `create_time`),
    -- 给定时扫描超时单用：WHERE status=1 AND overdue=0 AND deadline<NOW()
    KEY `idx_overdue_scan` (`status`, `overdue`, `deadline`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='订单表';
