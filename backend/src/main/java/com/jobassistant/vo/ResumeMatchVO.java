package com.jobassistant.vo;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * 简历与 JD 的匹配结果。
 */
@Schema(description = "简历匹配结果")
public record ResumeMatchVO(
        @Schema(description = "匹配度 0-100")
        Integer score,

        @Schema(description = "已匹配的技能")
        List<String> matchedSkills,

        @Schema(description = "简历里缺少、但 JD 要求的技能")
        List<String> missingSkills,

        @Schema(description = "简历的亮点")
        List<String> strengths,

        @Schema(description = "优化建议")
        List<String> suggestions,

        @Schema(description = "总体点评")
        String comment
) {
    public ResumeMatchVO {
        matchedSkills = matchedSkills == null ? List.of() : matchedSkills;
        missingSkills = missingSkills == null ? List.of() : missingSkills;
        strengths = strengths == null ? List.of() : strengths;
        suggestions = suggestions == null ? List.of() : suggestions;
    }
}
