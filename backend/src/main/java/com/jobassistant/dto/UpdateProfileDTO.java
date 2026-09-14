package com.jobassistant.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

@Schema(description = "修改个人资料请求")
public record UpdateProfileDTO(
        @Size(max = 50, message = "昵称最长 50 个字符")
        String nickname,

        @Email(message = "邮箱格式不正确")
        String email,

        @Size(max = 20, message = "手机号最长 20 个字符")
        String phone,

        String avatar,

        String education,

        @Min(value = 0, message = "工作年限不能为负数")
        @Max(value = 60, message = "工作年限不合法")
        Integer workYears
) {
}
