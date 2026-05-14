package com.powergateway.controller;

import cn.dev33.satoken.stp.StpUtil;
import com.powergateway.common.Result;
import com.powergateway.dao.SysUserMapper;
import com.powergateway.exception.BusinessException;
import com.powergateway.model.SysConfig;
import com.powergateway.model.SysUser;
import com.powergateway.service.SysConfigService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Tag(name = "系统配置")
@RestController
@RequestMapping("/api/config")
public class SysConfigController {

    @Autowired private SysConfigService sysConfigService;
    @Autowired private SysUserMapper sysUserMapper;

    @Operation(summary = "获取全部系统配置")
    @GetMapping("/all")
    public Result<List<SysConfig>> getAll() {
        return Result.success(sysConfigService.getAll());
    }

    @Operation(summary = "批量更新系统配置（仅管理员）")
    @PutMapping
    public Result<Void> update(@RequestBody Map<String, String> updates) {
        long userId = StpUtil.getLoginIdAsLong();
        SysUser currentUser = sysUserMapper.selectById(userId);
        if (currentUser == null || !"admin".equals(currentUser.getRole())) {
            throw new BusinessException(403, "无权操作，需要管理员角色");
        }
        sysConfigService.batchUpdate(updates);
        return Result.success();
    }
}
