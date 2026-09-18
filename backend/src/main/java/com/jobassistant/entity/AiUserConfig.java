package com.jobassistant.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("ai_user_config")
public class AiUserConfig {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private String provider;
    private String apiMode;
    private String baseUrl;
    @JsonIgnore
    private String apiKeyEncrypted;
    private String model;
    /** 思考模式：DEFAULT / OFF / ON，见 {@link com.jobassistant.ai.AiThinkingMode} */
    private String thinkingMode;
    /** 单价（元/百万 tokens），用于估算费用，null 表示不估算 */
    private Double inputPrice;
    private Double cachePrice;
    private Double outputPrice;
    private Integer enabled;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    @JsonIgnore
    @TableLogic
    private Integer deleted;
}
