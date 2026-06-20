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
 * 应用启动后初始化基础数据：预置管理员账号 admin/Admin@123
 */
@Component
public class DataInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DataInitializer.class);

    @Autowired
    private SysUserMapper sysUserMapper;

    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    @Override
    public void run(ApplicationArguments args) {
        Long count = sysUserMapper.selectCount(
                new QueryWrapper<SysUser>().eq("username", "admin")
        );
        if (count == 0) {
            SysUser admin = new SysUser();
            admin.setUsername("admin");
            admin.setPassword(passwordEncoder.encode("Admin@123"));
            admin.setRole("admin");
            admin.setStatus(1);
            // BUG-012 修复：显式设置 createTime/updateTime，避免 MyBatis-Plus 插入时传 null 覆盖数据库默认值
            LocalDateTime now = LocalDateTime.now();
            admin.setCreateTime(now);
            admin.setUpdateTime(now);
            sysUserMapper.insert(admin);
            log.info("已预置管理员账号：admin / Admin@123");
        }
    }
}
