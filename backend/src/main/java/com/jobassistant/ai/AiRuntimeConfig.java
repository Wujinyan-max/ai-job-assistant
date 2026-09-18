package com.jobassistant.ai;

/**
 * Immutable configuration used for one AI request.
 *
 * @param pricing      单价配置，用于估算费用
 * @param thinkingMode 思考模式，见 {@link AiThinkingMode}
 */
public record AiRuntimeConfig(
        String provider,
        String apiMode,
        String baseUrl,
        String apiKey,
        String model,
        int timeoutSeconds,
        AiPricing pricing,
        String thinkingMode
) {

    /** 只关心协议本身的调用方（例如拉模型列表）用这个构造：不估算费用、思考模式跟随厂商 */
    public AiRuntimeConfig(String provider, String apiMode, String baseUrl, String apiKey,
                           String model, int timeoutSeconds) {
        this(provider, apiMode, baseUrl, apiKey, model, timeoutSeconds, AiPricing.NONE, AiThinkingMode.DEFAULT);
    }

    @Override
    public AiPricing pricing() {
        return pricing == null ? AiPricing.NONE : pricing;
    }

    @Override
    public String thinkingMode() {
        return AiThinkingMode.normalize(thinkingMode);
    }
}
