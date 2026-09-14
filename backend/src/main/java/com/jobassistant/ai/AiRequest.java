package com.jobassistant.ai;

import java.util.Map;

/**
 * 一次 AI 调用的入参。
 *
 * @param task        任务类型
 * @param systemPrompt 真实模型的系统提示词
 * @param userPrompt   真实模型的用户提示词
 * @param inputs       结构化输入，本地模拟实现直接用它做规则计算
 */
public record AiRequest(AiTask task,
                        String systemPrompt,
                        String userPrompt,
                        Map<String, Object> inputs) {
}
