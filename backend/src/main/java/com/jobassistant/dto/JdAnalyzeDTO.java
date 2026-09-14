package com.jobassistant.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "JD 分析请求：jobId 与 jobDescription 至少传一个")
public record JdAnalyzeDTO(
        @Schema(description = "已保存的职位 ID，传了它可以直接用库里的 JD")
        Long jobId,

        @Schema(description = "直接粘贴的 JD 文本")
        String jobDescription
) {
}
