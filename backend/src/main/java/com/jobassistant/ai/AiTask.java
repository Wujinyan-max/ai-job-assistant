package com.jobassistant.ai;

/**
 * AI 任务类型。真实模型按提示词区分，本地模拟实现按这个枚举区分。
 */
public enum AiTask {

    /** 解析 JD，产出结构化岗位画像 */
    JD_ANALYZE,

    /** 简历与 JD 匹配度分析 */
    RESUME_MATCH,

    /** 生成面试题 */
    INTERVIEW_QUESTION
}
