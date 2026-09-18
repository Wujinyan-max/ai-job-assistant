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
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.ResourceAccessException;

import java.net.ConnectException;
import java.net.InetSocketAddress;
import java.net.NoRouteToHostException;
import java.net.Proxy;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.function.Supplier;
import javax.net.ssl.SSLException;

/**
 * 大模型调用客户端，兼容 OpenAI 协议。
 * <p>换服务商只需要改 ai.base-url / ai.model，例如 DeepSeek、通义千问兼容模式、本地 Ollama。</p>
 * <p>连接阶段失败会自动重试：底层 HttpURLConnection 只连 DNS 返回的第一个地址，
 * 遇到某个 CDN 节点抽风时，重试能让请求落到健康的那条链路上。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AiClient {

    static {
        // 让 HttpURLConnection 自动读取 Windows 系统代理设置：
        // 用户开了 Clash / V2Ray 系统代理时，后端自动走代理，无需手动配置。
        System.setProperty("java.net.useSystemProxies", "true");
    }

    /** 未配置 API Key 时使用的虚拟模型名 */
    public static final String MOCK_MODEL = "mock-local";

    /** 连接阶段失败后的重试间隔（毫秒）：网络抖动通常几秒内自愈，间隔不需要太长。 */
    private static final int[] RETRY_BACKOFF_MILLIS = {400, 1200};

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
            return new AiReply(mockAiEngine.reply(request), MOCK_MODEL, true, AiUsage.EMPTY);
        }

        protocolCodec.validateMode(config.apiMode());

        // 报错时要说清到底请求的是哪个地址，所以这里用剥掉端点路径后的真实 URL
        String url = protocolCodec.endpoint(config);
        long start = System.currentTimeMillis();
        try {
            JsonNode response = executeWithRetry("调用模型服务", () -> restClient(config).post()
                    .uri(url)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(protocolCodec.buildBody(config, request))
                    .retrieve()
                    .body(JsonNode.class));

            String content = protocolCodec.extractText(config.apiMode(), response);
            if (content == null || content.isBlank()) {
                throw new BusinessException(ErrorCode.AI_CALL_FAILED, "AI 返回内容为空");
            }
            AiUsage usage = protocolCodec.parseUsage(config.apiMode(), response);
            log.info("AI 调用完成，task={}, provider={}, mode={}, model={}, 耗时 {}ms, "
                            + "输入 {} tokens（缓存命中 {}）, 输出 {} tokens（思维链 {}）",
                    request.task(), config.provider(), config.apiMode(), config.model(),
                    System.currentTimeMillis() - start,
                    usage.inputTokens(), usage.cachedTokens(), usage.outputTokens(), usage.reasoningTokens());
            return new AiReply(content, config.model(), false, usage);
        } catch (BusinessException e) {
            throw e;
        } catch (HttpClientErrorException e) {
            log.error("调用 AI 服务失败，task={}, status={}", request.task(), e.getStatusCode().value(), e);
            throw new BusinessException(ErrorCode.AI_CALL_FAILED,
                    "模型服务返回 " + describeHttpError(e.getStatusCode().value(),
                            e.getResponseBodyAsString(StandardCharsets.UTF_8)));
        } catch (ResourceAccessException e) {
            log.error("调用 AI 服务失败，task={}", request.task(), e);
            throw new BusinessException(ErrorCode.AI_CALL_FAILED,
                    describeNetworkError(url, e));
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
            // 必须留下原文：否则线上只能看到“不是合法 JSON”，无法判断模型到底回了什么
            log.warn("AI 返回内容里找不到 JSON 主体，原文（最多 300 字）：{}", abbreviate(text));
            throw new BusinessException(ErrorCode.AI_PARSE_FAILED, "AI 返回内容不是合法 JSON");
        }
        return text.substring(start, end + 1);
    }

    private RestClient restClient(AiRuntimeConfig config) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(Math.max(1, aiProperties.getConnectTimeoutSeconds())));
        factory.setReadTimeout(Duration.ofSeconds(config.timeoutSeconds()));
        // 代理优先级：手动配置 > 系统自动检测
        if (aiProperties.hasProxy()) {
            // 手动配置的代理（如 AI_PROXY_HOST / AI_PROXY_PORT）优先，用于系统代理没生效或需要强制指定场景
            factory.setProxy(new Proxy(Proxy.Type.HTTP,
                    new InetSocketAddress(aiProperties.getProxyHost(), aiProperties.getProxyPort())));
        } else {
            // 自动探测系统代理：用户开了 Clash / V2Ray 系统代理时，后端自动走代理
            Proxy systemProxy = detectSystemProxy(config.baseUrl());
            if (systemProxy != null) {
                factory.setProxy(systemProxy);
            }
        }
        return RestClient.builder()
                .requestFactory(factory)
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + config.apiKey())
                .build();
    }

    /**
     * 根据目标地址探测系统代理：命中则返回代理，没配代理或不需要代理时返回 null。
     * <p>java.net.useSystemProxies=true 后，ProxySelector 会读取 Windows 注册表里的代理设置。</p>
     */
    private Proxy detectSystemProxy(String baseUrl) {
        try {
            java.net.ProxySelector selector = java.net.ProxySelector.getDefault();
            if (selector == null) {
                return null;
            }
            java.net.URI uri = new java.net.URI(baseUrl == null || baseUrl.isBlank()
                    ? "https://api.openai.com" : baseUrl);
            for (Proxy proxy : selector.select(uri)) {
                if (proxy.type() == Proxy.Type.HTTP) {
                    return proxy;
                }
            }
        } catch (Exception ignored) {
            // 探测失败时静默降级为直连，不影响主流程
        }
        return null;
    }

    /**
     * 执行一次 HTTP 调用，连接阶段失败时按 ai.max-attempts 重试。
     */
    JsonNode executeWithRetry(String action, Supplier<JsonNode> call) {
        int maxAttempts = Math.max(1, aiProperties.getMaxAttempts());
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                return call.get();
            } catch (ResourceAccessException e) {
                if (!isRetryableConnectFailure(e) || attempt == maxAttempts) {
                    throw e;
                }
                long backoff = RETRY_BACKOFF_MILLIS[Math.min(attempt - 1, RETRY_BACKOFF_MILLIS.length - 1)];
                log.warn("{} 连接失败（第 {}/{} 次尝试：{}），{}ms 后重试",
                        action, attempt, maxAttempts, rootCause(e), backoff);
                sleep(backoff);
            }
        }
        throw new IllegalStateException("unreachable");
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BusinessException(ErrorCode.AI_CALL_FAILED, "等待重试时被中断");
        }
    }

    /**
     * 拉取该服务可用的模型列表：GET {baseUrl}/models（OpenAI 兼容），供前端筛选选择。
     */
    public List<String> listModels(AiRuntimeConfig config) {
        if (config == null || config.apiKey() == null || config.apiKey().isBlank()) {
            throw new BusinessException(ErrorCode.AI_CALL_FAILED, "请先填写 API Key 再拉取模型列表");
        }
        String url = modelsEndpoint(config.baseUrl());
        try {
            JsonNode response = executeWithRetry("拉取模型列表",
                    () -> restClient(config).get().uri(url).retrieve().body(JsonNode.class));
            List<String> models = extractModelIds(response);
            if (models.isEmpty()) {
                throw new BusinessException(ErrorCode.AI_CALL_FAILED,
                        "该服务没有返回任何模型，请手动填写模型名称");
            }
            log.info("拉取模型列表成功，url={}, 共 {} 个模型", url, models.size());
            return models;
        } catch (BusinessException e) {
            throw e;
        } catch (HttpClientErrorException e) {
            if (e.getStatusCode().value() == 404) {
                throw new BusinessException(ErrorCode.AI_CALL_FAILED,
                        "该服务的 " + url + " 不存在，请检查 Base URL 或手动填写模型名称");
            }
            throw new BusinessException(ErrorCode.AI_CALL_FAILED,
                    "拉取模型列表失败，" + describeHttpError(e.getStatusCode().value(),
                            e.getResponseBodyAsString(StandardCharsets.UTF_8)));
        } catch (ResourceAccessException e) {
            throw new BusinessException(ErrorCode.AI_CALL_FAILED,
                    describeNetworkError(url, e));
        } catch (Exception e) {
            log.error("拉取模型列表失败, url={}", url, e);
            throw new BusinessException(ErrorCode.AI_CALL_FAILED,
                    "拉取模型列表失败：" + e.getClass().getSimpleName());
        }
    }

    /** 拼 /models 地址，容忍 Base URL 末尾多写斜杠或已经带了 /models。 */
    static String modelsEndpoint(String baseUrl) {
        return AiProtocolCodec.normalizeBaseUrl(baseUrl) + "/models";
    }

    /** OpenAI 兼容返回 {"data":[{"id":"..."}]}，同时兼容 {"models":[...]} 与纯数组。 */
    static List<String> extractModelIds(JsonNode response) {
        if (response == null) {
            return List.of();
        }
        JsonNode array = response.isArray() ? response : response.path("data");
        if (!array.isArray()) {
            array = response.path("models");
        }
        if (!array.isArray()) {
            return List.of();
        }
        LinkedHashSet<String> ids = new LinkedHashSet<>();
        for (JsonNode node : array) {
            String id = node.isTextual() ? node.asText()
                    : firstText(node.path("id").asText(null),
                            node.path("name").asText(null),
                            node.path("model").asText(null));
            if (id != null && !id.isBlank()) {
                ids.add(id.trim());
            }
        }
        return List.copyOf(ids);
    }

    private static String firstText(String... candidates) {
        for (String candidate : candidates) {
            if (candidate != null && !candidate.isBlank()) {
                return candidate;
            }
        }
        return null;
    }

    /** 尽量把上游的原始错误说明带给用户，而不是只报“BadRequest”。 */
    static String describeHttpError(int status, String rawBody) {
        String body = rawBody == null ? "" : rawBody.trim();
        String upstream = null;
        if (body.startsWith("{")) {
            try {
                JsonNode json = new ObjectMapper().readTree(body);
                upstream = firstText(json.path("error").path("message").asText(null),
                        json.path("message").asText(null),
                        json.path("error").asText(null));
            } catch (Exception ignored) {
                // 不是合法 JSON，退化成原始文本
            }
        }
        String detail = upstream == null || upstream.isBlank() ? body : upstream;
        return "HTTP " + status + (detail.isBlank() ? "" : "：" + abbreviate(detail.replaceAll("\\s+", " ")));
    }

    /** 取最底层异常，"Connect timed out" 这类信息才是有用的。 */
    static String rootCause(Throwable e) {
        Throwable root = deepestCause(e);
        String message = root.getMessage();
        return root.getClass().getSimpleName() + (message == null || message.isBlank() ? "" : " - " + message);
    }

    /** 逐层取 cause；异常链自引用时停下，避免死循环。 */
    static Throwable deepestCause(Throwable e) {
        Throwable root = e;
        while (root.getCause() != null && root.getCause() != root) {
            root = root.getCause();
        }
        return root;
    }

    /**
     * 按底层异常细分提示。连接超时、读取超时、拒绝连接、域名解析、TLS 的成因完全不同，
     * 统一回一句“请检查 Base URL 与网络/代理”会把排查方向带偏。
     */
    static String describeNetworkError(String url, Throwable error) {
        Throwable root = deepestCause(error);
        String detail = rootCause(error);
        if (root instanceof UnknownHostException) {
            return "无法解析 " + url + " 的域名（" + detail + "），请检查 Base URL 拼写或本机 DNS";
        }
        if (root instanceof SocketTimeoutException) {
            if (isConnectPhaseTimeout(root)) {
                return "连接 " + url + " 超时（" + detail + "），通常是网络到该地址不稳定、"
                        + "被代理或防火墙拦截，可稍后重试或更换网络";
            }
            return "已连上 " + url + " 但读取响应超时（" + detail + "），"
                    + "可调大 ai.timeout-seconds 或换用更快的模型后重试";
        }
        if (root instanceof ConnectException || root instanceof NoRouteToHostException) {
            return "无法连接 " + url + "（" + detail + "），"
                    + "请确认端口正确、服务已启动且未被本机防火墙拦截";
        }
        if (root instanceof SSLException) {
            return "与 " + url + " 建立 TLS 连接失败（" + detail + "），请检查代理、证书或被中间设备拦截";
        }
        return "无法连接 " + url + "（" + detail + "），请检查 Base URL 与网络";
    }

    /**
     * 只重试连接阶段的失败：这时请求还没发出去，重试不会重复计费或产生重复副作用。
     * 读超时说明上游可能已经处理完请求，重试有重复计费的风险，因此不重试。
     */
    static boolean isRetryableConnectFailure(Throwable error) {
        Throwable root = deepestCause(error);
        return root instanceof UnknownHostException
                || root instanceof NoRouteToHostException
                || (root instanceof SocketTimeoutException && isConnectPhaseTimeout(root));
    }

    /** HttpURLConnection 的连接超时消息是 "Connect timed out"，读超时是 "Read timed out"。 */
    private static boolean isConnectPhaseTimeout(Throwable root) {
        String message = root.getMessage();
        return message != null && message.toLowerCase(Locale.ROOT).contains("connect");
    }

    private static String abbreviate(String text) {
        if (text == null) {
            return "null";
        }
        return text.length() <= 300 ? text : text.substring(0, 300) + "...";
    }
}
