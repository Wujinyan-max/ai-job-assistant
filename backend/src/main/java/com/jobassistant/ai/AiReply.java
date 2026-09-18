package com.jobassistant.ai;

/**
 * 一次 AI 调用的结果。
 *
 * @param content 模型返回的文本（约定为 JSON）
 * @param model   实际使用的模型名
 * @param mocked  是否为本地模拟结果（未配置 API Key 时为 true）
 * @param usage   本次调用的 token 消耗，本地模拟或厂商未返回 usage 时为 {@link AiUsage#EMPTY}
 */
public record AiReply(String content, String model, boolean mocked, AiUsage usage) {
}
