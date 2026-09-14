package com.jobassistant.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * AI 相关配置，对应 application.yml 的 ai.* 节点。
 * <p>兼容 OpenAI 协议的任何服务（OpenAI / DeepSeek / 通义千问兼容模式 / 本地 Ollama 等）。</p>
 */
@Data
@ConfigurationProperties(prefix = "ai")
public class AiProperties {

    private boolean enabled = true;

    /** 形如 https://api.openai.com/v1 */
    private String baseUrl = "https://api.openai.com/v1";

    /** 为空时使用本地模拟实现，保证不配 key 也能跑通全流程 */
    private String apiKey;

    private String model = "gpt-4o-mini";

    private int timeoutSeconds = 90;

    public boolean hasApiKey() {
        return apiKey != null && !apiKey.isBlank();
    }
}
