package com.fiveok.express.user.service;

import com.fiveok.express.user.dto.LoginRequest;
import com.fiveok.express.user.dto.RegisterRequest;
import com.fiveok.express.user.dto.UpdateProfileRequest;
import com.fiveok.express.user.entity.User;

/**
 * 用户服务接口（同学C 模块内部使用）
 * <p>注：跨模块调用请走 {@link com.fiveok.express.user.api.UserFacade}。</p>
 */
public interface UserService {

    void register(RegisterRequest request);

    String login(LoginRequest request);

    User getProfile(Long userId);

    void updateProfile(Long userId, UpdateProfileRequest request);
}
