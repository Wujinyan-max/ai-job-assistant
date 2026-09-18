package com.jobassistant.vo;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 一条「原文 → 优化后」的改写记录，前端并排展示，让用户看清 AI 到底改了什么。
 */
@Schema(description = "简历逐条改写记录")
public record ResumeRewriteVO(
        @Schema(description = "所属板块：个人简介 / 技能 / 工作经历 / 项目经历")
        String section,

        @Schema(description = "用户提供的原文，逐字保留")
        String original,

        @Schema(description = "针对目标岗位改写后的表达")
        String optimized,

        @Schema(description = "这条为什么这么改")
        String reason
) {
}
