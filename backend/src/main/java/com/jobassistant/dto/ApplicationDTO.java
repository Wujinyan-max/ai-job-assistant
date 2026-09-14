package com.jobassistant.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;

@Schema(description = "投递记录新增/修改请求")
public record ApplicationDTO(
        @NotNull(message = "请选择要投递的职位")
        Long jobId,

        @Schema(description = "使用的简历，可为空")
        Long resumeId,

        @Schema(description = "投递状态，为空时默认 WISHLIST")
        String applicationStatus,

        @Schema(description = "投递时间，格式 yyyy-MM-dd HH:mm:ss", example = "2026-09-14 10:00:00")
        LocalDateTime applyTime,

        @Size(max = 50, message = "投递渠道最长 50 个字符")
        String source,

        @Size(max = 500, message = "备注最长 500 个字符")
        String remark
) {
}
