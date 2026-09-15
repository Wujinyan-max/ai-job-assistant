package com.jobassistant.service;

import com.jobassistant.ai.AiRuntimeConfig;
import com.jobassistant.dto.AiConfigSaveDTO;
import com.jobassistant.vo.AiConfigVO;

public interface AiConfigService {
    AiConfigVO getMaskedConfig();
    AiConfigVO save(AiConfigSaveDTO dto);
    AiRuntimeConfig runtimeConfig();
}
