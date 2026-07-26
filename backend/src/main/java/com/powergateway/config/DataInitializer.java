package com.powergateway.config;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.powergateway.dao.SysUserMapper;
import com.powergateway.model.SysUser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * 应用启动后初始化基础数据：预置 3 个基础账号
 *   admin      / Admin@123 (role=admin)     — 管理员，全菜单权限
 *   testuser1  / Test@123  (role=user)      — 手工测试用普通账号，验证菜单收窄
 *   testreader / Test@123  (role=readonly)  — 手工测试用只读账号，验证删除拦截
 *
 * 每个账号独立 INSERT-if-not-exists，已存在则不覆盖，避免破坏用户改过的密码。
 */
@Component
public class DataInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DataInitializer.class);

    @Autowired
    private SysUserMapper sysUserMapper;

    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    @Override
    public void run(ApplicationArguments args) {
        ensureUser("admin",      "Admin@123", "admin");
        ensureUser("testuser1",  "Test@123",  "user");
        ensureUser("testreader", "Test@123",  "readonly");
    }

    private void ensureUser(String username, String plainPassword, String role) {
        Long count = sysUserMapper.selectCount(
                new QueryWrapper<SysUser>().eq("username", username)
        );
        if (count > 0) {
            return;
        }
        SysUser user = new SysUser();
        user.setUsername(username);
        user.setPassword(passwordEncoder.encode(plainPassword));
        user.setRole(role);
        user.setStatus(1);
        // BUG-012 修复：显式设置 createTime/updateTime，避免 MyBatis-Plus 插入时传 null 覆盖数据库默认值
        LocalDateTime now = LocalDateTime.now();
        user.setCreateTime(now);
        user.setUpdateTime(now);
        sysUserMapper.insert(user);
        log.info("已预置账号：{} / {} (role={})", username, plainPassword, role);
    }
}
