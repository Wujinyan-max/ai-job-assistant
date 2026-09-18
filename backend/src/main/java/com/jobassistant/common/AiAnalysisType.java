package com.jobassistant.common;

/**
 * AI 分析类型常量。
 */
public final class AiAnalysisType {

    /** JD 解析：把职位描述拆成技能、经验、学历等结构化字段 */
    public static final String JD_ANALYZE = "JD_ANALYZE";
    /** 简历匹配：简历与 JD 的匹配度分析 */
    public static final String RESUME_MATCH = "RESUME_MATCH";
    /** 简历优化：按目标岗位专项改写项目经历等素材 */
    public static final String RESUME_OPTIMIZE = "RESUME_OPTIMIZE";
    /** 简历结构化：把纯文本简历识别成排版用的固定结构 */
    public static final String RESUME_STRUCTURE = "RESUME_STRUCTURE";
    /** 面试题生成 */
    public static final String INTERVIEW_QUESTION = "INTERVIEW_QUESTION";
    /** 面试复盘提取题目 */
    public static final String REVIEW_QUESTIONS = "REVIEW_QUESTIONS";

    private AiAnalysisType() {
    }
}
