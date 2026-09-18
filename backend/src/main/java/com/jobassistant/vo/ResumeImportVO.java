package com.jobassistant.vo;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * 简历文件导入结果：解析出来的字段直接回填到「新增简历」表单，用户确认后再保存。
 */
@Schema(description = "简历文件解析结果")
public record ResumeImportVO(
        @Schema(description = "原始文件名")
        String fileName,

        @Schema(description = "文件类型：PDF / DOCX / TXT")
        String fileType,

        @Schema(description = "解析出的正文字数")
        int textLength,

        @Schema(description = "自动填充的字段，前端用来提示用户")
        List<String> filledFields,

        @Schema(description = "建议的简历名称")
        String title,

        String name,

        String phone,

        String email,

        String education,

        Integer workYears,

        String skills,

        String summary,

        @Schema(description = "完整简历正文，供 AI 匹配使用")
        String content,

        @Schema(description = "从 PDF 里提取到的版式配色，用于原版复刻；非 PDF 或识别失败时为 null")
        ResumeStyleVO style,

        @Schema(description = "从 PDF 里提取到的头像（data URL），没有头像时为 null")
        String avatar,

        @Schema(description = "识别出的结构化内容，前端保存时写进 contentJson；识别失败时为 null")
        ResumeStructureVO structure,

        @Schema(description = "识别方式：VISION 视觉识别 / TEXT 文本识别 / LOCAL 本地规则解析 / NONE 未识别")
        String source,

        @Schema(description = "需要提示用户的话，没有时为 null")
        String notice
) {
}
