USE `ai_job_assistant`;

CREATE TABLE IF NOT EXISTS `ai_user_config` (
    `id`                BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `user_id`           BIGINT       NOT NULL COMMENT '所属用户',
    `provider`          VARCHAR(30)  NOT NULL DEFAULT 'OPENAI' COMMENT '厂商',
    `api_mode`          VARCHAR(30)  NOT NULL DEFAULT 'CHAT_COMPLETIONS' COMMENT 'API 模式',
    `base_url`          VARCHAR(500) NOT NULL COMMENT 'API Base URL',
    `api_key_encrypted` TEXT                  DEFAULT NULL COMMENT 'AES-GCM 加密后的 API Key',
    `model`             VARCHAR(100) NOT NULL COMMENT '模型名',
    `enabled`           TINYINT      NOT NULL DEFAULT 1 COMMENT '是否启用',
    `created_at`        DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at`        DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `deleted`           TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_ai_config_user` (`user_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '用户级 AI 配置';
