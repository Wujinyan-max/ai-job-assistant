package com.jobassistant.vo;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 视觉识别结果：内容和版式一次拿到。
 *
 * <p>模型按 {@code {"structure":{...},"style":{...}}} 返回，这里把两段拆开，
 * structure 复用 {@link ResumeStructureVO}，style 复用 {@link ResumeStyleVO}，
 * 避免为同一份数据再定义一套模型。</p>
 */
@Schema(description = "简历视觉识别结果：内容结构 + 版式")
@JsonIgnoreProperties(ignoreUnknown = true)
public record ResumeVisionVO(
        @Schema(description = "识别出的结构化内容")
        ResumeStructureVO structure,

        @Schema(description = "识别出的版式配色")
        ResumeStyleVO style
) {

    public ResumeVisionVO {
        structure = structure == null ? ResumeStructureVO.empty() : structure;
        style = style == null ? ResumeStyleVO.empty() : style;
    }
}