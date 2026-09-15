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
    private final AiProtocolCodec protocolCodec;

    public AiReply chat(AiRequest request) {
        if (!aiProperties.isEnabled()) {
            throw new BusinessException(ErrorCode.AI_DISABLED);
        }
        return chat(request, new AiRuntimeConfig("OPENAI", AiProtocolCodec.CHAT_COMPLETIONS,
                aiProperties.getBaseUrl(), aiProperties.getApiKey(), aiProperties.getModel(),
                aiProperties.getTimeoutSeconds()));
    }

    public AiReply chat(AiRequest request, AiRuntimeConfig config) {
        if (config == null || config.apiKey() == null || config.apiKey().isBlank()) {
            log.info("未配置 ai.api-key，使用本地模拟引擎处理任务 {}", request.task());
            return new AiReply(mockAiEngine.reply(request), MOCK_MODEL, true);
        }

        protocolCodec.validateMode(config.apiMode());

        long start = System.currentTimeMillis();
        try {
            JsonNode response = restClient(config).post()
                    .uri(protocolCodec.endpoint(config))
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(protocolCodec.buildBody(config, request))
                    .retrieve()
                    .body(JsonNode.class);

            String content = protocolCodec.extractText(config.apiMode(), response);
            if (content == null || content.isBlank()) {
                throw new BusinessException(ErrorCode.AI_CALL_FAILED, "AI 返回内容为空");
            }
            log.info("AI 调用完成，task={}, provider={}, mode={}, model={}, 耗时 {}ms",
                    request.task(), config.provider(), config.apiMode(), config.model(),
                    System.currentTimeMillis() - start);
            return new AiReply(content, config.model(), false);
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

    private RestClient restClient(AiRuntimeConfig config) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(10));
        factory.setReadTimeout(Duration.ofSeconds(config.timeoutSeconds()));
        return RestClient.builder()
                .requestFactory(factory)
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + config.apiKey())
                .build();
    }

    private static String abbreviate(String text) {
        if (text == null) {
            return "null";
        }
        return text.length() <= 300 ? text : text.substring(0, 300) + "...";
    }
}
