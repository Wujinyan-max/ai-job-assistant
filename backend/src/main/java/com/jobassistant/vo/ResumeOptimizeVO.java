package com.jobassistant.vo;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * 简历专项优化结果，字段与 AI 提示词里约定的 JSON 结构一致。
 */
@Schema(description = "简历专项优化结果")
public record ResumeOptimizeVO(
        @Schema(description = "逐条改写记录")
        List<ResumeRewriteVO> rewrites,

        @Schema(description = "素材里已体现、且岗位要求的关键词")
        List<String> matchedKeywords,

        @Schema(description = "岗位要求、但素材里没有体现的关键词")
        List<String> missingKeywords,

        @Schema(description = "还需要用户补充的信息")
        List<String> suggestions,

        @Schema(description = "AI 补全的项目经历列表，仅在用户允许时出现")
        List<ResumeNewProjectVO> newProjects,

        @Schema(description = "改写后的完整简历正文，可直接替换原简历正文")
        String optimizedContent,

        @Schema(description = "总体点评")
        String comment,

        @Schema(description = "保存为新简历版本后的简历 ID，未保存时为 null")
        Long savedResumeId
) {
    public ResumeOptimizeVO {
        rewrites = rewrites == null ? List.of() : rewrites;
        matchedKeywords = matchedKeywords == null ? List.of() : matchedKeywords;
        missingKeywords = missingKeywords == null ? List.of() : missingKeywords;
        suggestions = suggestions == null ? List.of() : suggestions;
        newProjects = newProjects == null ? List.of() : newProjects;
    }

    /**
     * 保存成新简历版本后回填 ID。
     * <p>savedResumeId 不在提示词的 JSON 结构里，模型不会返回它，由服务端落库后补上。</p>
     */
    public ResumeOptimizeVO withSavedResumeId(Long resumeId) {
        return new ResumeOptimizeVO(rewrites, matchedKeywords, missingKeywords, suggestions,
                newProjects, optimizedContent, comment, resumeId);
    }

    /**
     * 服务端强制过滤 AI 补全的项目：用户没允许时，模型返回的 newProjects 不能带出。
     */
    public ResumeOptimizeVO withNewProjects(List<ResumeNewProjectVO> projects) {
        return new ResumeOptimizeVO(rewrites, matchedKeywords, missingKeywords, suggestions,
                projects, optimizedContent, comment, savedResumeId);
    }
}
