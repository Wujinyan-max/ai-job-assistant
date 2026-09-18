-- 简历排版导出：resume.content 是一段纯文本，没法直接排版成 A4 简历，
-- 所以新增 content_json 存「AI 识别 / 手工编辑」后的结构化简历（JSON），
-- 结构见 ResumeStructureVO：{ basics, education[], work[], projects[], skills[], honors[] }。
-- 老数据保持 NULL，前端打开排版页时会先用本地规则解析 content 再让用户确认。
ALTER TABLE resume
    ADD COLUMN content_json LONGTEXT DEFAULT NULL COMMENT '结构化简历 JSON（排版导出用），结构见 ResumeStructureVO'
        AFTER content;
