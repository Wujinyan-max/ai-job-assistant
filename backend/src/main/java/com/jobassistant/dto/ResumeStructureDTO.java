package com.jobassistant.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "简历结构化识别请求：把纯文本简历识别成可排版的固定 JSON 结构")
public record ResumeStructureDTO(
        @Schema(description = "简历 ID，传了它就直接识别这份简历的正文")
        Long resumeId,

        @Schema(description = "直接粘贴的简历文本，传了 resumeId 时可省略")
        String content
) {
}
