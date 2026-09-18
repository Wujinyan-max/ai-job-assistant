package com.jobassistant.ai;

/**
 * AI 任务类型。真实模型按提示词区分，本地模拟实现按这个枚举区分。
 */
public enum AiTask {

    /** 解析 JD，产出结构化岗位画像 */
    JD_ANALYZE,

    /** 简历与 JD 匹配度分析 */
    RESUME_MATCH,

    /** 简历专项优化：按目标岗位改写用户提供的项目经历等素材 */
    RESUME_OPTIMIZE,

    /** 简历结构化：把纯文本简历识别成可排版的固定 JSON 结构 */
    RESUME_STRUCTURE,

    /** 简历视觉识别：直接从导入 PDF 渲染出的页面图片里读出内容结构与版式 */
    RESUME_VISION,

    /** 生成面试题 */
    INTERVIEW_QUESTION,

    /** 从面试复盘文本里提取题目，沉淀到题库 */
    REVIEW_QUESTIONS
}
