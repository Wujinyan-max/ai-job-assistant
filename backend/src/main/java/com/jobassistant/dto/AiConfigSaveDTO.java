package com.jobassistant.dto;

import jakarta.validation.constraints.NotBlank;

public record AiConfigSaveDTO(
        @NotBlank String provider,
        @NotBlank String apiMode,
        @NotBlank String baseUrl,
        @NotBlank String model,
        String apiKey,
        Boolean enabled
) {
}
