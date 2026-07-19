package com.powergateway.service;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.powergateway.dao.SysUserMapper;
import com.powergateway.model.SysUser;
import org.springframework.stereotype.Service;

/**
 * 用户表 Service（UX-A 主题偏好 + 其他扩展）
 */
@Service
public class SysUserService extends ServiceImpl<SysUserMapper, SysUser> {

    /**
     * 获取用户主题偏好
     *
     * @param userId 用户ID
     * @return 主题偏好 JSON 字符串，或 null 如果未设置
     */
    public String getThemePref(Long userId) {
        SysUser u = getById(userId);
        return u == null ? null : u.getThemePref();
    }

    /**
     * 设置用户主题偏好
     *
     * @param userId 用户ID
     * @param pref   主题偏好 JSON 字符串
     */
    public void setThemePref(Long userId, String pref) {
        SysUser u = new SysUser();
        u.setId(userId);
        u.setThemePref(pref);
        updateById(u);
    }
}
