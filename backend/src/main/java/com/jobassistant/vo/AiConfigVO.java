package com.jobassistant.vo;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * AI 能力当前配置，前端用它提示用户当前是真实模型还是本地模拟。
 */
@Schema(description = "AI 配置信息")
public record AiConfigVO(
        @Schema(description = "AI 能力是否开启")
        boolean enabled,

        String provider,

        String apiMode,

        String baseUrl,

        @Schema(description = "当前使用的模型名")
        String model,

        @Schema(description = "是否运行在本地模拟模式（未配置 ai.api-key 时为 true）")
        boolean mock,

        boolean hasApiKey,

        String apiKeyMasked,

        @Schema(description = "思考模式：DEFAULT 跟随厂商 / OFF 关闭 / ON 开启")
        String thinkingMode,

        @Schema(description = "输入（缓存未命中）单价，元/百万 tokens，未配置为 null")
        Double inputPrice,

        @Schema(description = "输入（缓存命中）单价，元/百万 tokens，未配置为 null")
        Double cachePrice,

        @Schema(description = "输出单价，元/百万 tokens，未配置为 null")
        Double outputPrice
) {
}
