package com.jobassistant.service;

import com.jobassistant.common.BusinessException;
import com.jobassistant.common.ErrorCode;
import com.jobassistant.config.RateLimitProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 注册限流行为测试。
 * <p>登录不做限流，所以这里没有任何登录相关断言。</p>
 */
class RateLimitServiceTest {

    private RateLimitProperties properties;
    private RateLimitService service;

    @BeforeEach
    void setUp() {
        properties = new RateLimitProperties();
        service = new RateLimitService(properties);
    }

    @Test
    @DisplayName("正常注册：未达阈值时放行")
    void allowRegisterUnderLimit() {
        String ip = "1.1.1.1";
        assertThatCode(() -> {
            for (int i = 0; i < properties.getRegisterMaxPerIp(); i++) {
                service.checkRegister(ip);
            }
        }).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("注册限流：超过窗口内次数上限后拒绝")
    void rejectRegisterOverWindowLimit() {
        String ip = "2.2.2.2";
        for (int i = 0; i < properties.getRegisterMaxPerIp(); i++) {
            service.checkRegister(ip);
        }
        assertThatThrownBy(() -> service.checkRegister(ip))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getCode())
                .isEqualTo(ErrorCode.TOO_MANY_REQUESTS.getCode());
    }

    @Test
    @DisplayName("注册限流：不同 IP 互不影响")
    void registerLimitIsPerIp() {
        String ipA = "3.3.3.3";
        String ipB = "4.4.4.4";
        for (int i = 0; i < properties.getRegisterMaxPerIp(); i++) {
            service.checkRegister(ipA);
        }
        assertThatCode(() -> service.checkRegister(ipB)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("注册限流：成功后累计达标，即使窗口重置也拒绝")
    void rejectRegisterOverTotalLimit() {
        String ip = "5.5.5.5";
        // 模拟多次注册成功，每次都跨过窗口限制（重置窗口队列）
        for (int i = 0; i < properties.getRegisterMaxTotalPerIp(); i++) {
            service.recordRegisterSuccess(ip);
        }
        assertThatThrownBy(() -> service.checkRegister(ip))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("关闭限流开关后不做任何拦截")
    void disabledAllowsEverything() {
        properties.setEnabled(false);
        String ip = "9.9.9.9";
        for (int i = 0; i < 100; i++) {
            service.checkRegister(ip);
        }
        assertThatCode(() -> service.checkRegister(ip)).doesNotThrowAnyException();
    }
}
