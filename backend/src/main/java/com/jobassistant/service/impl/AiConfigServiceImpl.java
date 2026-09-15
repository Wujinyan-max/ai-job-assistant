package com.jobassistant.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.jobassistant.ai.AiProtocolCodec;
import com.jobassistant.ai.AiRuntimeConfig;
import com.jobassistant.ai.ApiKeyCipher;
import com.jobassistant.common.BusinessException;
import com.jobassistant.common.ErrorCode;
import com.jobassistant.config.AiProperties;
import com.jobassistant.dto.AiConfigSaveDTO;
import com.jobassistant.entity.AiUserConfig;
import com.jobassistant.mapper.AiUserConfigMapper;
import com.jobassistant.security.SecurityUtils;
import com.jobassistant.service.AiConfigService;
import com.jobassistant.vo.AiConfigVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.net.URI;
import java.util.Locale;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class AiConfigServiceImpl implements AiConfigService {

    private static final Set<String> PROVIDERS = Set.of("OPENAI", "DEEPSEEK", "QWEN", "MOONSHOT", "OLLAMA", "CUSTOM");
    private final AiUserConfigMapper mapper;
    private final ApiKeyCipher cipher;
    private final AiProperties properties;

    @Override
    public AiConfigVO getMaskedConfig() {
        AiUserConfig stored = findCurrent();
        if (stored == null) {
            AiRuntimeConfig runtime = runtimeConfig();
            boolean hasServerKey = StringUtils.hasText(runtime.apiKey());
            return new AiConfigVO(properties.isEnabled(), runtime.provider(), runtime.apiMode(), runtime.baseUrl(),
                    runtime.model(), !properties.isEnabled() || !hasServerKey, hasServerKey,
                    hasServerKey ? "系统托管" : null);
        }
        return toView(stored, toRuntime(stored));
    }

    @Override
    public AiConfigVO save(AiConfigSaveDTO dto) {
        validate(dto);
        Long userId = SecurityUtils.getUserId();
        AiUserConfig entity = findCurrent();
        boolean create = entity == null;
        if (create) {
            entity = new AiUserConfig();
            entity.setUserId(userId);
        }
        entity.setProvider(dto.provider().toUpperCase(Locale.ROOT));
        entity.setApiMode(dto.apiMode());
        entity.setBaseUrl(trimSlash(dto.baseUrl()));
        entity.setModel(dto.model().trim());
        entity.setEnabled(Boolean.FALSE.equals(dto.enabled()) ? 0 : 1);
        if (StringUtils.hasText(dto.apiKey())) entity.setApiKeyEncrypted(cipher.encrypt(dto.apiKey().trim()));
        if (create) mapper.insert(entity); else mapper.updateById(entity);
        return toView(entity, toRuntime(entity));
    }

    @Override
    public AiRuntimeConfig runtimeConfig() {
        AiUserConfig stored = findCurrent();
        if (stored == null) {
            return new AiRuntimeConfig("OPENAI", AiProtocolCodec.CHAT_COMPLETIONS,
                    properties.getBaseUrl(), properties.getApiKey(), properties.getModel(), properties.getTimeoutSeconds());
        }
        return toRuntime(stored);
    }

    private AiUserConfig findCurrent() {
        return mapper.selectOne(new LambdaQueryWrapper<AiUserConfig>()
                .eq(AiUserConfig::getUserId, SecurityUtils.getUserId()).last("LIMIT 1"));
    }

    private AiRuntimeConfig toRuntime(AiUserConfig entity) {
        String key = entity.getEnabled() != null && entity.getEnabled() == 1
                ? cipher.decrypt(entity.getApiKeyEncrypted()) : null;
        return new AiRuntimeConfig(entity.getProvider(), entity.getApiMode(), entity.getBaseUrl(), key,
                entity.getModel(), properties.getTimeoutSeconds());
    }

    private AiConfigVO toView(AiUserConfig stored, AiRuntimeConfig runtime) {
        String key = runtime.apiKey();
        boolean enabled = stored == null ? properties.isEnabled() : stored.getEnabled() == 1;
        return new AiConfigVO(enabled, runtime.provider(), runtime.apiMode(), runtime.baseUrl(), runtime.model(),
                !enabled || !StringUtils.hasText(key), StringUtils.hasText(key), cipher.mask(key));
    }

    private void validate(AiConfigSaveDTO dto) {
        String provider = dto.provider().toUpperCase(Locale.ROOT);
        if (!PROVIDERS.contains(provider)) throw new BusinessException(ErrorCode.PARAM_ERROR, "不支持的 AI 厂商");
        if (!AiProtocolCodec.CHAT_COMPLETIONS.equals(dto.apiMode())
                && !AiProtocolCodec.RESPONSES.equals(dto.apiMode())) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "不支持的 API 模式");
        }
        try {
            URI uri = URI.create(dto.baseUrl().trim());
            if (!("http".equalsIgnoreCase(uri.getScheme()) || "https".equalsIgnoreCase(uri.getScheme()))
                    || uri.getHost() == null || uri.getUserInfo() != null || uri.getFragment() != null) {
                throw new IllegalArgumentException();
            }
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "Base URL 必须是有效的 HTTP(S) 地址");
        }
    }

    private String trimSlash(String value) {
        String result = value.trim();
        while (result.endsWith("/")) result = result.substring(0, result.length() - 1);
        return result;
    }
}
