package com.fiveok.express.user.controller;

import com.fiveok.express.base.utils.Result;
import com.fiveok.express.user.dto.LoginRequest;
import com.fiveok.express.user.dto.RegisterRequest;
import com.fiveok.express.user.dto.UpdateProfileRequest;
import com.fiveok.express.user.entity.User;
import com.fiveok.express.user.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @PostMapping("/api/auth/register")
    public Result<Void> register(@Valid @RequestBody RegisterRequest request) {
        userService.register(request);
        return Result.success();
    }

    @PostMapping("/api/auth/login")
    public Result<Map<String, String>> login(@Valid @RequestBody LoginRequest request) {
        return Result.success(Map.of("token", userService.login(request)));
    }

    @GetMapping("/api/user/profile")
    public Result<User> profile(HttpServletRequest httpRequest) {
        Long userId = (Long) httpRequest.getAttribute("userId");
        return Result.success(userService.getProfile(userId));
    }

    @PatchMapping("/api/user/profile")
    public Result<Void> updateProfile(@Valid @RequestBody UpdateProfileRequest request,
                                      HttpServletRequest httpRequest) {
        Long userId = (Long) httpRequest.getAttribute("userId");
        userService.updateProfile(userId, request);
        return Result.success();
    }
}
