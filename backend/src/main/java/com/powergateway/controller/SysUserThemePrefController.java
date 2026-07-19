package com.powergateway.controller;

import cn.dev33.satoken.annotation.SaCheckLogin;
import cn.dev33.satoken.stp.StpUtil;
import com.powergateway.common.Result;
import com.powergateway.service.SysUserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

/**
 * 用户主题偏好 Controller（UX-A）
 */
@RestController
@RequestMapping("/api/user")
public class SysUserThemePrefController {

    @Autowired
    private SysUserService sysUserService;

    /**
     * 获取用户主题偏好
     *
     * @return JSON 字符串或 null
     */
    @GetMapping("/theme-pref")
    @SaCheckLogin
    public Result<String> get() {
        Long uid = Long.valueOf(StpUtil.getLoginId().toString());
        return Result.success(sysUserService.getThemePref(uid));
    }

    /**
     * 设置用户主题偏好
     *
     * @param body JSON 字符串
     */
    @PutMapping("/theme-pref")
    @SaCheckLogin
    public Result<Void> put(@RequestBody String body) {
        Long uid = Long.valueOf(StpUtil.getLoginId().toString());
        sysUserService.setThemePref(uid, body);
        return Result.success();
    }
}
