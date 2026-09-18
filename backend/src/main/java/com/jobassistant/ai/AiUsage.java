package com.jobassistant.ai;

/**
 * 一次模型调用消耗的 token 明细。
 * <p>各协议与厂商的字段名不同（chat 用 prompt_tokens，responses 用 input_tokens），
 * 解析统一收敛在 {@link AiProtocolCodec#parseUsage}。</p>
 *
 * @param inputTokens     输入 token 数
 * @param outputTokens    输出 token 数
 * @param cachedTokens    输入中命中上下文缓存的 token 数（是 inputTokens 的子集）
 * @param reasoningTokens 思维链 token 数（已计入 outputTokens）
 */
public record AiUsage(int inputTokens,
                      int outputTokens,
                      int cachedTokens,
                      int reasoningTokens) {

    /** 本地模拟引擎，或厂商没返回 usage 时使用 */
    public static final AiUsage EMPTY = new AiUsage(0, 0, 0, 0);

    public int totalTokens() {
        return inputTokens + outputTokens;
    }

    public boolean isEmpty() {
        return totalTokens() == 0;
    }
}
