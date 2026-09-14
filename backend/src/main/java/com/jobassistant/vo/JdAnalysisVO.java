package com.jobassistant.vo;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * JD 解析结果，字段与 AI 提示词里约定的 JSON 结构一致。
 */
@Schema(description = "JD 解析结果")
public record JdAnalysisVO(
        @Schema(description = "岗位要求的技术栈")
        List<String> skills,

        @Schema(description = "关键词（业务方向、加分项等）")
        List<String> keywords,

        @Schema(description = "经验要求，如 3-5年")
        String experience,

        @Schema(description = "学历要求")
        String education,

        @Schema(description = "职级判断：初级/中级/高级/专家")
        String seniority,

        @Schema(description = "主要职责")
        List<String> responsibilities,

        @Schema(description = "一句话总结这个岗位在找什么人")
        String summary
) {
    public JdAnalysisVO {
        skills = skills == null ? List.of() : skills;
        keywords = keywords == null ? List.of() : keywords;
        responsibilities = responsibilities == null ? List.of() : responsibilities;
    }
}
