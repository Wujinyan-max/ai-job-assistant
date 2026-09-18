package com.jobassistant.ai;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * 单价配置，单位统一为「元 / 百万 tokens」，由用户在模型配置里填写，用来估算调用费用。
 * <p>任意一项为 null 表示未配置，此时不估算费用，只展示 token 数。</p>
 *
 * @param inputPerMillion       输入（缓存未命中）单价
 * @param cachedInputPerMillion 输入（缓存命中）单价
 * @param outputPerMillion      输出单价
 */
public record AiPricing(Double inputPerMillion,
                        Double cachedInputPerMillion,
                        Double outputPerMillion) {

    /** 未配置单价 */
    public static final AiPricing NONE = new AiPricing(null, null, null);

    private static final BigDecimal MILLION = BigDecimal.valueOf(1_000_000);
    private static final int COST_SCALE = 6;

    /** 三项单价是否都已填写 */
    public boolean configured() {
        return inputPerMillion != null && cachedInputPerMillion != null && outputPerMillion != null;
    }

    /**
     * 估算本次调用费用（元）。单价未配置、或本次没有 token 消耗（例如本地模拟）时返回 null。
     * <p>缓存命中的输入按 {@code cachedInputPerMillion} 计价，其余输入按 {@code inputPerMillion} 计价。</p>
     */
    public BigDecimal estimateCost(AiUsage usage) {
        if (usage == null || usage.isEmpty() || !configured()) {
            return null;
        }
        int cached = Math.min(usage.cachedTokens(), usage.inputTokens());
        BigDecimal cost = BigDecimal.valueOf(usage.inputTokens() - cached).multiply(value(inputPerMillion))
                .add(BigDecimal.valueOf(cached).multiply(value(cachedInputPerMillion)))
                .add(BigDecimal.valueOf(usage.outputTokens()).multiply(value(outputPerMillion)));
        return cost.divide(MILLION, COST_SCALE, RoundingMode.HALF_UP);
    }

    private static BigDecimal value(Double price) {
        return BigDecimal.valueOf(price);
    }
}
