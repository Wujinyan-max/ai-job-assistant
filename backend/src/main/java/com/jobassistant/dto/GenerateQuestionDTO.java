package com.jobassistant.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(description = "AI 生成面试题请求")
public record GenerateQuestionDTO(
        Long jobId,
        Long resumeId,

        @Schema(description = "期望的题目分类，默认按岗位技能自动决定")
        List<String> categories,

        @Schema(description = "每个分类出几道题，默认 3，最多 5")
        Integer countPerCategory,

        @Schema(description = "难度：EASY / MEDIUM / HARD，默认 MEDIUM")
        String difficulty,

        @Schema(description = "是否把生成的题目保存到题库")
        Boolean save
) {
}
