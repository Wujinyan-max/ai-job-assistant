package com.jobassistant.vo;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * AI 为缺口生成的项目经历。
 * <p>仅在用户明确允许时出现，fabricated 固定为 true，前端要显著标记。</p>
 */
@Schema(description = "AI 补全的项目经历")
public record ResumeNewProjectVO(
        @Schema(description = "项目名")
        String title,

        @Schema(description = "你在项目中的角色")
        String role,

        @Schema(description = "项目一句话背景")
        String description,

        @Schema(description = "用到的技术栈")
        List<String> techStack,

        @Schema(description = "具体做了什么、拿到什么结果")
        List<String> bullets,

        @Schema(description = "是否为 AI 补全，固定为 true")
        boolean fabricated
) {
    public ResumeNewProjectVO {
        techStack = techStack == null ? List.of() : techStack;
        bullets = bullets == null ? List.of() : bullets;
    }
}
