package com.powergateway.service;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.powergateway.config.MenuPermission;
import com.powergateway.dao.SysConfigMapper;
import com.powergateway.dao.SysUserMapper;
import com.powergateway.exception.BusinessException;
import com.powergateway.model.SysConfig;
import com.powergateway.model.SysUser;
import com.powergateway.model.dto.LoginRequest;
import com.powergateway.model.dto.LoginResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * 认证 Service：处理登录/登出业务逻辑
 */
@Service
public class AuthService {

    @Autowired
    private SysUserMapper sysUserMapper;

    @Autowired
    private SysConfigMapper sysConfigMapper;

    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    public LoginResponse login(LoginRequest req) {
        SysUser user = sysUserMapper.selectOne(
                new QueryWrapper<SysUser>().eq("username", req.getUsername())
        );
        if (user == null || !passwordEncoder.matches(req.getPassword(), user.getPassword())) {
            throw new BusinessException(401, "用户名或密码错误");
        }
        if (user.getStatus() != 1) {
            throw new BusinessException(403, "账号已被禁用");
        }

        StpUtil.login(user.getId());
        String token = StpUtil.getTokenValue();

        LoginResponse.UserInfo userInfo = new LoginResponse.UserInfo();
        userInfo.setId(user.getId());
        userInfo.setUsername(user.getUsername());
        userInfo.setRole(user.getRole());

        LoginResponse resp = new LoginResponse();
        resp.setToken(token);
        resp.setUserInfo(userInfo);
        return resp;
    }

    public void logout() {
        StpUtil.logout();
    }

    public LoginResponse.UserInfo getCurrentUserInfo() {
        long userId = StpUtil.getLoginIdAsLong();
        SysUser user = sysUserMapper.selectById(userId);
        LoginResponse.UserInfo userInfo = new LoginResponse.UserInfo();
        userInfo.setId(user.getId());
        userInfo.setUsername(user.getUsername());
        userInfo.setRole(user.getRole());
        return userInfo;
    }

    public List<String> getMenuForCurrentUser() {
        long userId = StpUtil.getLoginIdAsLong();
        SysUser user = sysUserMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(401, "用户不存在，请重新登录");
        }

        List<String> menus;
        switch (user.getRole()) {
            case "admin":    menus = new ArrayList<>(MenuPermission.ADMIN_MENUS); break;
            case "user":     menus = new ArrayList<>(MenuPermission.USER_MENUS);  break;
            case "readonly": menus = new ArrayList<>(MenuPermission.READONLY_MENUS); break;
            default:         menus = new ArrayList<>(MenuPermission.READONLY_MENUS); break;
        }

        SysConfig logConfig = sysConfigMapper.selectById(MenuPermission.LOG_MENU_CONFIG_KEY);
        if (logConfig != null && "false".equalsIgnoreCase(logConfig.getConfigValue())) {
            menus.remove(MenuPermission.LOG_MENU_PATH);
        }

        return menus;
    }
}
