package com.jobassistant.security;

import com.jobassistant.common.BusinessException;
import com.jobassistant.common.ErrorCode;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * 获取当前登录用户的工具类。
 */
public final class SecurityUtils {

    private SecurityUtils() {
    }

    public static LoginUser getLoginUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof LoginUser loginUser)) {
            throw new BusinessException(ErrorCode.NOT_LOGIN);
        }
        return loginUser;
    }

    /** 当前登录用户 ID，业务层用它做数据隔离。 */
    public static Long getUserId() {
        return getLoginUser().userId();
    }

    public static String getUsername() {
        return getLoginUser().username();
    }
}
