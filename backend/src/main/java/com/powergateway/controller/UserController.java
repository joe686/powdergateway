package com.powergateway.controller;

import com.powergateway.common.Result;
import com.powergateway.model.dto.UserSaveRequest;
import com.powergateway.model.dto.UserVO;
import com.powergateway.aop.SysLogRecord;
import com.powergateway.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 用户管理接口（SYS-3）
 */
@RestController
@RequestMapping("/api/user")
@Tag(name = "用户管理", description = "用户 CRUD（SYS-3）")
public class UserController {

    @Autowired
    private UserService userService;

    @GetMapping("/list")
    @Operation(summary = "用户列表（密码脱敏）")
    public Result<List<UserVO>> list(
            @RequestParam(required = false) String username,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        return Result.success(userService.list(username, page, size));
    }

    @SysLogRecord(module = "用户管理", action = "保存用户")
    @PostMapping("/save")
    @Operation(summary = "新增/更新用户（id 为空=新增，密码空=不改）")
    public Result<Long> save(@RequestBody UserSaveRequest req) {
        return Result.success(userService.save(req));
    }

    @SysLogRecord(module = "用户管理", action = "删除用户")
    @DeleteMapping("/{id}")
    @Operation(summary = "删除用户（不能删自己，不能删最后一个 admin）")
    public Result<Void> delete(@PathVariable Long id) {
        userService.delete(id);
        return Result.success();
    }
}
