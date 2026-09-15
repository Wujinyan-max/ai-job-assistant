package com.jobassistant.controller;

import com.jobassistant.common.Result;
import com.jobassistant.dto.GenerateQuestionDTO;
import com.jobassistant.dto.AiConfigSaveDTO;
import com.jobassistant.dto.JdAnalyzeDTO;
import com.jobassistant.dto.ResumeMatchDTO;
import com.jobassistant.entity.AiAnalysis;
import com.jobassistant.service.AiService;
import com.jobassistant.vo.AiConfigVO;
import com.jobassistant.vo.GeneratedQuestionVO;
import com.jobassistant.vo.JdAnalysisVO;
import com.jobassistant.vo.ResumeMatchVO;
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

@Tag(name = "08-AI 能力", description = "JD 解析 / 简历匹配 / 面试题生成")
@RestController
@RequestMapping("/ai")
@RequiredArgsConstructor
public class AiController {

    private final AiService aiService;

    @Operation(summary = "AI 解析 JD",
            description = "传 jobId 用库里已保存的 JD，或直接传 jobDescription 粘贴文本")
    @PostMapping("/analyze-jd")
    public Result<JdAnalysisVO> analyzeJd(@RequestBody JdAnalyzeDTO dto) {
        return Result.success(aiService.analyzeJd(dto));
    }

    @Operation(summary = "AI 简历匹配度分析",
            description = "返回匹配分数、已匹配技能、缺失技能和优化建议")
    @PostMapping("/match-resume")
    public Result<ResumeMatchVO> matchResume(@RequestBody ResumeMatchDTO dto) {
        return Result.success(aiService.matchResume(dto));
    }

    @Operation(summary = "AI 生成面试题",
            description = "根据 JD + 简历出题，save=true 时同时保存到题库")
    @PostMapping("/generate-questions")
    public Result<GeneratedQuestionVO> generateQuestions(@RequestBody GenerateQuestionDTO dto) {
        return Result.success(aiService.generateQuestions(dto));
    }

    @Operation(summary = "AI 分析历史")
    @GetMapping("/history")
    public Result<List<AiAnalysis>> history(@RequestParam(required = false) String type,
                                            @RequestParam(defaultValue = "20") int limit) {
        return Result.success(aiService.history(type, limit));
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
}
