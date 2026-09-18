-- 题库分类补齐：新题的 category 一定由后端写入，但历史数据里可能留着 NULL / 空串。
-- 题库页改成「按分类平铺、点进去看题目」之后，没有分类的题会变成谁都不认领的孤儿题，
-- 所以这里统一补成兜底分类「其他」，脚本幂等，重复执行不会影响已有数据。
UPDATE interview_question
SET category = '其他'
WHERE deleted = 0 AND (category IS NULL OR TRIM(category) = '');
