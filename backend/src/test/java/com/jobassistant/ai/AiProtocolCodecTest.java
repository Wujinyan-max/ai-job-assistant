package com.jobassistant.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AiProtocolCodecTest {

    private final ObjectMapper mapper = new ObjectMapper();
    private final AiProtocolCodec codec = new AiProtocolCodec();
    private final AiRequest request = new AiRequest(AiTask.JD_ANALYZE, "system rules", "user input", null);

    @Test
    @DisplayName("Base URL 里多带了端点路径时先剥掉，不能拼成 /v1/models/models")
    void stripsEndpointPathFromBaseUrl() {
        assertThat(AiProtocolCodec.normalizeBaseUrl("https://api.openai.com/v1/models"))
                .isEqualTo("https://api.openai.com/v1");
        assertThat(AiProtocolCodec.normalizeBaseUrl("https://api.openai.com/v1/models/models/"))
                .isEqualTo("https://api.openai.com/v1");
        assertThat(AiProtocolCodec.normalizeBaseUrl("https://api.openai.com/v1/chat/completions"))
                .isEqualTo("https://api.openai.com/v1");
        assertThat(AiProtocolCodec.normalizeBaseUrl("https://api.openai.com/v1/responses"))
                .isEqualTo("https://api.openai.com/v1");

        // 正常地址不能被误伤
        assertThat(AiProtocolCodec.normalizeBaseUrl("https://api.deepseek.com/v1"))
                .isEqualTo("https://api.deepseek.com/v1");
        assertThat(AiProtocolCodec.normalizeBaseUrl("http://localhost:11434/v1"))
                .isEqualTo("http://localhost:11434/v1");
        assertThat(AiProtocolCodec.normalizeBaseUrl(null)).isEmpty();

        // 剥掉之后终点仍然是用户想调的那个地址
        AiRuntimeConfig config = new AiRuntimeConfig("OPENAI", "RESPONSES",
                "https://api.openai.com/v1/models", "secret", "gpt-test", 90);
        assertThat(codec.endpoint(config)).isEqualTo("https://api.openai.com/v1/responses");
    }

    @Test
    void buildsAndParsesChatCompletions() throws Exception {
        AiRuntimeConfig config = new AiRuntimeConfig("OPENAI", "CHAT_COMPLETIONS",
                "https://api.openai.com/v1/", "secret", "gpt-test", 90);
        JsonNode body = mapper.valueToTree(codec.buildBody(config, request));

        assertThat(codec.endpoint(config)).isEqualTo("https://api.openai.com/v1/chat/completions");
        assertThat(body.path("messages").path(0).path("role").asText()).isEqualTo("system");
        assertThat(body.path("response_format").path("type").asText()).isEqualTo("json_object");
        assertThat(codec.extractText("CHAT_COMPLETIONS", mapper.readTree(
                "{\"choices\":[{\"message\":{\"content\":\"{\\\"ok\\\":true}\"}}]}")))
                .isEqualTo("{\"ok\":true}");
    }

    @Test
    void buildsAndParsesResponses() throws Exception {
        AiRuntimeConfig config = new AiRuntimeConfig("OPENAI", "RESPONSES",
                "https://api.openai.com/v1", "secret", "gpt-test", 90);
        JsonNode body = mapper.valueToTree(codec.buildBody(config, request));

        assertThat(codec.endpoint(config)).isEqualTo("https://api.openai.com/v1/responses");
        assertThat(body.path("instructions").asText()).isEqualTo("system rules");
        assertThat(body.path("input").asText()).isEqualTo("user input");
        assertThat(codec.extractText("RESPONSES", mapper.readTree(
                "{\"output\":[{\"type\":\"message\",\"content\":[{\"type\":\"output_text\",\"text\":\"{\\\"ok\\\":true}\"}]}]}")))
                .isEqualTo("{\"ok\":true}");
    }

    @Test
    @DisplayName("带图时两套协议的内容块格式不能串：Chat 用 image_url 对象，Responses 用 input_image 字符串")
    void buildsImageBlocksPerProtocol() throws Exception {
        AiRequest withImages = new AiRequest(AiTask.RESUME_VISION, "system rules", "看这份简历", null,
                List.of("data:image/jpeg;base64,AAA", "data:image/jpeg;base64,BBB"));

        JsonNode chat = mapper.valueToTree(codec.buildBody(new AiRuntimeConfig("OPENAI",
                "CHAT_COMPLETIONS", "https://api.openai.com/v1", "secret", "gpt-test", 90), withImages));
        JsonNode chatBlob = chat.path("messages").path(1).path("content");
        assertThat(chatBlob.isArray()).isTrue();
        assertThat(chatBlob.path(0).path("type").asText()).isEqualTo("text");
        assertThat(chatBlob.path(0).path("text").asText()).isEqualTo("看这份简历");
        assertThat(chatBlob.path(1).path("type").asText()).isEqualTo("image_url");
        assertThat(chatBlob.path(1).path("image_url").path("url").asText())
                .isEqualTo("data:image/jpeg;base64,AAA");
        assertThat(chatBlob).hasSize(3);

        JsonNode responses = mapper.valueToTree(codec.buildBody(new AiRuntimeConfig("OPENAI",
                "RESPONSES", "https://api.openai.com/v1", "secret", "gpt-test", 90), withImages));
        JsonNode inputBlob = responses.path("input").path(0).path("content");
        assertThat(inputBlob.path(0).path("type").asText()).isEqualTo("input_text");
        assertThat(inputBlob.path(1).path("type").asText()).isEqualTo("input_image");
        // Responses 的 image_url 是字符串，写成对象会被上游拒绝
        assertThat(inputBlob.path(1).path("image_url").isTextual()).isTrue();
        assertThat(inputBlob.path(1).path("image_url").asText())
                .isEqualTo("data:image/jpeg;base64,AAA");

        // 纯文本请求不能被改成内容块数组，否则现有真实调用全部受影响
        JsonNode plainChat = mapper.valueToTree(codec.buildBody(new AiRuntimeConfig("OPENAI",
                "CHAT_COMPLETIONS", "https://api.openai.com/v1", "secret", "gpt-test", 90), request));
        assertThat(plainChat.path("messages").path(1).path("content").isTextual()).isTrue();
        JsonNode plainResponses = mapper.valueToTree(codec.buildBody(new AiRuntimeConfig("OPENAI",
                "RESPONSES", "https://api.openai.com/v1", "secret", "gpt-test", 90), request));
        assertThat(plainResponses.path("input").isTextual()).isTrue();
    }

    @Test
    @DisplayName("Chat 协议解析 DeepSeek 返回的 usage，含缓存命中与思维链")
    void parsesChatUsage() throws Exception {
        JsonNode response = mapper.readTree("""
                {"choices":[{"message":{"content":"{}"}}],
                 "usage":{"prompt_tokens":1200,"completion_tokens":800,"total_tokens":2000,
                          "prompt_cache_hit_tokens":512,"prompt_cache_miss_tokens":688,
                          "completion_tokens_details":{"reasoning_tokens":300}}}
                """);

        AiUsage usage = codec.parseUsage(AiProtocolCodec.CHAT_COMPLETIONS, response);

        assertThat(usage.inputTokens()).isEqualTo(1200);
        assertThat(usage.outputTokens()).isEqualTo(800);
        assertThat(usage.cachedTokens()).isEqualTo(512);
        assertThat(usage.reasoningTokens()).isEqualTo(300);
        assertThat(usage.totalTokens()).isEqualTo(2000);
    }

    @Test
    @DisplayName("Responses：思维链排在答案前面时只能取 message，不能把 reasoning 当答案")
    void extractTextIgnoresReasoningItems() throws Exception {
        JsonNode response = mapper.readTree("""
                {"output":[
                   {"type":"reasoning","content":[{"type":"reasoning_text","text":"We need answer JSON only."}]},
                   {"type":"message","content":[{"type":"output_text","text":"{\\"ok\\":true}"}]}
                 ]}
                """);

        assertThat(codec.extractText(AiProtocolCodec.RESPONSES, response)).isEqualTo("{\"ok\":true}");
    }

    @Test
    @DisplayName("Responses：message 内容为空时不返回空串，继续往后找真正的答案")
    void extractTextSkipsBlankMessageContent() throws Exception {
        JsonNode response = mapper.readTree("""
                {"output":[
                   {"type":"message","content":[{"type":"output_text","text":"   "}]},
                   {"type":"message","content":[{"type":"output_text","text":"{\\"ok\\":true}"}]}
                 ]}
                """);

        assertThat(codec.extractText(AiProtocolCodec.RESPONSES, response)).isEqualTo("{\"ok\":true}");
    }

    @Test
    @DisplayName("Responses：只有思维链时才退回 reasoning，不返回 null")
    void extractTextFallsBackToReasoningWhenNothingElse() throws Exception {
        JsonNode response = mapper.readTree("""
                {"output":[{"type":"reasoning","content":[{"type":"reasoning_text","text":"thinking..."}]}]}
                """);

        assertThat(codec.extractText(AiProtocolCodec.RESPONSES, response)).isEqualTo("thinking...");
    }

    @Test
    @DisplayName("Responses 协议解析 usage，字段名与 Chat 不同")
    void parsesResponsesUsage() throws Exception {
        JsonNode response = mapper.readTree("""
                {"output":[{"type":"message","content":[{"type":"output_text","text":"{}"}]}],
                 "usage":{"input_tokens":900,"output_tokens":400,"total_tokens":1300,
                          "input_tokens_details":{"cached_tokens":64},
                          "output_tokens_details":{"reasoning_tokens":128}}}
                """);

        AiUsage usage = codec.parseUsage(AiProtocolCodec.RESPONSES, response);

        assertThat(usage.inputTokens()).isEqualTo(900);
        assertThat(usage.outputTokens()).isEqualTo(400);
        assertThat(usage.cachedTokens()).isEqualTo(64);
        assertThat(usage.reasoningTokens()).isEqualTo(128);
    }

    @Test
    @DisplayName("厂商没返回 usage 时按 0 处理，不报错")
    void missingUsageFallsBackToEmpty() throws Exception {
        assertThat(codec.parseUsage(AiProtocolCodec.CHAT_COMPLETIONS, mapper.readTree("{}")))
                .isEqualTo(AiUsage.EMPTY);
        assertThat(codec.parseUsage(AiProtocolCodec.RESPONSES, null)).isEqualTo(AiUsage.EMPTY);
    }

    @Test
    @DisplayName("思考模式：默认不传参，关闭/开启才带厂商对应的字段")
    void sendsThinkingParamsOnlyWhenConfigured() throws Exception {
        JsonNode chatDefault = mapper.valueToTree(codec.buildBody(chatConfig(AiThinkingMode.DEFAULT), request));
        assertThat(chatDefault.has("thinking")).isFalse();
        assertThat(mapper.valueToTree(codec.buildBody(chatConfig(AiThinkingMode.OFF), request))
                .path("thinking").path("type").asText()).isEqualTo("disabled");
        assertThat(mapper.valueToTree(codec.buildBody(chatConfig(AiThinkingMode.ON), request))
                .path("thinking").path("type").asText()).isEqualTo("enabled");

        JsonNode responsesDefault = mapper.valueToTree(codec.buildBody(responsesConfig(AiThinkingMode.DEFAULT), request));
        assertThat(responsesDefault.has("reasoning")).isFalse();
        assertThat(mapper.valueToTree(codec.buildBody(responsesConfig(AiThinkingMode.OFF), request))
                .path("reasoning").path("effort").asText()).isEqualTo("none");
        assertThat(mapper.valueToTree(codec.buildBody(responsesConfig(AiThinkingMode.ON), request))
                .path("reasoning").path("effort").asText()).isEqualTo("high");
    }

    private AiRuntimeConfig chatConfig(String thinkingMode) {
        return new AiRuntimeConfig("DEEPSEEK", AiProtocolCodec.CHAT_COMPLETIONS,
                "https://api.deepseek.com/v1", "secret", "deepseek-flash", 90, AiPricing.NONE, thinkingMode);
    }

    private AiRuntimeConfig responsesConfig(String thinkingMode) {
        return new AiRuntimeConfig("DEEPSEEK", AiProtocolCodec.RESPONSES,
                "https://api.deepseek.com", "secret", "deepseek-flash", 90, AiPricing.NONE, thinkingMode);
    }
}
