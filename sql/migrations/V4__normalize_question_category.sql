-- 面试题库分类归一化：把模型自由发挥的分类名收敛成固定分类
-- 关键词与优先级同后端 com.jobassistant.ai.QuestionCategory#normalize 保持一致：
--   HR → 项目 → 测试 → 数据库 → 框架 → 语言 → 系统设计
-- 脚本幂等：固定分类名本身不会再命中前面的规则，重复执行结果不变。
UPDATE interview_question
SET category = CASE
    WHEN LOWER(category) LIKE '%hr%' OR category LIKE '%软素质%' OR category LIKE '%沟通%'
        OR category LIKE '%职业规划%' OR category LIKE '%离职%' OR category LIKE '%加班%'
        OR category LIKE '%抗压%' OR category LIKE '%团队协作%' OR category LIKE '%自我介绍%'
        OR category LIKE '%人力资源%'
        THEN 'HR与软素质'
    WHEN category LIKE '%项目%' OR category LIKE '%业务%' OR category LIKE '%简历%'
        OR category LIKE '%工作经历%' OR category LIKE '%实习经历%'
        THEN '项目与业务'
    WHEN category LIKE '%测试%' OR category LIKE '%用例%' OR category LIKE '%质量%'
        OR category LIKE '%缺陷%' OR LOWER(category) LIKE '%bug%' OR LOWER(category) LIKE '%qa%'
        THEN '测试与质量'
    WHEN category LIKE '%数据库%' OR category LIKE '%缓存%' OR category LIKE '%索引%'
        OR category LIKE '%慢查询%' OR category LIKE '%分库分表%' OR LOWER(category) LIKE '%sql%'
        OR LOWER(category) LIKE '%mysql%' OR LOWER(category) LIKE '%redis%'
        OR LOWER(category) LIKE '%mongo%' OR LOWER(category) LIKE '%oracle%'
        OR LOWER(category) LIKE '%postgres%'
        THEN '数据库与缓存'
    WHEN category LIKE '%框架%' OR category LIKE '%中间件%' OR category LIKE '%消息队列%'
        OR category LIKE '%微服务%' OR category LIKE '%前端%'
        OR LOWER(category) LIKE '%spring%' OR LOWER(category) LIKE '%mybatis%'
        OR LOWER(category) LIKE '%kafka%' OR LOWER(category) LIKE '%rabbitmq%'
        OR LOWER(category) LIKE '%rocketmq%' OR LOWER(category) LIKE '%dubbo%'
        OR LOWER(category) LIKE '%netty%' OR LOWER(category) LIKE '%tomcat%'
        OR LOWER(category) LIKE '%nginx%' OR LOWER(category) LIKE '%docker%'
        OR LOWER(category) LIKE '%kubernetes%' OR LOWER(category) LIKE '%vue%'
        OR LOWER(category) LIKE '%react%' OR LOWER(category) LIKE '%node%'
        THEN '框架与中间件'
    WHEN category LIKE '%基础%' OR category LIKE '%语言%' OR category LIKE '%语法%'
        OR category LIKE '%面向对象%' OR category LIKE '%集合%' OR category LIKE '%线程%'
        OR category LIKE '%并发编程%' OR LOWER(category) LIKE '%java%'
        OR LOWER(category) LIKE '%python%' OR LOWER(category) LIKE '%golang%'
        OR LOWER(category) LIKE '%jvm%' OR LOWER(category) LIKE '%gc%'
        THEN '编程语言与基础'
    WHEN category LIKE '%高并发%' OR category LIKE '%高可用%' OR category LIKE '%架构%'
        OR category LIKE '%设计%' OR category LIKE '%分布式%' OR category LIKE '%性能%'
        OR category LIKE '%限流%' OR category LIKE '%降级%' OR category LIKE '%容灾%'
        THEN '系统设计与性能'
    WHEN category = '综合' OR category = ''
        THEN '其他'
    ELSE category
END
WHERE deleted = 0 AND category IS NOT NULL;
