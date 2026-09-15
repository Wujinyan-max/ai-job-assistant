package com.jobassistant.ai;

/** Immutable configuration used for one AI request. */
public record AiRuntimeConfig(
        String provider,
        String apiMode,
        String baseUrl,
        String apiKey,
        String model,
        int timeoutSeconds
) {
}
