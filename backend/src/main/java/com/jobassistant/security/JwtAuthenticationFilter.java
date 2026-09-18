package com.jobassistant.security;

import com.jobassistant.mapper.UserMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * 从请求头解析 JWT，写入 SecurityContext。
 * 解析失败不抛异常，交给后面的 AuthenticationEntryPoint 统一返回 401。
 */
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtUtils jwtUtils;
    private final UserMapper userMapper;

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain filterChain) throws ServletException, IOException {
        String token = jwtUtils.resolveToken(request.getHeader(jwtUtils.getHeaderName()));
        if (token != null && SecurityContextHolder.getContext().getAuthentication() == null) {
            LoginUser loginUser = jwtUtils.parseToken(token);
            // 单点登录校验：tokenId 必须与数据库一致，不一致说明已被新登录挤下线
            if (loginUser != null && isTokenCurrent(loginUser)) {
                UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                        loginUser, null, AuthorityUtils.createAuthorityList("ROLE_USER"));
                authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                SecurityContextHolder.getContext().setAuthentication(authentication);
            }
        }
        filterChain.doFilter(request, response);
    }

    /** 校验 token 中的 tokenId 是否与用户当前 tokenId 一致（单点登录）。 */
    private boolean isTokenCurrent(LoginUser loginUser) {
        if (loginUser.tokenId() == null) {
            return true;
        }
        var user = userMapper.selectById(loginUser.userId());
        return user != null && loginUser.tokenId().equals(user.getTokenId());
    }
}
