-- 「原版复刻」：导入 PDF 简历时把版式和头像一起取出来，排版页就能还原成原来的样子。
--   avatar     头像（640px 以内的 PNG data URL，约 20~60KB）
--   style_json 版式配色 JSON，结构见 ResumeStyleVO
-- 老数据保持 NULL，排版页会自动退回手选模板。
USE `ai_job_assistant`;

ALTER TABLE resume
    ADD COLUMN avatar MEDIUMTEXT DEFAULT NULL COMMENT '头像 data URL' AFTER content_json;

ALTER TABLE resume
    ADD COLUMN style_json TEXT DEFAULT NULL COMMENT '导入时提取的版式配色，结构见 ResumeStyleVO' AFTER avatar;
