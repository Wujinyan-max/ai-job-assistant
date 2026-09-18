package com.jobassistant.vo;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jobassistant.ai.AiPricing;
import com.jobassistant.ai.AiUsage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 前端 AiUsageBar 直接读 data / usage / mocked 这几个字段，这里固化返回体的字段名与类型。
 */
class AiCallVOTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    @DisplayName("返回体字段与前端读取的一致")
    void serializesFieldsTheFrontendReads() {
        AiUsageVO usage = AiUsageVO.from(new AiUsage(2000, 1000, 512, 300), new AiPricing(1.0, 0.02, 4.0));
        AiCallVO<Map<String, Object>> vo = new AiCallVO<>(Map.of("score", 82), usage, false);

        JsonNode json = mapper.valueToTree(vo);

        assertThat(json.path("data").path("score").asInt()).isEqualTo(82);
        assertThat(json.path("mocked").asBoolean()).isFalse();
        assertThat(json.path("usage").path("inputTokens").asInt()).isEqualTo(2000);
        assertThat(json.path("usage").path("outputTokens").asInt()).isEqualTo(1000);
        assertThat(json.path("usage").path("cachedTokens").asInt()).isEqualTo(512);
        assertThat(json.path("usage").path("reasoningTokens").asInt()).isEqualTo(300);
        assertThat(json.path("usage").path("totalTokens").asInt()).isEqualTo(3000);
        // (2000 - 512) * 1 + 512 * 0.02 + 1000 * 4 = 5498.24，除以一百万
        assertThat(json.path("usage").path("estimatedCost").decimalValue()).isEqualByComparingTo("0.005498");
    }

    @Test
    @DisplayName("未配置单价或本地模拟时 estimatedCost 为 null，前端只显示 token 数")
    void omitsCostWhenPricingMissing() {
        assertThat(mapper.valueToTree(AiUsageVO.from(new AiUsage(10, 5, 0, 0), AiPricing.NONE))
                .path("estimatedCost").isNull()).isTrue();
        assertThat(mapper.valueToTree(AiUsageVO.from(AiUsage.EMPTY, new AiPricing(1.0, 0.02, 4.0)))
                .path("estimatedCost").isNull()).isTrue();
    }
}
