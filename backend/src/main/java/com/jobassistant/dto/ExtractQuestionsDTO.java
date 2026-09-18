package com.jobassistant.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "从面试复盘文本里提取面试题")
public record ExtractQuestionsDTO(
        @NotBlank(message = "请先填写面试复盘内容")
        @Size(max = 5000, message = "复盘内容最长 5000 个字符")
        String review,

        @Schema(description = "关联职位，用于给题目打上岗位分类")
        Long jobId,

        @Schema(description = "是否直接保存到题库，默认 true")
        Boolean save
) {
}
