package com.jobassistant.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "状态更新请求")
public record StatusUpdateDTO(
        @NotBlank(message = "状态不能为空")
        String status
) {
}
