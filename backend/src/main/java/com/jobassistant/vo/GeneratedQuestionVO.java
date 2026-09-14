package com.jobassistant.vo;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(description = "AI 生成的面试题列表")
public record GeneratedQuestionVO(
        List<InterviewQuestionVO> questions,
        @Schema(description = "保存到题库的题目数量")
        int savedCount
) {
}
