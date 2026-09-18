package com.jobassistant.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "职位新增/修改请求")
public record JobDTO(
        Long companyId,

        @NotBlank(message = "职位名称不能为空")
        @Size(max = 100, message = "职位名称最长 100 个字符")
        String jobName,

        @Schema(description = "职位描述原文")
        String jobDescription,

        Integer salaryMin,
        Integer salaryMax,

        @Schema(description = "薪资描述自由文本，如「15-30K·14薪」，填写后列表优先展示它")
        @Size(max = 50, message = "薪资描述最长 50 个字符")
        String salaryDesc,

        String location,
        String jobUrl,

        @Schema(description = "OPEN / CLOSED")
        String status
) {
}
