package com.jobassistant.vo;

import com.jobassistant.ai.AiPricing;
import com.jobassistant.ai.AiUsage;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;

/**
 * 一次 AI 调用的 token 消耗，附带按用户配置单价估算的费用。
 */
@Schema(description = "一次 AI 调用的 token 消耗")
public record AiUsageVO(
        @Schema(description = "输入 token 数")
        int inputTokens,

        @Schema(description = "输出 token 数（含思维链）")
        int outputTokens,

        @Schema(description = "输入中命中上下文缓存的 token 数，是 inputTokens 的子集")
        int cachedTokens,

        @Schema(description = "思维链 token 数，已计入 outputTokens")
        int reasoningTokens,

        @Schema(description = "输入 + 输出总 token 数")
        int totalTokens,

        @Schema(description = "按配置单价估算的费用（元），未配置单价时为 null")
        BigDecimal estimatedCost
) {

    public static AiUsageVO from(AiUsage usage, AiPricing pricing) {
        AiUsage value = usage == null ? AiUsage.EMPTY : usage;
        AiPricing price = pricing == null ? AiPricing.NONE : pricing;
        return new AiUsageVO(value.inputTokens(), value.outputTokens(), value.cachedTokens(),
                value.reasoningTokens(), value.totalTokens(), price.estimateCost(value));
    }
}
