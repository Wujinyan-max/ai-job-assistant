package com.jobassistant.service;

import com.jobassistant.ai.ApiKeyCipher;
import com.jobassistant.common.BusinessException;
import com.jobassistant.config.AiProperties;
import com.jobassistant.dto.AiConfigSaveDTO;
import com.jobassistant.entity.AiUserConfig;
import com.jobassistant.mapper.AiUserConfigMapper;
import com.jobassistant.security.LoginUser;
import com.jobassistant.service.impl.AiConfigServiceImpl;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AiConfigServiceTest {

    private final AiUserConfigMapper mapper = mock(AiUserConfigMapper.class);
    private final ApiKeyCipher cipher = new ApiKeyCipher("test-secret-that-is-long-enough");
    private AiConfigServiceImpl service;

    @BeforeEach
    void setUp() {
        AiProperties properties = new AiProperties();
        service = new AiConfigServiceImpl(mapper, cipher, properties);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(new LoginUser(7L, "alice", "token-1"), null));
    }

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void returnsMaskedKeyAndNeverPlaintext() {
        AiUserConfig stored = storedConfig();
        when(mapper.selectOne(any())).thenReturn(stored);

        var result = service.getMaskedConfig();

        assertThat(result.hasApiKey()).isTrue();
        assertThat(result.apiKeyMasked()).isEqualTo("sk-u••••••alue");
        assertThat(result.toString()).doesNotContain("sk-user-secret-value");
    }

    @Test
    void blankKeyPreservesExistingCiphertextOnUpdate() {
        AiUserConfig stored = storedConfig();
        when(mapper.selectOne(any())).thenReturn(stored);

        service.save(new AiConfigSaveDTO("DEEPSEEK", "CHAT_COMPLETIONS",
                "https://api.deepseek.com/v1", "deepseek-chat", "", true, null, null, null, null));

        verify(mapper).updateById(org.mockito.ArgumentMatchers.argThat(
                value -> stored.getApiKeyEncrypted().equals(value.getApiKeyEncrypted())
                        && value.getUserId().equals(7L)));
    }

    @Test
    void savesThinkingModeAndUnitPrices() {
        when(mapper.selectOne(any())).thenReturn(null);

        service.save(new AiConfigSaveDTO("DEEPSEEK", "CHAT_COMPLETIONS", "https://api.deepseek.com/v1",
                "deepseek-flash", "sk-user-secret-value", true, "OFF", 1.0, 0.02, 4.0));

        verify(mapper).insert(org.mockito.ArgumentMatchers.argThat(value ->
                "OFF".equals(value.getThinkingMode())
                        && Double.valueOf(1.0).equals(value.getInputPrice())
                        && Double.valueOf(0.02).equals(value.getCachePrice())
                        && Double.valueOf(4.0).equals(value.getOutputPrice())
                        && value.getUserId().equals(7L)));
    }

    @Test
    void blankThinkingModeFallsBackToVendorDefault() {
        when(mapper.selectOne(any())).thenReturn(null);

        service.save(new AiConfigSaveDTO("DEEPSEEK", "CHAT_COMPLETIONS", "https://api.deepseek.com/v1",
                "deepseek-flash", "sk-user-secret-value", true, null, null, null, null));

        verify(mapper).insert(org.mockito.ArgumentMatchers.argThat(
                value -> "DEFAULT".equals(value.getThinkingMode())));
    }

    @Test
    void rejectsUnknownThinkingModeAndNegativePrice() {
        when(mapper.selectOne(any())).thenReturn(null);

        assertThatThrownBy(() -> service.save(new AiConfigSaveDTO("DEEPSEEK", "CHAT_COMPLETIONS",
                "https://api.deepseek.com/v1", "deepseek-flash", "sk-x", true, "SOMETIMES", null, null, null)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("思考模式");

        assertThatThrownBy(() -> service.save(new AiConfigSaveDTO("DEEPSEEK", "CHAT_COMPLETIONS",
                "https://api.deepseek.com/v1", "deepseek-flash", "sk-x", true, "ON", -1.0, null, null)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("输入单价");
    }

    @Test
    void serverFallbackKeyIsNeverPartiallyExposed() {
        AiProperties properties = new AiProperties();
        properties.setApiKey("sk-server-fallback-secret");
        service = new AiConfigServiceImpl(mapper, cipher, properties);
        when(mapper.selectOne(any())).thenReturn(null);

        var result = service.getMaskedConfig();

        assertThat(result.hasApiKey()).isTrue();
        assertThat(result.apiKeyMasked()).isEqualTo("系统托管");
        assertThat(result.toString()).doesNotContain("sk-s").doesNotContain("cret");
    }

    private AiUserConfig storedConfig() {
        AiUserConfig config = new AiUserConfig();
        config.setId(10L);
        config.setUserId(7L);
        config.setProvider("OPENAI");
        config.setApiMode("RESPONSES");
        config.setBaseUrl("https://api.openai.com/v1");
        config.setModel("gpt-test");
        config.setApiKeyEncrypted(cipher.encrypt("sk-user-secret-value"));
        config.setEnabled(1);
        return config;
    }
}
