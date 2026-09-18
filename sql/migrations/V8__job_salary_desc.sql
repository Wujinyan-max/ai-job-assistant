USE `ai_job_assistant`;

-- 薪资支持自由文本（如「15-30K·14薪」），与 salary_min / salary_max 并存，列表优先展示描述
ALTER TABLE `job`
    ADD COLUMN `salary_desc` VARCHAR(50) DEFAULT NULL
        COMMENT '薪资描述自由文本，如 15-30K·14薪，优先于数字范围展示' AFTER `salary_max`;
