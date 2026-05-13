package com.powergateway.controller;

import cn.dev33.satoken.stp.StpUtil;
import com.powergateway.aop.SysLogRecord;
import com.powergateway.common.Result;
import com.powergateway.model.dto.LoginRequest;
import com.powergateway.model.dto.LoginResponse;
import com.powergateway.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import java.util.List;

/**
 * 认证接口：登录、登出
 */
@Tag(name = "认证接口")
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    @Autowired
    private AuthService authService;

    @SysLogRecord(module = "认证", action = "用户登录")
    @Operation(summary = "用户登录")
    @PostMapping("/login")
    public Result<LoginResponse> login(@RequestBody @Valid LoginRequest req) {
        return Result.success(authService.login(req));
    }

    @SysLogRecord(module = "认证", action = "用户登出")
    @Operation(summary = "用户登出")
    @PostMapping("/logout")
    public Result<Void> logout() {
        authService.logout();
        return Result.success();
    }

    @Operation(summary = "获取当前登录用户信息（需要登录）")
    @GetMapping("/info")
    public Result<LoginResponse.UserInfo> info() {
        return Result.success(authService.getCurrentUserInfo());
    }

    @Operation(summary = "获取当前用户可见菜单列表（SYS-3）")
    @GetMapping("/menu")
    public Result<List<String>> menu() {
        return Result.success(authService.getMenuForCurrentUser());
    }
}
