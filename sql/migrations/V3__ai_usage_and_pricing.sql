-- 用量统计与费用估算：
--   1) ai_analysis 记录每次调用的 token 消耗与预估费用，便于回看历史用量；
--   2) ai_user_config 增加思考模式开关与单价配置，单价用于估算费用（元 / 百万 tokens）。
-- 非破坏性迁移，老库执行一次即可：
--   mysql -uroot -p < sql/migrations/V3__ai_usage_and_pricing.sql

USE `ai_job_assistant`;

ALTER TABLE `ai_analysis`
    ADD COLUMN `input_tokens`     INT            DEFAULT NULL COMMENT '输入 token 数' AFTER `score`,
    ADD COLUMN `output_tokens`    INT            DEFAULT NULL COMMENT '输出 token 数（含思维链）' AFTER `input_tokens`,
    ADD COLUMN `cached_tokens`    INT            DEFAULT NULL COMMENT '输入中命中上下文缓存的 token 数' AFTER `output_tokens`,
    ADD COLUMN `reasoning_tokens` INT            DEFAULT NULL COMMENT '思维链 token 数' AFTER `cached_tokens`,
    ADD COLUMN `estimated_cost`   DECIMAL(14, 6) DEFAULT NULL COMMENT '按配置单价估算的费用（元）' AFTER `reasoning_tokens`;

ALTER TABLE `ai_user_config`
    ADD COLUMN `thinking_mode` VARCHAR(20)    NOT NULL DEFAULT 'DEFAULT' COMMENT '思考模式：DEFAULT/OFF/ON' AFTER `model`,
    ADD COLUMN `input_price`   DECIMAL(12, 6) DEFAULT NULL COMMENT '输入（缓存未命中）单价，元/百万 tokens' AFTER `thinking_mode`,
    ADD COLUMN `cache_price`   DECIMAL(12, 6) DEFAULT NULL COMMENT '输入（缓存命中）单价，元/百万 tokens' AFTER `input_price`,
    ADD COLUMN `output_price`  DECIMAL(12, 6) DEFAULT NULL COMMENT '输出单价，元/百万 tokens' AFTER `cache_price`;
