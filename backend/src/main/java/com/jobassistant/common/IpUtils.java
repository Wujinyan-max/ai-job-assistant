package com.jobassistant.common;

import jakarta.servlet.http.HttpServletRequest;

/**
 * 从请求中提取客户端真实 IP。
 * <p>
 * 服务部署在 Nginx / Cloudflare Tunnel 之后时，remoteAddr 拿到的是代理地址，
 * 需要按优先级读取转发头：CF-Connecting-IP → X-Real-IP → X-Forwarded-For。
 * </p>
 */
public final class IpUtils {

    private static final String[] HEADERS = {
            "CF-Connecting-IP",
            "X-Real-IP",
            "X-Forwarded-For"
    };

    private IpUtils() {
    }

    public static String getClientIp(HttpServletRequest request) {
        if (request == null) {
            return "unknown";
        }
        for (String header : HEADERS) {
            String value = request.getHeader(header);
            if (value != null && !value.isBlank() && !"unknown".equalsIgnoreCase(value)) {
                // X-Forwarded-For 可能是 "客户端IP, 代理1, 代理2"，取第一个
                int comma = value.indexOf(',');
                return comma > 0 ? value.substring(0, comma).trim() : value.trim();
            }
        }
        String remote = request.getRemoteAddr();
        return remote == null || remote.isBlank() ? "unknown" : remote;
    }
}
