package com.jobassistant.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "简历匹配请求：库内组合或纯文本组合")
public record ResumeMatchDTO(
        Long jobId,
        Long resumeId,

        @Schema(description = "直接粘贴的 JD 文本，传了 jobId 时可省略")
        String jobDescription,

        @Schema(description = "直接粘贴的简历文本，传了 resumeId 时可省略")
        String resumeContent
) {
}
