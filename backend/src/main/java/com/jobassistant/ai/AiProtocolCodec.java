package com.jobassistant.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.jobassistant.common.BusinessException;
import com.jobassistant.common.ErrorCode;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class AiProtocolCodec {

    public static final String CHAT_COMPLETIONS = "CHAT_COMPLETIONS";
    public static final String RESPONSES = "RESPONSES";

    /**
     * 从厂商文档复制地址时，用户很容易把整个请求地址贴进 Base URL
     * （例如 {@code https://api.openai.com/v1/models}），直接往后拼端点就会变成
     * {@code /v1/models/models}。这里统一剥掉末尾的端点路径，只保留主机 + 版本前缀。
     * <p>顺序有意义：要先匹配 {@code /chat/completions}，否则只剥掉 {@code /completions}
     * 会剩下一个 {@code /chat}。</p>
     */
    private static final List<String> ENDPOINT_SUFFIXES =
            List.of("/chat/completions", "/completions", "/responses", "/models");

    /** 去掉末尾斜杠与端点路径，得到可以安全拼接的 Base URL。 */
    public static String normalizeBaseUrl(String baseUrl) {
        String base = baseUrl == null ? "" : baseUrl.trim();
        while (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        boolean stripped = true;
        while (stripped) {
            stripped = false;
            for (String suffix : ENDPOINT_SUFFIXES) {
                // 必须留出主机部分，避免把 "/models" 这种残缺值剥成空串
                if (base.length() > suffix.length() && base.endsWith(suffix)) {
                    base = base.substring(0, base.length() - suffix.length());
                    while (base.endsWith("/")) {
                        base = base.substring(0, base.length() - 1);
                    }
                    stripped = true;
                    break;
                }
            }
        }
        return base;
    }

    public String endpoint(AiRuntimeConfig config) {
        return normalizeBaseUrl(config.baseUrl())
                + (RESPONSES.equals(config.apiMode()) ? "/responses" : "/chat/completions");
    }

    public Map<String, Object> buildBody(AiRuntimeConfig config, AiRequest request) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", config.model());
        if (RESPONSES.equals(config.apiMode())) {
            body.put("instructions", request.systemPrompt());
            // 带图时 input 要拆成内容块数组，纯文本仍然直接给字符串（改动面最小）
            body.put("input", request.hasImages()
                    ? List.of(Map.of("role", "user",
                            "content", responsesContent(request)))
                    : request.userPrompt());
            body.put("text", Map.of("format", Map.of("type", "json_object")));
            applyResponsesThinking(body, config.thinkingMode());
        } else {
            body.put("temperature", 0.2);
            body.put("messages", List.of(
                    Map.of("role", "system", "content", request.systemPrompt()),
                    Map.of("role", "user", "content", request.hasImages()
                            ? chatContent(request) : request.userPrompt())));
            body.put("response_format", Map.of("type", "json_object"));
            applyChatThinking(body, config.thinkingMode());
        }
        return body;
    }

    /** Chat Completions 的图片块：先文字再图片，图片走 data URL 内联 */
    private List<Map<String, Object>> chatContent(AiRequest request) {
        List<Map<String, Object>> blocks = new ArrayList<>();
        blocks.add(Map.of("type", "text", "text", request.userPrompt()));
        for (String image : request.images()) {
            blocks.add(Map.of("type", "image_url", "image_url", Map.of("url", image)));
        }
        return blocks;
    }

    /** Responses 协议的内容块命名不同：input_text / input_image */
    private List<Map<String, Object>> responsesContent(AiRequest request) {
        List<Map<String, Object>> blocks = new ArrayList<>();
        blocks.add(Map.of("type", "input_text", "text", request.userPrompt()));
        for (String image : request.images()) {
            blocks.add(Map.of("type", "input_image", "image_url", image));
        }
        return blocks;
    }

    /** Responses 协议用 reasoning.effort 控制思考，none 表示关闭。 */
    private void applyResponsesThinking(Map<String, Object> body, String thinkingMode) {
        if (AiThinkingMode.OFF.equals(thinkingMode)) {
            body.put("reasoning", Map.of("effort", "none"));
        } else if (AiThinkingMode.ON.equals(thinkingMode)) {
            body.put("reasoning", Map.of("effort", "high"));
        }
    }

    /** Chat 协议用 thinking.type 控制思考开关；不传则跟随厂商默认（DeepSeek 默认开启）。 */
    private void applyChatThinking(Map<String, Object> body, String thinkingMode) {
        if (AiThinkingMode.OFF.equals(thinkingMode)) {
            body.put("thinking", Map.of("type", "disabled"));
        } else if (AiThinkingMode.ON.equals(thinkingMode)) {
            body.put("thinking", Map.of("type", "enabled"));
        }
    }

    /**
     * 取模型给出的最终答案。
     * <p>思考模型（DeepSeek 的 thinking 模式等）会在 output 里把 reasoning（思维链）排在 message（答案）
     * 前面，如果按顺序取第一个非空文本，拿到的会是思维链，最后报“不是合法 JSON”。所以这里只认
     * message / output_text 这类真正的内容，reasoning 仅在完全没有答案时才作为兜底。
     * <p>output_text 是聚合字段，某些实现会把思维链也拼进去，因此排在逐个 output 之后。
     */
    public String extractText(String apiMode, JsonNode response) {
        if (response == null) return null;
        if (!RESPONSES.equals(apiMode)) {
            return response.path("choices").path(0).path("message").path("content").asText(null);
        }
        String reasoningText = null;
        for (JsonNode output : response.path("output")) {
            boolean reasoningItem = "reasoning".equals(output.path("type").asText(""));
            for (JsonNode content : output.path("content")) {
                String text = content.path("text").asText(null);
                if (text == null || text.isBlank()) continue;
                if (!reasoningItem && !"reasoning_text".equals(content.path("type").asText(""))) {
                    return text;
                }
                if (reasoningText == null) {
                    reasoningText = text;
                }
            }
        }
        if (reasoningText != null) return reasoningText;
        return response.path("output_text").asText(null);
    }

    /**
     * 解析 usage。两套协议的字段名不一样，这里按协议优先、另一套兜底：
     * <ul>
     *   <li>Chat：prompt_tokens / completion_tokens，DeepSeek 额外给 prompt_cache_hit_tokens；</li>
     *   <li>Responses：input_tokens / output_tokens / input_tokens_details.cached_tokens。</li>
     * </ul>
     * 缺失的字段按 0 处理，不会因为厂商少给字段而报错。
     */
    public AiUsage parseUsage(String apiMode, JsonNode response) {
        JsonNode usage = response == null ? null : response.path("usage");
        if (usage == null || !usage.isObject()) {
            return AiUsage.EMPTY;
        }
        boolean responsesFirst = RESPONSES.equals(apiMode);
        int input = responsesFirst
                ? firstNumber(usage.path("input_tokens"), usage.path("prompt_tokens"))
                : firstNumber(usage.path("prompt_tokens"), usage.path("input_tokens"));
        int output = responsesFirst
                ? firstNumber(usage.path("output_tokens"), usage.path("completion_tokens"))
                : firstNumber(usage.path("completion_tokens"), usage.path("output_tokens"));
        int cached = firstNumber(usage.path("prompt_cache_hit_tokens"),
                usage.path("input_tokens_details").path("cached_tokens"),
                usage.path("prompt_tokens_details").path("cached_tokens"));
        int reasoning = firstNumber(usage.path("output_tokens_details").path("reasoning_tokens"),
                usage.path("completion_tokens_details").path("reasoning_tokens"));
        return new AiUsage(input, output, cached, reasoning);
    }

    private static int firstNumber(JsonNode... candidates) {
        for (JsonNode candidate : candidates) {
            if (candidate != null && candidate.isNumber()) {
                return candidate.asInt();
            }
        }
        return 0;
    }

    public void validateMode(String mode) {
        if (!CHAT_COMPLETIONS.equals(mode) && !RESPONSES.equals(mode)) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "不支持的 API 模式");
        }
    }
}
