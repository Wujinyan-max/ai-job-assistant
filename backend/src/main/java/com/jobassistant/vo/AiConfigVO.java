package com.jobassistant.vo;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * AI 能力当前配置，前端用它提示用户当前是真实模型还是本地模拟。
 */
@Schema(description = "AI 配置信息")
public record AiConfigVO(
        @Schema(description = "AI 能力是否开启")
        boolean enabled,

        @Schema(description = "当前使用的模型名")
        String model,

        @Schema(description = "是否运行在本地模拟模式（未配置 ai.api-key 时为 true）")
        boolean mock
) {
}
