package com.jobassistant.service;

import com.jobassistant.dto.GenerateQuestionDTO;
import com.jobassistant.dto.JdAnalyzeDTO;
import com.jobassistant.dto.ResumeMatchDTO;
import com.jobassistant.entity.AiAnalysis;
import com.jobassistant.vo.AiConfigVO;
import com.jobassistant.vo.GeneratedQuestionVO;
import com.jobassistant.vo.JdAnalysisVO;
import com.jobassistant.vo.ResumeMatchVO;

import java.util.List;

public interface AiService {

    /** 解析 JD，提取技能、经验、关键词等结构化信息 */
    JdAnalysisVO analyzeJd(JdAnalyzeDTO dto);

    /** 简历与 JD 的匹配度分析 */
    ResumeMatchVO matchResume(ResumeMatchDTO dto);

    /** 生成面试题 */
    GeneratedQuestionVO generateQuestions(GenerateQuestionDTO dto);

    /** AI 分析历史 */
    List<AiAnalysis> history(String analysisType, int limit);

    /** 当前 AI 配置，用于前端展示"真实模型 / 本地模拟" */
    AiConfigVO config();

    /** 保存当前用户的 AI 配置。 */
    AiConfigVO saveConfig(com.jobassistant.dto.AiConfigSaveDTO dto);

    /** 使用当前用户配置发送最小请求，验证连接。 */
    String testConfig();
}
