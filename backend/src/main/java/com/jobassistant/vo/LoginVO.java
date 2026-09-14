package com.jobassistant.vo;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "登录结果")
public record LoginVO(
        @Schema(description = "JWT token")
        String token,

        @Schema(description = "token 类型")
        String tokenType,

        @Schema(description = "过期时间（秒）")
        long expiresIn,

        UserVO user
) {
}
