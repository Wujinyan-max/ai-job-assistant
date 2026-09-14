package com.jobassistant.common;

/**
 * AI 分析类型常量。
 */
public final class AiAnalysisType {

    /** JD 解析：把职位描述拆成技能、经验、学历等结构化字段 */
    public static final String JD_ANALYZE = "JD_ANALYZE";
    /** 简历匹配：简历与 JD 的匹配度分析 */
    public static final String RESUME_MATCH = "RESUME_MATCH";
    /** 面试题生成 */
    public static final String INTERVIEW_QUESTION = "INTERVIEW_QUESTION";

    private AiAnalysisType() {
    }
}
