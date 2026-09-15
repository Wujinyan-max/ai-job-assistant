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
    private Integer enabled;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    @JsonIgnore
    @TableLogic
    private Integer deleted;
}
