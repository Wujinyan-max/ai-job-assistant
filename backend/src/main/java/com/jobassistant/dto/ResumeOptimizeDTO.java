package com.jobassistant.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.Map;

@Schema(description = "简历专项优化请求：按目标岗位改写用户提供的项目经历等素材")
public record ResumeOptimizeDTO(
        @Schema(description = "目标岗位 ID，传了它可以直接用库里的 JD")
        Long jobId,

        @Schema(description = "参考简历 ID，不想手动粘贴素材时可以拿它的正文当素材")
        Long resumeId,

        @Schema(description = "直接粘贴的 JD 文本，传了 jobId 时可省略")
        String jobDescription,

        @Schema(description = "用户粘贴的项目经历 / 工作内容 / 技能等原始素材，传了 resumeId 时可省略")
        String rawContent,

        @Schema(description = "用户对缺失技能补写的真实项目经历，键为缺失技能，值为一段素材文本")
        Map<String, String> supplementalProjects,

        @Schema(description = "是否允许 AI 为仍未补充的缺口生成贴合 JD 的虚构项目经历")
        Boolean allowFabrication,

        @Schema(description = "额外优化重点，如「突出高并发与架构能力」")
        String focus,

        @Schema(description = "是否把优化结果另存为一份新简历（不影响原简历）")
        Boolean save,

        @Schema(description = "另存时使用的简历名称，留空自动生成")
        String title
) {
}
