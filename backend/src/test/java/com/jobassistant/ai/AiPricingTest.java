package com.jobassistant.ai;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 费用估算：缓存命中的输入按命中价、其余输入按未命中价、输出按输出价，单位都是「元 / 百万 tokens」。
 */
class AiPricingTest {

    @Test
    @DisplayName("缓存命中与未命中分别计价")
    void splitsCachedAndUncachedInput() {
        AiPricing pricing = new AiPricing(1.0, 0.02, 4.0);
        AiUsage usage = new AiUsage(2000, 1000, 1500, 300);

        // (500 * 1 + 1500 * 0.02 + 1000 * 4) / 1_000_000 = 0.00453
        assertThat(pricing.estimateCost(usage)).isEqualByComparingTo("0.004530");
    }

    @Test
    @DisplayName("单价没填全就不估算费用")
    void returnsNullWhenPricesMissing() {
        AiUsage usage = new AiUsage(2000, 1000, 0, 0);

        assertThat(AiPricing.NONE.estimateCost(usage)).isNull();
        assertThat(new AiPricing(1.0, null, 4.0).estimateCost(usage)).isNull();
        assertThat(new AiPricing(1.0, 0.02, null).estimateCost(usage)).isNull();
    }

    @Test
    @DisplayName("没有 token 消耗（本地模拟）时不显示费用")
    void returnsNullForEmptyUsage() {
        assertThat(new AiPricing(1.0, 0.02, 4.0).estimateCost(AiUsage.EMPTY)).isNull();
        assertThat(new AiPricing(1.0, 0.02, 4.0).estimateCost(null)).isNull();
    }

    @Test
    @DisplayName("缓存命中数异常大于输入数时，按输入数封顶，不会算出负数")
    void clampsCachedTokensToInput() {
        AiPricing pricing = new AiPricing(1.0, 0.02, 4.0);

        // 输入 100 全部按缓存命中价：100 * 0.02 / 1_000_000
        assertThat(pricing.estimateCost(new AiUsage(100, 0, 9999, 0)))
                .isEqualByComparingTo("0.000002");
    }
}
