package com.jobassistant.controller;

import com.jobassistant.common.Result;
import com.jobassistant.dto.GenerateQuestionDTO;
import com.jobassistant.dto.ExtractQuestionsDTO;
import com.jobassistant.dto.AiConfigSaveDTO;
import com.jobassistant.dto.AiModelQueryDTO;
import com.jobassistant.dto.JdAnalyzeDTO;
import com.jobassistant.dto.ResumeMatchDTO;
import com.jobassistant.dto.ResumeOptimizeDTO;
import com.jobassistant.dto.ResumeStructureDTO;
import com.jobassistant.entity.AiAnalysis;
import com.jobassistant.service.AiService;
import com.jobassistant.vo.AiConfigVO;
import com.jobassistant.vo.AiCallVO;
import com.jobassistant.vo.GeneratedQuestionVO;
import com.jobassistant.vo.JdAnalysisVO;
import com.jobassistant.vo.ResumeMatchVO;
import com.jobassistant.vo.ResumeOptimizeVO;
import com.jobassistant.vo.ResumeStructureVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import jakarta.validation.Valid;

import java.util.List;

@Tag(name = "08-AI 能力", description = "JD 解析 / 简历匹配 / 简历优化 / 面试题生成")
@RestController
@RequestMapping("/ai")
@RequiredArgsConstructor
public class AiController {

    private final AiService aiService;

    @Operation(summary = "AI 解析 JD",
            description = "传 jobId 用库里已保存的 JD，或直接传 jobDescription 粘贴文本；返回体带本次调用的 token 消耗")
    @PostMapping("/analyze-jd")
    public Result<AiCallVO<JdAnalysisVO>> analyzeJd(@RequestBody JdAnalyzeDTO dto) {
        return Result.success(aiService.analyzeJd(dto));
    }

    @Operation(summary = "AI 简历匹配度分析",
            description = "返回匹配分数、已匹配技能、缺失技能和优化建议；返回体带本次调用的 token 消耗")
    @PostMapping("/match-resume")
    public Result<AiCallVO<ResumeMatchVO>> matchResume(@RequestBody ResumeMatchDTO dto) {
        return Result.success(aiService.matchResume(dto));
    }

    @Operation(summary = "AI 生成面试题",
            description = "根据 JD + 简历出题，save=true 时同时保存到题库；返回体带本次调用的 token 消耗")
    @PostMapping("/generate-questions")
    public Result<AiCallVO<GeneratedQuestionVO>> generateQuestions(@RequestBody GenerateQuestionDTO dto) {
        return Result.success(aiService.generateQuestions(dto));
    }

    @Operation(summary = "从面试复盘提取题目",
            description = "把面试复盘文本拆成面试题并加入题库，自动关联职位分类；save=false 时只返回不落库")
    @PostMapping("/extract-questions")
    public Result<AiCallVO<GeneratedQuestionVO>> extractQuestions(@Valid @RequestBody ExtractQuestionsDTO dto) {
        return Result.success(aiService.extractQuestionsFromReview(dto));
    }

    @Operation(summary = "AI 简历专项优化",
            description = "按目标岗位改写用户粘贴的项目经历等素材，返回逐条改写、关键词覆盖和完整优化稿；save=true 时另存为一份新简历（不影响原简历）")
    @PostMapping("/optimize-resume")
    public Result<AiCallVO<ResumeOptimizeVO>> optimizeResume(@RequestBody ResumeOptimizeDTO dto) {
        return Result.success(aiService.optimizeResume(dto));
    }

    @Operation(summary = "AI 简历结构化",
            description = "把纯文本简历识别成固定结构（基本信息/教育/工作/项目/技能/荣誉），供前端排版 A4 简历；传 resumeId 时结果会写回该简历的 contentJson")
    @PostMapping("/structure-resume")
    public Result<AiCallVO<ResumeStructureVO>> structureResume(@RequestBody ResumeStructureDTO dto) {
        return Result.success(aiService.structureResume(dto));
    }
    @Operation(summary = "AI 分析历史")
    @GetMapping("/history")
    public Result<List<AiAnalysis>> history(@RequestParam(required = false) String type,
                                            @RequestParam(required = false) Long jobId,
                                            @RequestParam(required = false) Long resumeId,
                                            @RequestParam(defaultValue = "20") int limit) {
        return Result.success(aiService.history(type, jobId, resumeId, limit));
    }

    @Operation(summary = "查询 AI 配置",
            description = "返回 AI 是否开启、当前模型名，以及是否处于本地模拟模式（未配置 api-key）")
    @GetMapping("/config")
    public Result<AiConfigVO> config() {
        return Result.success(aiService.config());
    }

    @Operation(summary = "保存当前用户的 AI 配置")
    @PutMapping("/config")
    public Result<AiConfigVO> saveConfig(@Valid @RequestBody AiConfigSaveDTO dto) {
        return Result.success(aiService.saveConfig(dto));
    }

    @Operation(summary = "测试当前用户的 AI 配置")
    @PostMapping("/config/test")
    public Result<String> testConfig() {
        return Result.success(aiService.testConfig());
    }

    @Operation(summary = "拉取可选模型列表",
            description = "调用 GET {baseUrl}/models，返回该服务支持的模型名。请求体可传临时 baseUrl/apiKey，留空则用已保存的配置")
    @PostMapping("/models")
    public Result<List<String>> models(@RequestBody(required = false) AiModelQueryDTO dto) {
        return Result.success(aiService.listModels(
                dto == null ? new AiModelQueryDTO(null, null, null, null) : dto));
    }
}
