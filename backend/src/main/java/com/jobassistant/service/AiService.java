package com.jobassistant.service;

import com.jobassistant.dto.GenerateQuestionDTO;
import com.jobassistant.dto.ExtractQuestionsDTO;
import com.jobassistant.dto.JdAnalyzeDTO;
import com.jobassistant.dto.ResumeMatchDTO;
import com.jobassistant.dto.ResumeOptimizeDTO;
import com.jobassistant.dto.ResumeStructureDTO;
import com.jobassistant.entity.AiAnalysis;
import com.jobassistant.vo.AiConfigVO;
import com.jobassistant.vo.AiCallVO;
import com.jobassistant.vo.GeneratedQuestionVO;
import com.jobassistant.vo.JdAnalysisVO;
import com.jobassistant.vo.ResumeMatchVO;
import com.jobassistant.vo.ResumeOptimizeVO;
import com.jobassistant.vo.ResumeStructureVO;

import java.util.List;

public interface AiService {

    /** 解析 JD，提取技能、经验、关键词等结构化信息 */
    AiCallVO<JdAnalysisVO> analyzeJd(JdAnalyzeDTO dto);

    /** 简历与 JD 的匹配度分析 */
    AiCallVO<ResumeMatchVO> matchResume(ResumeMatchDTO dto);

    /** 简历专项优化：按目标岗位改写用户提供的项目经历等素材 */
    AiCallVO<ResumeOptimizeVO> optimizeResume(ResumeOptimizeDTO dto);

    /** 简历结构化：把纯文本简历识别成可排版的固定结构 */
    AiCallVO<ResumeStructureVO> structureResume(ResumeStructureDTO dto);

    /** 生成面试题 */
    AiCallVO<GeneratedQuestionVO> generateQuestions(GenerateQuestionDTO dto);

    /** 从面试复盘文本里提取面试题，可选直接入库 */
    AiCallVO<GeneratedQuestionVO> extractQuestionsFromReview(ExtractQuestionsDTO dto);

    /** AI 分析历史 */
    List<AiAnalysis> history(String analysisType, Long jobId, Long resumeId, int limit);

    /** 当前 AI 配置，用于前端展示"真实模型 / 本地模拟" */
    AiConfigVO config();

    /** 保存当前用户的 AI 配置。 */
    AiConfigVO saveConfig(com.jobassistant.dto.AiConfigSaveDTO dto);

    /** 使用当前用户配置发送最小请求，验证连接。 */
    String testConfig();

    /** 拉取该配置可用的模型列表，供前端筛选。 */
    List<String> listModels(com.jobassistant.dto.AiModelQueryDTO dto);
}
