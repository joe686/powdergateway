package com.powergateway.controller;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.powergateway.aop.SysLogRecord;
import com.powergateway.common.Result;
import com.powergateway.dao.SysUserMapper;
import com.powergateway.exception.BusinessException;
import com.powergateway.model.SysUser;
import com.powergateway.model.dto.FieldFormulaDto;
import com.powergateway.model.dto.FormulaSaveRequest;
import com.powergateway.model.dto.FormulaValidateRequest;
import com.powergateway.model.dto.FormulaValidateResult;
import com.powergateway.service.FieldFormulaService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

/**
 * 字段公式管理 REST 端点（UX-C FN-03）。
 * 6 个端点：list / getById / save / duplicate / delete / validate
 * 全部要求登录；delete 要求 admin 角色。
 */
@RestController
@RequestMapping("/api/field-formula")
@Tag(name = "字段公式管理", description = "常用字段公式 CRUD 与校验（UX-C FN-03）")
public class FieldFormulaController {

    @Autowired private FieldFormulaService service;
    @Autowired private SysUserMapper sysUserMapper;

    @GetMapping("/list")
    @Operation(summary = "分页查询公式列表")
    public Result<IPage<FieldFormulaDto>> list(
            @RequestParam(required = false) String scene,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "1") int pageNo,
            @RequestParam(defaultValue = "20") int pageSize) {
        StpUtil.checkLogin();
        return Result.success(service.list(scene, keyword, pageNo, pageSize));
    }

    @GetMapping("/{id}")
    @Operation(summary = "查询公式详情，软删返回 null")
    public Result<FieldFormulaDto> getById(@PathVariable Long id) {
        StpUtil.checkLogin();
        return Result.success(service.getById(id));
    }

    @SysLogRecord(module = "字段公式管理", action = "保存公式")
    @PostMapping("/save")
    @Operation(summary = "新增或更新公式（id 空=新增，非空=更新）")
    public Result<Long> save(@RequestBody FormulaSaveRequest req) {
        StpUtil.checkLogin();
        return Result.success(service.save(req, currentUsername()));
    }

    @SysLogRecord(module = "字段公式管理", action = "复制公式")
    @PostMapping("/{id}/duplicate")
    @Operation(summary = "复制公式为新记录")
    public Result<Long> duplicate(@PathVariable Long id) {
        StpUtil.checkLogin();
        return Result.success(service.duplicate(id, currentUsername()));
    }

    @SysLogRecord(module = "字段公式管理", action = "删除公式")
    @DeleteMapping("/{id}")
    @Operation(summary = "软删除公式（仅 admin）")
    public Result<Void> delete(@PathVariable Long id) {
        StpUtil.checkLogin();
        requireAdmin();
        service.delete(id);
        return Result.success();
    }

    @PostMapping("/validate")
    @Operation(summary = "独立校验端点（不保存）")
    public Result<FormulaValidateResult> validate(@RequestBody FormulaValidateRequest req) {
        StpUtil.checkLogin();
        return Result.success(service.validate(req));
    }

    // ─── 辅助 ─────────────────────────────────

    private String currentUsername() {
        SysUser u = sysUserMapper.selectById(StpUtil.getLoginIdAsLong());
        return u == null ? "unknown" : u.getUsername();
    }

    private void requireAdmin() {
        SysUser u = sysUserMapper.selectById(StpUtil.getLoginIdAsLong());
        if (u == null || !"admin".equals(u.getRole())) {
            throw new BusinessException(403, "仅管理员可执行此操作");
        }
    }
}
