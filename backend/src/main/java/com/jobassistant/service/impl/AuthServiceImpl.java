package com.jobassistant.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.jobassistant.common.BusinessException;
import com.jobassistant.common.ErrorCode;
import com.jobassistant.dto.ChangePasswordDTO;
import com.jobassistant.dto.LoginDTO;
import com.jobassistant.dto.RegisterDTO;
import com.jobassistant.dto.UpdateProfileDTO;
import com.jobassistant.entity.User;
import com.jobassistant.mapper.UserMapper;
import com.jobassistant.security.JwtUtils;
import com.jobassistant.security.SecurityUtils;
import com.jobassistant.service.AuthService;
import com.jobassistant.vo.LoginVO;
import com.jobassistant.vo.UserVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtils jwtUtils;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public LoginVO register(RegisterDTO dto) {
        Long exists = userMapper.selectCount(
                new LambdaQueryWrapper<User>().eq(User::getUsername, dto.username()));
        if (exists != null && exists > 0) {
            throw new BusinessException(ErrorCode.USERNAME_EXISTS);
        }

        User user = new User();
        user.setUsername(dto.username());
        user.setPassword(passwordEncoder.encode(dto.password()));
        user.setNickname(StringUtils.hasText(dto.nickname()) ? dto.nickname() : dto.username());
        user.setEmail(dto.email());
        user.setWorkYears(0);
        user.setStatus(1);
        userMapper.insert(user);

        log.info("新用户注册成功: {}", user.getUsername());
        return buildLoginVO(user);
    }

    @Override
    public LoginVO login(LoginDTO dto) {
        User user = userMapper.selectOne(
                new LambdaQueryWrapper<User>().eq(User::getUsername, dto.username()));
        // 用户名不存在与密码错误返回同一个提示，避免被枚举账号
        if (user == null || !passwordEncoder.matches(dto.password(), user.getPassword())) {
            throw new BusinessException(ErrorCode.PASSWORD_ERROR);
        }
        if (user.getStatus() != null && user.getStatus() == 0) {
            throw new BusinessException(ErrorCode.USER_DISABLED);
        }
        return buildLoginVO(user);
    }

    @Override
    public UserVO currentUser() {
        return UserVO.from(requireCurrentUser());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public UserVO updateProfile(UpdateProfileDTO dto) {
        User user = requireCurrentUser();
        if (StringUtils.hasText(dto.nickname())) {
            user.setNickname(dto.nickname());
        }
        user.setEmail(dto.email());
        user.setPhone(dto.phone());
        if (StringUtils.hasText(dto.avatar())) {
            user.setAvatar(dto.avatar());
        }
        user.setEducation(dto.education());
        if (dto.workYears() != null) {
            user.setWorkYears(dto.workYears());
        }
        userMapper.updateById(user);
        return UserVO.from(user);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void changePassword(ChangePasswordDTO dto) {
        User user = requireCurrentUser();
        if (!passwordEncoder.matches(dto.oldPassword(), user.getPassword())) {
            throw new BusinessException(ErrorCode.OLD_PASSWORD_ERROR);
        }
        User update = new User();
        update.setId(user.getId());
        update.setPassword(passwordEncoder.encode(dto.newPassword()));
        userMapper.updateById(update);
        log.info("用户 {} 修改了密码", user.getUsername());
    }

    @Override
    public User requireCurrentUser() {
        User user = userMapper.selectById(SecurityUtils.getUserId());
        if (user == null) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND);
        }
        return user;
    }

    private LoginVO buildLoginVO(User user) {
        String token = jwtUtils.generateToken(user.getId(), user.getUsername());
        return new LoginVO(token, "Bearer", jwtUtils.getExpireSeconds(), UserVO.from(user));
    }
}
