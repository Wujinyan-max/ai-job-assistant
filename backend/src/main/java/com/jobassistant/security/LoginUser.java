package com.jobassistant.security;

import java.security.Principal;

/**
 * 登录用户信息，作为 Spring Security 的 Authentication#getPrincipal() 存放。
 * 只放必要字段，不携带密码等敏感信息。
 */
public record LoginUser(Long userId, String username, String tokenId) implements Principal {

    @Override
    public String getName() {
        return username;
    }
}
