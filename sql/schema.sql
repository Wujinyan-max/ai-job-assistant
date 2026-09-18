-- ============================================================================
--  AI 求职管理平台 (AI Job Assistant) 数据库初始化脚本
--  MySQL 8.0+    用法: mysql -uroot -p < sql/schema.sql
-- ============================================================================

CREATE DATABASE IF NOT EXISTS `ai_job_assistant`
    DEFAULT CHARACTER SET utf8mb4
    DEFAULT COLLATE utf8mb4_general_ci;

USE `ai_job_assistant`;
SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

DROP TABLE IF EXISTS `interview_question`;
DROP TABLE IF EXISTS `ai_analysis`;
DROP TABLE IF EXISTS `interview`;
DROP TABLE IF EXISTS `application`;
DROP TABLE IF EXISTS `job`;
DROP TABLE IF EXISTS `company`;
DROP TABLE IF EXISTS `resume`;
DROP TABLE IF EXISTS `ai_user_config`;
DROP TABLE IF EXISTS `user`;

-- ----------------------------------------------------------------------------
-- 1. user 用户表
-- ----------------------------------------------------------------------------
CREATE TABLE `user` (
    `id`         BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `username`   VARCHAR(50)  NOT NULL                COMMENT '登录用户名',
    `password`   VARCHAR(100) NOT NULL                COMMENT 'BCrypt 加密后的密码',
    `nickname`   VARCHAR(50)           DEFAULT NULL   COMMENT '昵称',
    `email`      VARCHAR(100)          DEFAULT NULL   COMMENT '邮箱',
    `phone`      VARCHAR(20)           DEFAULT NULL   COMMENT '手机号',
    `avatar`     VARCHAR(255)          DEFAULT NULL   COMMENT '头像地址',
    `education`  VARCHAR(20)           DEFAULT NULL   COMMENT '学历：大专/本科/硕士/博士',
    `work_years` INT                   DEFAULT 0      COMMENT '工作年限',
    `status`     TINYINT      NOT NULL DEFAULT 1      COMMENT '状态：1-正常 0-禁用',
    `created_at` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`    TINYINT      NOT NULL DEFAULT 0      COMMENT '逻辑删除：0-未删除 1-已删除',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_user_username` (`username`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '用户表';

-- ----------------------------------------------------------------------------
-- 2. ai_user_config 用户级 AI 配置（API Key 为 AES-GCM 密文）
-- ----------------------------------------------------------------------------
CREATE TABLE `ai_user_config` (
    `id`                BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `user_id`           BIGINT       NOT NULL COMMENT '所属用户',
    `provider`          VARCHAR(30)  NOT NULL DEFAULT 'OPENAI' COMMENT '厂商',
    `api_mode`          VARCHAR(30)  NOT NULL DEFAULT 'CHAT_COMPLETIONS' COMMENT 'API 模式',
    `base_url`          VARCHAR(500) NOT NULL COMMENT 'API Base URL',
    `api_key_encrypted` TEXT                  DEFAULT NULL COMMENT 'AES-GCM 加密后的 API Key',
    `model`             VARCHAR(100) NOT NULL COMMENT '模型名',
    `thinking_mode`     VARCHAR(20)  NOT NULL DEFAULT 'DEFAULT' COMMENT '思考模式：DEFAULT/OFF/ON',
    `input_price`       DECIMAL(12,6)         DEFAULT NULL COMMENT '输入（缓存未命中）单价，元/百万 tokens',
    `cache_price`       DECIMAL(12,6)         DEFAULT NULL COMMENT '输入（缓存命中）单价，元/百万 tokens',
    `output_price`      DECIMAL(12,6)         DEFAULT NULL COMMENT '输出单价，元/百万 tokens',
    `enabled`           TINYINT      NOT NULL DEFAULT 1 COMMENT '是否启用',
    `created_at`        DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at`        DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `deleted`           TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_ai_config_user` (`user_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '用户级 AI 配置';

-- ----------------------------------------------------------------------------
-- 2. resume 简历表（一个用户可以有多个版本）
-- ----------------------------------------------------------------------------
CREATE TABLE `resume` (
    `id`         BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `user_id`    BIGINT       NOT NULL                COMMENT '所属用户',
    `title`      VARCHAR(100) NOT NULL                COMMENT '简历名称，如 Java后端-社招版',
    `name`       VARCHAR(50)           DEFAULT NULL   COMMENT '姓名',
    `phone`      VARCHAR(20)           DEFAULT NULL   COMMENT '联系电话',
    `email`      VARCHAR(100)          DEFAULT NULL   COMMENT '联系邮箱',
    `education`  VARCHAR(20)           DEFAULT NULL   COMMENT '学历',
    `work_years` INT                   DEFAULT 0      COMMENT '工作年限',
    `skills`     VARCHAR(1000)         DEFAULT NULL   COMMENT '技能标签，逗号分隔',
    `summary`    VARCHAR(2000)         DEFAULT NULL   COMMENT '个人简介',
    `content`    LONGTEXT              DEFAULT NULL   COMMENT '完整简历正文（供 AI 分析）',
    `content_json` LONGTEXT            DEFAULT NULL   COMMENT '结构化简历 JSON（排版导出用），结构见 ResumeStructureVO',
    `avatar`     MEDIUMTEXT            DEFAULT NULL   COMMENT '头像 data URL（导入 PDF 时提取或用户上传）',
    `style_json` TEXT                  DEFAULT NULL   COMMENT '导入时提取的版式配色，结构见 ResumeStyleVO',
    `is_default` TINYINT      NOT NULL DEFAULT 0      COMMENT '是否默认简历：1-是 0-否',
    `created_at` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`    TINYINT      NOT NULL DEFAULT 0      COMMENT '逻辑删除',
    PRIMARY KEY (`id`),
    KEY `idx_resume_user` (`user_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '简历表';

-- ----------------------------------------------------------------------------
-- 3. company 公司表
-- ----------------------------------------------------------------------------
CREATE TABLE `company` (
    `id`         BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `user_id`    BIGINT       NOT NULL                COMMENT '所属用户',
    `name`       VARCHAR(100) NOT NULL                COMMENT '公司名称',
    `industry`   VARCHAR(50)           DEFAULT NULL   COMMENT '所属行业',
    `scale`      VARCHAR(50)           DEFAULT NULL   COMMENT '公司规模，如 500-999人',
    `city`       VARCHAR(50)           DEFAULT NULL   COMMENT '所在城市',
    `website`    VARCHAR(255)          DEFAULT NULL   COMMENT '官网',
    `status`     VARCHAR(20)  NOT NULL DEFAULT 'TARGET' COMMENT '状态 TARGET目标 CONTACTED已沟通 CLOSED已放弃',
    `remark`     VARCHAR(500)          DEFAULT NULL   COMMENT '备注',
    `created_at` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`    TINYINT      NOT NULL DEFAULT 0      COMMENT '逻辑删除',
    PRIMARY KEY (`id`),
    KEY `idx_company_user` (`user_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '公司表';

-- ----------------------------------------------------------------------------
-- 4. job 职位表
-- ----------------------------------------------------------------------------
CREATE TABLE `job` (
    `id`              BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `user_id`         BIGINT       NOT NULL                COMMENT '所属用户',
    `company_id`      BIGINT                DEFAULT NULL   COMMENT '关联公司',
    `job_name`        VARCHAR(100) NOT NULL                COMMENT '职位名称',
    `job_description` TEXT                  DEFAULT NULL   COMMENT '职位描述（JD 原文）',
    `salary_min`      INT                   DEFAULT NULL   COMMENT '最低月薪（K）',
    `salary_max`      INT                   DEFAULT NULL   COMMENT '最高月薪（K）',
    `salary_desc`     VARCHAR(50)           DEFAULT NULL   COMMENT '薪资描述自由文本，如 15-30K·14薪，优先于数字范围展示',
    `location`        VARCHAR(100)          DEFAULT NULL   COMMENT '工作地点',
    `job_url`         VARCHAR(500)          DEFAULT NULL   COMMENT '职位链接',
    `status`          VARCHAR(20)  NOT NULL DEFAULT 'OPEN' COMMENT '职位状态 OPEN在招 CLOSED已关闭',
    `created_at`      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at`      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`         TINYINT      NOT NULL DEFAULT 0      COMMENT '逻辑删除',
    PRIMARY KEY (`id`),
    KEY `idx_job_user` (`user_id`),
    KEY `idx_job_company` (`company_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '职位表';

-- ----------------------------------------------------------------------------
-- 5. application 投递记录表（求职流程核心表）
-- ----------------------------------------------------------------------------
CREATE TABLE `application` (
    `id`                 BIGINT      NOT NULL AUTO_INCREMENT COMMENT '主键',
    `user_id`            BIGINT      NOT NULL                COMMENT '所属用户',
    `job_id`             BIGINT      NOT NULL                COMMENT '关联职位',
    `resume_id`          BIGINT               DEFAULT NULL   COMMENT '使用的简历',
    `application_status` VARCHAR(20) NOT NULL DEFAULT 'WISHLIST' COMMENT 'WISHLIST收藏 APPLIED已投递 WRITTEN_TEST笔试 INTERVIEW面试 OFFER录用 REJECTED拒绝 CLOSED关闭',
    `apply_time`         DATETIME             DEFAULT NULL   COMMENT '投递时间',
    `source`             VARCHAR(50)          DEFAULT NULL   COMMENT '投递渠道 Boss直聘/拉勾/内推/官网',
    `remark`             VARCHAR(500)         DEFAULT NULL   COMMENT '备注',
    `created_at`         DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at`         DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`            TINYINT     NOT NULL DEFAULT 0      COMMENT '逻辑删除',
    PRIMARY KEY (`id`),
    KEY `idx_application_user` (`user_id`),
    KEY `idx_application_status` (`user_id`, `application_status`),
    KEY `idx_application_apply_time` (`user_id`, `apply_time`),
    KEY `idx_application_job` (`job_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '投递记录表';

-- ----------------------------------------------------------------------------
-- 6. interview 面试记录表
-- ----------------------------------------------------------------------------
CREATE TABLE `interview` (
    `id`             BIGINT      NOT NULL AUTO_INCREMENT COMMENT '主键',
    `user_id`        BIGINT      NOT NULL                COMMENT '所属用户',
    `application_id` BIGINT      NOT NULL                COMMENT '关联投递记录',
    `round_no`       INT         NOT NULL DEFAULT 1      COMMENT '第几轮面试',
    `round_name`     VARCHAR(50)          DEFAULT NULL   COMMENT '轮次名称，如 技术一面',
    `interview_type` VARCHAR(20)          DEFAULT 'VIDEO' COMMENT '形式 PHONE/VIDEO/ONSITE/WRITTEN',
    `interview_time` DATETIME             DEFAULT NULL   COMMENT '面试时间',
    `interviewer`    VARCHAR(50)          DEFAULT NULL   COMMENT '面试官',
    `location`       VARCHAR(255)         DEFAULT NULL   COMMENT '面试地点',
    `meeting_url`    VARCHAR(500)         DEFAULT NULL   COMMENT '线上会议链接',
    `result`         VARCHAR(20) NOT NULL DEFAULT 'PENDING' COMMENT '结果 PENDING待定 PASS通过 FAIL未通过',
    `review`         TEXT                 DEFAULT NULL   COMMENT '复盘总结',
    `created_at`     DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at`     DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`        TINYINT     NOT NULL DEFAULT 0      COMMENT '逻辑删除',
    PRIMARY KEY (`id`),
    KEY `idx_interview_user` (`user_id`),
    KEY `idx_interview_application` (`application_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '面试记录表';

-- ----------------------------------------------------------------------------
-- 7. ai_analysis AI 分析结果表（JD 分析 / 简历匹配）
-- ----------------------------------------------------------------------------
CREATE TABLE `ai_analysis` (
    `id`            BIGINT      NOT NULL AUTO_INCREMENT COMMENT '主键',
    `user_id`       BIGINT      NOT NULL                COMMENT '所属用户',
    `job_id`        BIGINT               DEFAULT NULL   COMMENT '关联职位',
    `resume_id`     BIGINT               DEFAULT NULL   COMMENT '关联简历',
    `analysis_type` VARCHAR(30) NOT NULL                COMMENT '类型 JD_ANALYZE / RESUME_MATCH / RESUME_OPTIMIZE / INTERVIEW_QUESTION',
    `model`         VARCHAR(50)          DEFAULT NULL   COMMENT '使用的模型',
    `score`         INT                  DEFAULT NULL   COMMENT '匹配分数 0-100',
    `input_tokens`  INT                  DEFAULT NULL   COMMENT '输入 token 数',
    `output_tokens` INT                  DEFAULT NULL   COMMENT '输出 token 数（含思维链）',
    `cached_tokens` INT                  DEFAULT NULL   COMMENT '输入中命中上下文缓存的 token 数',
    `reasoning_tokens` INT               DEFAULT NULL   COMMENT '思维链 token 数',
    `estimated_cost` DECIMAL(14,6)       DEFAULT NULL   COMMENT '按配置单价估算的费用（元）',
    `result`        TEXT                 DEFAULT NULL   COMMENT '分析结果 JSON',
    `created_at`    DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `deleted`       TINYINT     NOT NULL DEFAULT 0      COMMENT '逻辑删除',
    PRIMARY KEY (`id`),
    KEY `idx_ai_analysis_user` (`user_id`, `analysis_type`),
    KEY `idx_ai_analysis_job` (`job_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = 'AI 分析结果表';

-- ----------------------------------------------------------------------------
-- 8. interview_question AI 面试题表
-- ----------------------------------------------------------------------------
CREATE TABLE `interview_question` (
    `id`         BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `user_id`    BIGINT       NOT NULL                COMMENT '所属用户',
    `job_id`     BIGINT                DEFAULT NULL   COMMENT '关联职位',
    `category`   VARCHAR(50)           DEFAULT NULL   COMMENT '固定分类 编程语言与基础/框架与中间件/数据库与缓存/系统设计与性能/测试与质量/项目与业务/HR与软素质',
    `question`   VARCHAR(1000) NOT NULL              COMMENT '题目',
    `answer`     TEXT                  DEFAULT NULL   COMMENT '参考答案',
    `difficulty` VARCHAR(20)           DEFAULT 'MEDIUM' COMMENT '难度 EASY/MEDIUM/HARD',
    `source`     VARCHAR(20)           DEFAULT 'AI'   COMMENT '来源 AI / MANUAL',
    `mastered`   TINYINT      NOT NULL DEFAULT 0      COMMENT '是否已掌握',
    `created_at` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `deleted`    TINYINT      NOT NULL DEFAULT 0      COMMENT '逻辑删除',
    PRIMARY KEY (`id`),
    KEY `idx_question_user` (`user_id`),
    KEY `idx_question_job` (`job_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = 'AI 面试题表';

SET FOREIGN_KEY_CHECKS = 1;
