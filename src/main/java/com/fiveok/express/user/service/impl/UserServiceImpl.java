package com.fiveok.express.user.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fiveok.express.base.exception.BusinessException;
import com.fiveok.express.base.utils.JwtUtil;
import com.fiveok.express.user.api.UserFacade;
import com.fiveok.express.user.api.UserView;
import com.fiveok.express.user.dto.LoginRequest;
import com.fiveok.express.user.dto.RegisterRequest;
import com.fiveok.express.user.dto.UpdateProfileRequest;
import com.fiveok.express.user.entity.User;
import com.fiveok.express.user.enums.UserRole;
import com.fiveok.express.user.mapper.UserMapper;
import com.fiveok.express.user.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 用户服务实现
 * <p>
 * 同时实现 UserService（模块内部接口）和 UserFacade（跨模块门面接口）。
 * 这样 order 模块只能拿到 UserFacade 的视角，看不到 UserService 的细节。
 * </p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService, UserFacade {

    private final UserMapper userMapper;
    private final JwtUtil jwtUtil;

    private static final BCryptPasswordEncoder PASSWORD_ENCODER = new BCryptPasswordEncoder(12);

    // ============ UserService 模块内部方法 ============

    @Override
    public void register(RegisterRequest request) {
        boolean exists = userMapper.selectCount(
                new LambdaQueryWrapper<User>().eq(User::getPhone, request.getPhone())
        ) > 0;
        if (exists) {
            throw new BusinessException(409, "user.phone.exists");
        }

        User user = new User();
        user.setName(request.getName());
        user.setPhone(request.getPhone());
        user.setPassword(PASSWORD_ENCODER.encode(request.getPassword()));

        // 默认注册为普通用户；如果前端明确传 COURIER，则注册为快递员。
        // 不允许通过注册接口创建 ADMIN，避免权限过高。
        String role = UserRole.USER.name();
        if (UserRole.COURIER.name().equalsIgnoreCase(request.getRole())) {
            role = UserRole.COURIER.name();
        }
        user.setRole(role);

        userMapper.insert(user);
        log.info("用户注册成功: phone={}", request.getPhone());
    }

    @Override
    public String login(LoginRequest request) {
        User user = userMapper.selectOne(
                new LambdaQueryWrapper<User>()
                        .eq(User::getPhone, request.getPhone())
                        .select(User::getId, User::getPhone, User::getPassword, User::getRole)
        );
        if (user == null || !PASSWORD_ENCODER.matches(request.getPassword(), user.getPassword())) {
            throw new BusinessException(401, "user.login.fail");
        }
        String token = jwtUtil.generateToken(user.getId(), user.getRole());
        log.info("用户登录成功: userId={}", user.getId());
        return token;
    }

    @Override
    public User getProfile(Long userId) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(404, "user.not.found");
        }
        return user;
    }

    @Override
    public void updateProfile(Long userId, UpdateProfileRequest request) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(404, "user.not.found");
        }
        if (StringUtils.hasText(request.getName())) {
            user.setName(request.getName());
        }
        if (StringUtils.hasText(request.getAvatar())) {
            user.setAvatar(request.getAvatar());
        }
        userMapper.updateById(user);
    }

    // ============ UserFacade 跨模块对外方法 ============

    @Override
    public Map<Long, UserView> findByIds(List<Long> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return Collections.emptyMap();
        }
        List<User> users = userMapper.selectBatchIds(userIds);
        return users.stream()
                .map(this::toView)
                .collect(Collectors.toMap(UserView::getId, Function.identity()));
    }

    @Override
    public UserView findById(Long userId) {
        User user = userMapper.selectById(userId);
        return user == null ? null : toView(user);
    }

    @Override
    public boolean isCourierBanned(Long courierId) {
        if (courierId == null) return false;
        return userMapper.countBanned(courierId) > 0;
    }

    @Override
    public boolean recordCourierOverdue(Long courierId, int threshold, int banDurationDays) {
        if (courierId == null) return false;

        // 1) 先 +1
        int rows = userMapper.incrementOverdueCount(courierId);
        if (rows == 0) {
            log.warn("累加超时计数失败：快递员不存在 courierId={}", courierId);
            return false;
        }

        // 2) 再尝试触发封禁（CAS：count >= threshold 时清零并设 ban_until）
        int banned = userMapper.triggerBanIfReached(courierId, threshold, banDurationDays);
        if (banned > 0) {
            log.info("快递员被封禁: courierId={}, 封禁{}天", courierId, banDurationDays);
            return true;
        }
        return false;
    }

    /** Entity → View 转换（屏蔽内部敏感字段） */
    private UserView toView(User user) {
        return new UserView(
                user.getId(),
                user.getName(),
                user.getPhone(),
                user.getAvatar(),
                user.getRole(),
                user.getOverdueCount(),
                user.getBanUntil()
        );
    }
}
