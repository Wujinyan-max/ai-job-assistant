package com.jobassistant.ai;

import com.jobassistant.common.BusinessException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 大模型经常把 JSON 包在 markdown 代码块里，或者前后加一句解释，
 * extractJson 负责把这些噪声去掉，这里覆盖几种常见形态。
 */
class AiClientTest {

    @Test
    @DisplayName("纯 JSON 原样返回")
    void keepsPlainJson() {
        assertThat(AiClient.extractJson("{\"score\":82}")).isEqualTo("{\"score\":82}");
    }

    @Test
    @DisplayName("剥掉 ```json 代码块包裹")
    void stripsMarkdownFence() {
        String content = """
                ```json
                {"score":82}
                ```
                """;
        assertThat(AiClient.extractJson(content)).isEqualTo("{\"score\":82}");
    }

    @Test
    @DisplayName("忽略模型前后附加的说明文字")
    void ignoresSurroundingProse() {
        String content = "好的，这是分析结果：\n{\"score\":82}\n希望对你有帮助。";
        assertThat(AiClient.extractJson(content)).isEqualTo("{\"score\":82}");
    }

    @Test
    @DisplayName("嵌套对象取最外层的一对花括号")
    void keepsNestedObject() {
        String content = "{\"score\":82,\"matchedSkills\":[\"Java\"]}";
        assertThat(AiClient.extractJson(content)).isEqualTo(content);
    }

    @Test
    @DisplayName("内容为空或没有 JSON 时抛业务异常，而不是返回 null")
    void rejectsContentWithoutJson() {
        assertThatThrownBy(() -> AiClient.extractJson(null)).isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> AiClient.extractJson("   ")).isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> AiClient.extractJson("完全没有 JSON 的一段话"))
                .isInstanceOf(BusinessException.class);
    }
}
