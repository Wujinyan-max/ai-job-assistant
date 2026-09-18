package com.jobassistant.dto;

/**
 * 拉取模型列表时的可选覆盖项：留空的字段回落到当前账号已保存的配置，
 * 这样用户可以「先填 API Key → 拉列表 → 选模型 → 再保存」。
 */
public record AiModelQueryDTO(
        String provider,
        String apiMode,
        String baseUrl,
        String apiKey
) {
}
