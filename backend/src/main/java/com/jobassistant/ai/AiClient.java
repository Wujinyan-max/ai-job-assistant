package com.jobassistant.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jobassistant.common.BusinessException;
import com.jobassistant.common.ErrorCode;
import com.jobassistant.config.AiProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 大模型调用客户端，兼容 OpenAI 协议。
 * <p>换服务商只需要改 ai.base-url / ai.model，例如 DeepSeek、通义千问兼容模式、本地 Ollama。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AiClient {

    /** 未配置 API Key 时使用的虚拟模型名 */
    public static final String MOCK_MODEL = "mock-local";

    private final AiProperties aiProperties;
    private final MockAiEngine mockAiEngine;
    private final ObjectMapper objectMapper;

    private volatile RestClient restClient;

    public AiReply chat(AiRequest request) {
        if (!aiProperties.isEnabled()) {
            throw new BusinessException(ErrorCode.AI_DISABLED);
        }
        if (!aiProperties.hasApiKey()) {
            log.info("未配置 ai.api-key，使用本地模拟引擎处理任务 {}", request.task());
            return new AiReply(mockAiEngine.reply(request), MOCK_MODEL, true);
        }

        long start = System.currentTimeMillis();
        try {
            JsonNode response = restClient().post()
                    .uri(chatCompletionsUrl())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(buildBody(request))
                    .retrieve()
                    .body(JsonNode.class);

            String content = response == null ? null
                    : response.path("choices").path(0).path("message").path("content").asText(null);
            if (content == null || content.isBlank()) {
                throw new BusinessException(ErrorCode.AI_CALL_FAILED, "AI 返回内容为空");
            }
            log.info("AI 调用完成，task={}, model={}, 耗时 {}ms", request.task(), aiProperties.getModel(),
                    System.currentTimeMillis() - start);
            return new AiReply(content, aiProperties.getModel(), false);
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("调用 AI 服务失败，task={}", request.task(), e);
            throw new BusinessException(ErrorCode.AI_CALL_FAILED,
                    "AI 服务调用失败：" + e.getClass().getSimpleName());
        }
    }

    /**
     * 把模型返回的文本解析成对象，兼容模型偶尔带上 markdown 代码块的情况。
     */
    public <T> T parse(String content, Class<T> type) {
        String json = extractJson(content);
        try {
            return objectMapper.readValue(json, type);
        } catch (Exception e) {
            log.warn("AI 返回内容解析失败: {}", abbreviate(content), e);
            throw new BusinessException(ErrorCode.AI_PARSE_FAILED, "AI 返回内容无法解析，请重试");
        }
    }

    /** 从可能包含 ```json 包裹或前后缀说明的文本中抠出 JSON 主体。 */
    static String extractJson(String content) {
        if (content == null) {
            throw new BusinessException(ErrorCode.AI_PARSE_FAILED, "AI 返回内容为空");
        }
        String text = content.trim();
        if (text.startsWith("```")) {
            int firstLineEnd = text.indexOf('\n');
            int fenceEnd = text.lastIndexOf("```");
            if (firstLineEnd > 0 && fenceEnd > firstLineEnd) {
                text = text.substring(firstLineEnd + 1, fenceEnd).trim();
            }
        }
        int start = text.indexOf('{');
        int end = text.lastIndexOf('}');
        if (start < 0 || end <= start) {
            throw new BusinessException(ErrorCode.AI_PARSE_FAILED, "AI 返回内容不是合法 JSON");
        }
        return text.substring(start, end + 1);
    }

    private Map<String, Object> buildBody(AiRequest request) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", aiProperties.getModel());
        body.put("temperature", 0.2);
        body.put("messages", List.of(
                Map.of("role", "system", "content", request.systemPrompt()),
                Map.of("role", "user", "content", request.userPrompt())));
        // 让模型以 JSON 形式输出，省掉后端正则抠字段的麻烦
        body.put("response_format", Map.of("type", "json_object"));
        return body;
    }

    private String chatCompletionsUrl() {
        String baseUrl = aiProperties.getBaseUrl();
        while (baseUrl.endsWith("/")) {
            baseUrl = baseUrl.substring(0, baseUrl.length() - 1);
        }
        return baseUrl + "/chat/completions";
    }

    private RestClient restClient() {
        if (restClient == null) {
            synchronized (this) {
                if (restClient == null) {
                    SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
                    factory.setConnectTimeout(Duration.ofSeconds(10));
                    factory.setReadTimeout(Duration.ofSeconds(aiProperties.getTimeoutSeconds()));
                    restClient = RestClient.builder()
                            .requestFactory(factory)
                            .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + aiProperties.getApiKey())
                            .build();
                }
            }
        }
        return restClient;
    }

    private static String abbreviate(String text) {
        if (text == null) {
            return "null";
        }
        return text.length() <= 300 ? text : text.substring(0, 300) + "...";
    }
}
