package com.jobassistant.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AiProtocolCodecTest {

    private final ObjectMapper mapper = new ObjectMapper();
    private final AiProtocolCodec codec = new AiProtocolCodec();
    private final AiRequest request = new AiRequest(AiTask.JD_ANALYZE, "system rules", "user input", null);

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
}
