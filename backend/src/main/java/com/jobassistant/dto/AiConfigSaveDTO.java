package com.jobassistant.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * 保存用户级 AI 配置。单价用于估算费用，单位统一为「元 / 百万 tokens」，留空则不估算。
 */
public record AiConfigSaveDTO(
        @NotBlank String provider,
        @NotBlank String apiMode,
        @NotBlank String baseUrl,
        @NotBlank String model,
        String apiKey,
        Boolean enabled,
        String thinkingMode,
        Double inputPrice,
        Double cachePrice,
        Double outputPrice
) {
}
