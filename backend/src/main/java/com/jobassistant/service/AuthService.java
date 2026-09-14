package com.jobassistant.service;

import com.jobassistant.dto.ChangePasswordDTO;
import com.jobassistant.dto.LoginDTO;
import com.jobassistant.dto.RegisterDTO;
import com.jobassistant.dto.UpdateProfileDTO;
import com.jobassistant.entity.User;
import com.jobassistant.vo.LoginVO;
import com.jobassistant.vo.UserVO;

public interface AuthService {

    /** 注册并直接返回登录态 */
    LoginVO register(RegisterDTO dto);

    LoginVO login(LoginDTO dto);

    UserVO currentUser();

    UserVO updateProfile(UpdateProfileDTO dto);

    void changePassword(ChangePasswordDTO dto);

    /** 取当前登录用户的实体，内部方法复用 */
    User requireCurrentUser();
}
