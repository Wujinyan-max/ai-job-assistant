package com.jobassistant.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "简历新增/修改请求")
public record ResumeDTO(
        @NotBlank(message = "简历名称不能为空")
        @Size(max = 100, message = "简历名称最长 100 个字符")
        String title,

        String name,
        String phone,

        @jakarta.validation.constraints.Email(message = "邮箱格式不正确")
        String email,

        String education,
        Integer workYears,

        @Size(max = 1000, message = "技能标签最长 1000 个字符")
        String skills,

        @Size(max = 2000, message = "个人简介最长 2000 个字符")
        String summary,

        String content,

        Boolean isDefault,

        @Schema(description = "结构化简历 JSON（排版导出用），结构见 ResumeStructureVO")
        String contentJson,

        @Schema(description = "头像 data URL；传空字符串表示清空头像")
        String avatar,

        @Schema(description = "提取到的版式配色 JSON，结构见 ResumeStyleVO")
        String styleJson
) {
}
