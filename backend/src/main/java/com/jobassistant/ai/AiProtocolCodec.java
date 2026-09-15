package com.jobassistant.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.jobassistant.common.BusinessException;
import com.jobassistant.common.ErrorCode;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class AiProtocolCodec {

    public static final String CHAT_COMPLETIONS = "CHAT_COMPLETIONS";
    public static final String RESPONSES = "RESPONSES";

    public String endpoint(AiRuntimeConfig config) {
        String base = config.baseUrl().trim();
        while (base.endsWith("/")) base = base.substring(0, base.length() - 1);
        return base + (RESPONSES.equals(config.apiMode()) ? "/responses" : "/chat/completions");
    }

    public Map<String, Object> buildBody(AiRuntimeConfig config, AiRequest request) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", config.model());
        if (RESPONSES.equals(config.apiMode())) {
            body.put("instructions", request.systemPrompt());
            body.put("input", request.userPrompt());
            body.put("text", Map.of("format", Map.of("type", "json_object")));
        } else {
            body.put("temperature", 0.2);
            body.put("messages", List.of(
                    Map.of("role", "system", "content", request.systemPrompt()),
                    Map.of("role", "user", "content", request.userPrompt())));
            body.put("response_format", Map.of("type", "json_object"));
        }
        return body;
    }

    public String extractText(String apiMode, JsonNode response) {
        if (response == null) return null;
        if (!RESPONSES.equals(apiMode)) {
            return response.path("choices").path(0).path("message").path("content").asText(null);
        }
        String direct = response.path("output_text").asText(null);
        if (direct != null && !direct.isBlank()) return direct;
        for (JsonNode output : response.path("output")) {
            for (JsonNode content : output.path("content")) {
                String text = content.path("text").asText(null);
                if (text != null && !text.isBlank()) return text;
            }
        }
        return null;
    }

    public void validateMode(String mode) {
        if (!CHAT_COMPLETIONS.equals(mode) && !RESPONSES.equals(mode)) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "不支持的 API 模式");
        }
    }
}
