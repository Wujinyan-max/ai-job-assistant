package com.jobassistant.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 接口限流配置，对应 application.yml 的 rate-limit.* 节点。
 * <p>目前只对注册生效：登录不做任何频率限制或失败锁定。</p>
 */
@Data
@ConfigurationProperties(prefix = "rate-limit")
public class RateLimitProperties {

    /** 是否开启限流 */
    private boolean enabled = true;

    /** 注册限流：同一 IP 在窗口期内最多注册次数 */
    private int registerMaxPerIp = 3;

    /** 注册限流窗口（分钟） */
    private int registerWindowMinutes = 60;

    /** 同一 IP 注册总数上限（防止长期批量注册） */
    private int registerMaxTotalPerIp = 10;

}
