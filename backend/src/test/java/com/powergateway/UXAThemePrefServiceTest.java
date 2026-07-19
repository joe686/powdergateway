package com.powergateway;

import com.powergateway.service.SysUserService;
import com.powergateway.model.SysUser;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class UXAThemePrefServiceTest {

    @Autowired
    private SysUserService sysUserService;

    @Test
    void getThemePref_未设置_返回null() {
        SysUser u = new SysUser();
        u.setUsername("t1");
        u.setPassword("x");
        sysUserService.save(u);
        assertThat(sysUserService.getThemePref(u.getId())).isNull();
    }

    @Test
    void setThemePref_合法JSON_持久化并可读回() {
        SysUser u = new SysUser();
        u.setUsername("t2");
        u.setPassword("x");
        sysUserService.save(u);
        String json = "{\"mode\":\"system\",\"theme\":\"dark\"}";
        sysUserService.setThemePref(u.getId(), json);
        assertThat(sysUserService.getThemePref(u.getId())).isEqualTo(json);
    }
}
