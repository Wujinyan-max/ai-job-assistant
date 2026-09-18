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

    /** 建立 TCP 连接的超时时间（秒）。遇到 CDN 抖动时靠重试兜底，所以不必设得过大。 */
    private int connectTimeoutSeconds = 15;

    /** 连接阶段失败时的总尝试次数（含首次请求）。 */
    private int maxAttempts = 3;

    /**
     * 可选 HTTP 代理主机。默认留空，系统会自动探测 Windows 代理设置；
     * 只有自动探测失败或需要强制指定时才配置。
     */
    private String proxyHost;

    /** 可选 HTTP 代理端口，与 {@link #proxyHost} 成对出现。 */
    private Integer proxyPort;

    public boolean hasApiKey() {
        return apiKey != null && !apiKey.isBlank();
    }

    /** 是否配置了完整的代理地址 */
    public boolean hasProxy() {
        return proxyHost != null && !proxyHost.isBlank() && proxyPort != null && proxyPort > 0;
    }
}
