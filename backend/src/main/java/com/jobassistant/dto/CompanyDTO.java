package com.jobassistant.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "公司新增/修改请求")
public record CompanyDTO(
        @NotBlank(message = "公司名称不能为空")
        @Size(max = 100, message = "公司名称最长 100 个字符")
        String name,

        String industry,
        String scale,
        String city,
        String website,

        @Schema(description = "TARGET / CONTACTED / CLOSED")
        String status,

        @Size(max = 500, message = "备注最长 500 个字符")
        String remark
) {
}
