USE `ai_job_assistant`;

-- 单点登录：每次登录刷新 token_id，旧 token 立即失效
ALTER TABLE `user`
    ADD COLUMN `token_id` VARCHAR(64) DEFAULT NULL COMMENT '当前有效登录态 ID，新登录时刷新' AFTER `status`;
