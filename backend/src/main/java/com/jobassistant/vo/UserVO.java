package com.jobassistant.vo;

import com.jobassistant.entity.User;

import java.time.LocalDateTime;

/**
 * 用户信息视图，不包含密码。
 */
public record UserVO(
        Long id,
        String username,
        String nickname,
        String email,
        String phone,
        String avatar,
        String education,
        Integer workYears,
        LocalDateTime createdAt
) {
    public static UserVO from(User user) {
        return new UserVO(user.getId(), user.getUsername(), user.getNickname(), user.getEmail(),
                user.getPhone(), user.getAvatar(), user.getEducation(), user.getWorkYears(),
                user.getCreatedAt());
    }
}
