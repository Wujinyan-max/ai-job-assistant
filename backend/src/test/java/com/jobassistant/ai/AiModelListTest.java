package com.jobassistant.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jobassistant.config.AiProperties;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.ResourceAccessException;

import java.io.IOException;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 模型列表拉取与上游错误透传的纯函数测试。 */
class AiModelListTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void modelsEndpoint_shouldTolerateTrailingSlashes() {
        assertEquals("https://api.deepseek.com/v1/models",
                AiClient.modelsEndpoint("https://api.deepseek.com/v1"));
        assertEquals("https://api.deepseek.com/v1/models",
                AiClient.modelsEndpoint("https://api.deepseek.com/v1/"));
        assertEquals("https://api.deepseek.com/v1/models",
                AiClient.modelsEndpoint("  https://api.deepseek.com/v1//  "));
    }

    @Test
    void modelsEndpoint_shouldNotAppendModelsTwice() {
        // 很多人直接从文档里复制完整地址，粘贴 Base URL 后会拼成 /v1/models/models
        assertEquals("https://api.openai.com/v1/models",
                AiClient.modelsEndpoint("https://api.openai.com/v1/models"));
        assertEquals("https://api.openai.com/v1/models",
                AiClient.modelsEndpoint("https://api.openai.com/v1/models/"));
        // 复制对话地址进 Base URL 也不能拼出 /chat/completions/models
        assertEquals("https://api.deepseek.com/v1/models",
                AiClient.modelsEndpoint("https://api.deepseek.com/v1/chat/completions"));
    }

    @Test
    void extractModelIds_shouldReadOpenAiDataArray() throws Exception {
        var json = objectMapper.readTree(
                "{\"object\":\"list\",\"data\":[{\"id\":\"deepseek-flash\"},{\"id\":\"deepseek-v4-pro\"}]}");
        assertEquals(List.of("deepseek-flash", "deepseek-v4-pro"), AiClient.extractModelIds(json));
    }

    @Test
    void extractModelIds_shouldSupportModelsArrayAndPlainStrings() throws Exception {
        var json = objectMapper.readTree("{\"models\":[{\"name\":\"qwen-plus\"},\"gpt-4o-mini\"]}");
        assertEquals(List.of("qwen-plus", "gpt-4o-mini"), AiClient.extractModelIds(json));
    }

    @Test
    void extractModelIds_shouldDeduplicateAndDropBlanks() throws Exception {
        var json = objectMapper.readTree("{\"data\":[{\"id\":\"a\"},{\"id\":\"a\"},{\"id\":\"  \"}]}");
        assertEquals(List.of("a"), AiClient.extractModelIds(json));
    }

    @Test
    void extractModelIds_shouldReturnEmptyOnUnknownShape() throws Exception {
        assertTrue(AiClient.extractModelIds(objectMapper.readTree("{\"foo\":1}")).isEmpty());
        assertTrue(AiClient.extractModelIds(null).isEmpty());
    }

    @Test
    void describeHttpError_shouldSurfaceUpstreamMessage() {
        String body = "{\"error\":{\"message\":\"The supported API model names are deepseek-flash, "
                + "deepseek-v4-pro, but you passed DeepSeek-V4.1-Flash.\"}}";
        String message = AiClient.describeHttpError(400, body);
        assertTrue(message.contains("HTTP 400"), message);
        assertTrue(message.contains("deepseek-flash"), message);
    }

    @Test
    void describeHttpError_shouldFallBackToRawBody() {
        assertEquals("HTTP 500：oops", AiClient.describeHttpError(500, "oops"));
        assertEquals("HTTP 502", AiClient.describeHttpError(502, "   "));
    }

    @Test
    void rootCause_shouldExposeDeepestMessage() {
        Exception wrapped = new RuntimeException("outer", new SocketTimeoutException("Connect timed out"));
        String cause = AiClient.rootCause(wrapped);
        assertTrue(cause.contains("SocketTimeoutException"), cause);
        assertTrue(cause.contains("Connect timed out"), cause);
    }

    @Test
    void describeNetworkError_shouldExplainConnectTimeoutInsteadOfBlamingBaseUrl() {
        String message = AiClient.describeNetworkError("https://api.deepseek.com/v1/models",
                transport(new SocketTimeoutException("Connect timed out")));
        assertTrue(message.contains("连接 https://api.deepseek.com/v1/models 超时"), message);
        assertTrue(message.contains("Connect timed out"), message);
        assertFalse(message.contains("Base URL"), message);
    }

    @Test
    void describeNetworkError_shouldNotConfuseReadTimeoutWithConnectTimeout() {
        String message = AiClient.describeNetworkError("https://api.deepseek.com/v1/models",
                transport(new SocketTimeoutException("Read timed out")));
        assertTrue(message.contains("读取响应超时"), message);
        assertFalse(message.contains("连接 https://api.deepseek.com/v1/models 超时"), message);
    }

    @Test
    void describeNetworkError_shouldPointAtPortWhenConnectionRefused() {
        String message = AiClient.describeNetworkError("http://localhost:9/v1/models",
                transport(new ConnectException("Connection refused: no further information")));
        assertTrue(message.contains("端口"), message);
    }

    @Test
    void describeNetworkError_shouldPointAtDnsWhenHostUnknown() {
        String message = AiClient.describeNetworkError("https://api.deapseek.com/v1/models",
                transport(new UnknownHostException("api.deapseek.com")));
        assertTrue(message.contains("域名"), message);
    }

    @Test
    void isRetryableConnectFailure_shouldOnlyRetryConnectPhaseProblems() {
        assertTrue(AiClient.isRetryableConnectFailure(transport(new SocketTimeoutException("Connect timed out"))));
        assertTrue(AiClient.isRetryableConnectFailure(transport(new UnknownHostException("api.deepseek.com"))));
        assertFalse(AiClient.isRetryableConnectFailure(transport(new SocketTimeoutException("Read timed out"))));
        assertFalse(AiClient.isRetryableConnectFailure(
                transport(new ConnectException("Connection refused: no further information"))));
    }

    @Test
    void executeWithRetry_shouldRecoverFromTransientConnectTimeout() {
        AiClient client = clientWithMaxAttempts(3);
        AtomicInteger calls = new AtomicInteger();
        JsonNode result = client.executeWithRetry("拉取模型列表", () -> {
            if (calls.incrementAndGet() < 3) {
                throw transport(new SocketTimeoutException("Connect timed out"));
            }
            return objectMapper.createObjectNode().put("ok", true);
        });
        assertEquals(3, calls.get());
        assertTrue(result.path("ok").asBoolean());
    }

    @Test
    void executeWithRetry_shouldGiveUpWhenEveryAttemptFails() {
        AiClient client = clientWithMaxAttempts(3);
        AtomicInteger calls = new AtomicInteger();
        assertThrows(ResourceAccessException.class, () -> client.executeWithRetry("拉取模型列表", () -> {
            calls.incrementAndGet();
            throw transport(new SocketTimeoutException("Connect timed out"));
        }));
        assertEquals(3, calls.get());
    }

    @Test
    void executeWithRetry_shouldNotRetryReadTimeout() {
        AiClient client = clientWithMaxAttempts(3);
        AtomicInteger calls = new AtomicInteger();
        assertThrows(ResourceAccessException.class, () -> client.executeWithRetry("调用模型服务", () -> {
            calls.incrementAndGet();
            throw transport(new SocketTimeoutException("Read timed out"));
        }));
        assertEquals(1, calls.get());
    }

    private AiClient clientWithMaxAttempts(int maxAttempts) {
        AiProperties aiProperties = new AiProperties();
        aiProperties.setMaxAttempts(maxAttempts);
        return new AiClient(aiProperties, new MockAiEngine(objectMapper), objectMapper, new AiProtocolCodec());
    }

    private static ResourceAccessException transport(IOException cause) {
        return new ResourceAccessException("I/O error: " + cause.getMessage(), cause);
    }
}
