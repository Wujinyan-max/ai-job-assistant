package com.jobassistant.security;

import com.jobassistant.config.JwtProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;

/**
 * JWT 生成与解析。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JwtUtils {

    private static final String CLAIM_USERNAME = "username";

    private final JwtProperties jwtProperties;

    private volatile SecretKey cachedKey;

    private SecretKey key() {
        if (cachedKey == null) {
            cachedKey = Keys.hmacShaKeyFor(jwtProperties.getSecret().getBytes(StandardCharsets.UTF_8));
        }
        return cachedKey;
    }

    /** 生成 token，subject 存用户 ID。 */
    public String generateToken(Long userId, String username) {
        Instant now = Instant.now();
        Instant expireAt = now.plusSeconds(jwtProperties.getExpireMinutes() * 60);
        return Jwts.builder()
                .subject(String.valueOf(userId))
                .claim(CLAIM_USERNAME, username)
                .issuedAt(Date.from(now))
                .expiration(Date.from(expireAt))
                .signWith(key())
                .compact();
    }

    /**
     * 解析 token，非法或过期返回 null（由调用方决定如何处理）。
     */
    public LoginUser parseToken(String token) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(key())
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
            return new LoginUser(Long.valueOf(claims.getSubject()), claims.get(CLAIM_USERNAME, String.class));
        } catch (JwtException | IllegalArgumentException e) {
            log.debug("JWT 解析失败: {}", e.getMessage());
            return null;
        }
    }

    /** 从请求头原始值中剥离前缀，得到纯 token。 */
    public String resolveToken(String headerValue) {
        if (headerValue == null || headerValue.isBlank()) {
            return null;
        }
        String prefix = jwtProperties.getPrefix();
        if (prefix != null && !prefix.isBlank() && headerValue.startsWith(prefix)) {
            return headerValue.substring(prefix.length()).trim();
        }
        return headerValue.trim();
    }

    public String getHeaderName() {
        return jwtProperties.getHeader();
    }

    public long getExpireSeconds() {
        return jwtProperties.getExpireMinutes() * 60;
    }
}
