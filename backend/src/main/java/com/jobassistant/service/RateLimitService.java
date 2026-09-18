package com.jobassistant.service;

import com.jobassistant.common.BusinessException;
import com.jobassistant.common.ErrorCode;
import com.jobassistant.config.RateLimitProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 轻量级内存限流器。
 * <p>
 * 只用于防止注册刷号：登录不做任何拦截，避免用户自己反复试密码被锁在门外。
 * 单机部署足够；如果将来做成多实例集群，
 * 需要把计数器换成 Redis 实现，接口保持不变即可。
 * </p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RateLimitService {

    private final RateLimitProperties properties;

    /** 注册：IP -> 窗口内的时间戳队列 */
    private final Map<String, Deque<Instant>> registerWindow = new ConcurrentHashMap<>();

    /** 注册：IP -> 累计注册总数（不随窗口重置，用于长期防刷） */
    private final Map<String, AtomicInteger> registerTotal = new ConcurrentHashMap<>();

    // ------------------------------------------------------------------ 注册

    /**
     * 校验注册是否允许，不允许直接抛业务异常。
     */
    public void checkRegister(String ip) {
        if (!properties.isEnabled()) {
            return;
        }

        // 1. 长期总量：防止慢速批量注册
        int total = registerTotal.computeIfAbsent(ip, k -> new AtomicInteger()).get();
        if (total >= properties.getRegisterMaxTotalPerIp()) {
            log.warn("IP {} 注册总数已达上限 {}", ip, total);
            throw new BusinessException(ErrorCode.TOO_MANY_REQUESTS);
        }

        // 2. 短窗口：防止短时间内批量注册
        Deque<Instant> window = registerWindow.computeIfAbsent(ip, k -> new ArrayDeque<>());
        Duration span = Duration.ofMinutes(properties.getRegisterWindowMinutes());
        Instant now = Instant.now();
        synchronized (window) {
            evictExpired(window, now, span);
            if (window.size() >= properties.getRegisterMaxPerIp()) {
                log.warn("IP {} 在 {} 分钟内注册次数已达上限", ip, properties.getRegisterWindowMinutes());
                throw new BusinessException(ErrorCode.TOO_MANY_REQUESTS);
            }
            window.addLast(now);
        }
    }

    /** 注册成功后再累加总量（失败不计数） */
    public void recordRegisterSuccess(String ip) {
        registerTotal.computeIfAbsent(ip, k -> new AtomicInteger()).incrementAndGet();
    }

    // ------------------------------------------------------------------ 清理

    /** 每 10 分钟清理一次过期数据，避免内存无限增长 */
    @Scheduled(fixedDelay = 600_000L)
    public void cleanup() {
        Instant now = Instant.now();
        Duration registerSpan = Duration.ofMinutes(properties.getRegisterWindowMinutes());

        registerWindow.forEach((ip, window) -> {
            synchronized (window) {
                evictExpired(window, now, registerSpan);
                if (window.isEmpty()) {
                    registerWindow.remove(ip);
                }
            }
        });

        log.debug("限流器清理完成，当前注册键 {} 个", registerWindow.size());
    }

    private void evictExpired(Deque<Instant> window, Instant now, Duration span) {
        while (!window.isEmpty() && Duration.between(window.peekFirst(), now).compareTo(span) > 0) {
            window.pollFirst();
        }
    }
}
