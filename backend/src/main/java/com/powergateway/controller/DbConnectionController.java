package com.powergateway.controller;

import com.powergateway.common.Result;
import com.powergateway.model.dto.DbConnectionSaveRequest;
import com.powergateway.model.dto.DbConnectionVO;
import com.powergateway.model.dto.TestConnectionResult;
import com.powergateway.aop.SysLogRecord;
import com.powergateway.service.DbConnectionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import java.util.List;

@Tag(name = "数据库连接管理", description = "M2-1 数据库连接 CRUD 与连通测试")
@RestController
@RequestMapping("/api/db")
public class DbConnectionController {

    @Autowired
    private DbConnectionService dbConnectionService;

    @Operation(summary = "查询连接列表")
    @GetMapping("/list")
    public Result<List<DbConnectionVO>> list() {
        return Result.success(dbConnectionService.list());
    }

    @Operation(summary = "新建或更新连接")
    @SysLogRecord(module = "数据库连接", action = "保存连接")
    @PostMapping("/save")
    public Result<Long> save(@Valid @RequestBody DbConnectionSaveRequest req) {
        return Result.success(dbConnectionService.save(req));
    }

    @Operation(summary = "删除连接")
    @SysLogRecord(module = "数据库连接", action = "删除连接")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        dbConnectionService.delete(id);
        return Result.success();
    }

    @Operation(summary = "测试连接连通性")
    @PostMapping("/{id}/test")
    public Result<TestConnectionResult> test(@PathVariable Long id) {
        return Result.success(dbConnectionService.testConnection(id));
    }

    @GetMapping("/list/export")
    @Operation(summary = "FN-10 导出数据源列表 Excel（密码列脱敏）")
    public ResponseEntity<byte[]> exportList(@RequestParam(required = false) String keyword) throws Exception {
        byte[] data = dbConnectionService.exportList(keyword);
        return InterfaceConfigController.excelResponse(data, "数据源列表_" + InterfaceConfigController.tsSuffix() + ".xlsx");
    }
}
