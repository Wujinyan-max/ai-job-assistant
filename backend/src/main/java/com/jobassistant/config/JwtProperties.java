package com.jobassistant.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * JWT 相关配置，对应 application.yml 的 jwt.* 节点。
 */
@Data
@ConfigurationProperties(prefix = "jwt")
public class JwtProperties {

    /** 签名密钥，HS256 要求 >= 256 bit */
    private String secret;

    /** 过期时间（分钟） */
    private long expireMinutes = 1440;

    /** 存放 token 的请求头 */
    private String header = "Authorization";

    /** token 前缀 */
    private String prefix = "Bearer ";
}
