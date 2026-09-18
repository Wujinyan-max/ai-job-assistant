package com.jobassistant.controller;

import com.jobassistant.common.IpUtils;
import com.jobassistant.common.Result;
import com.jobassistant.dto.ChangePasswordDTO;
import com.jobassistant.dto.LoginDTO;
import com.jobassistant.dto.RegisterDTO;
import com.jobassistant.dto.UpdateProfileDTO;
import com.jobassistant.service.AuthService;
import com.jobassistant.service.RateLimitService;
import com.jobassistant.vo.LoginVO;
import com.jobassistant.vo.UserVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "01-用户认证", description = "注册、登录、个人资料")
@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final RateLimitService rateLimitService;

    @Operation(summary = "注册", description = "注册成功后直接返回登录态，前端无需再调一次登录")
    @PostMapping("/register")
    public Result<LoginVO> register(@Valid @RequestBody RegisterDTO dto, HttpServletRequest request) {
        String ip = IpUtils.getClientIp(request);
        rateLimitService.checkRegister(ip);
        LoginVO vo = authService.register(dto);
        rateLimitService.recordRegisterSuccess(ip);
        return Result.success(vo);
    }

    @Operation(summary = "登录")
    @PostMapping("/login")
    public Result<LoginVO> login(@Valid @RequestBody LoginDTO dto) {
        // 登录不设频率限制与失败锁定：用户自己反复试密码不该被拦，账号安全由密码本身保证
        return Result.success(authService.login(dto));
    }

    @Operation(summary = "获取当前登录用户")
    @GetMapping("/me")
    public Result<UserVO> me() {
        return Result.success(authService.currentUser());
    }

    @Operation(summary = "修改个人资料")
    @PutMapping("/profile")
    public Result<UserVO> updateProfile(@Valid @RequestBody UpdateProfileDTO dto) {
        return Result.success(authService.updateProfile(dto));
    }

    @Operation(summary = "修改密码")
    @PutMapping("/password")
    public Result<Void> changePassword(@Valid @RequestBody ChangePasswordDTO dto) {
        authService.changePassword(dto);
        return Result.success();
    }
}
