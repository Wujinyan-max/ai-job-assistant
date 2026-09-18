package com.jobassistant.ai;

import java.util.List;
import java.util.Map;

/**
 * 一次 AI 调用的入参。
 *
 * @param task         任务类型
 * @param systemPrompt 真实模型的系统提示词
 * @param userPrompt   真实模型的用户提示词
 * @param inputs       结构化输入，本地模拟实现直接用它做规则计算
 * @param images       随提示词一起发给多模态模型的图片（data URL），没有图时为空列表
 */
public record AiRequest(AiTask task,
                        String systemPrompt,
                        String userPrompt,
                        Map<String, Object> inputs,
                        List<String> images) {

    public AiRequest {
        images = images == null ? List.of() : List.copyOf(images);
    }

    /** 纯文本调用：绝大多数任务只有文字，不需要每次都显式传空图片列表 */
    public AiRequest(AiTask task, String systemPrompt, String userPrompt, Map<String, Object> inputs) {
        this(task, systemPrompt, userPrompt, inputs, List.of());
    }

    /** 是否携带图片，决定请求体用字符串还是内容块数组 */
    public boolean hasImages() {
        return !images.isEmpty();
    }
}