package com.powergateway.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.powergateway.common.Result;
import com.powergateway.config.SysAppInfoInitializer;
import com.powergateway.dao.SysAppInfoMapper;
import com.powergateway.model.SysAppInfo;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.text.SimpleDateFormat;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * 程序版本信息 API(v0.3.1 CR-003 · Task 2 · 免登)。
 *
 * <p>用户明确要求返:版本号 + 作者(光斓) + 中文日期(当前) + "当前仅为测试版本"注 + Build SHA。</p>
 */
@RestController
@RequestMapping("/api/app-info")
@Tag(name = "程序版本信息", description = "v0.3.1 CR-003 · 用于前端 sidebar/LoginView 版本卡片")
public class AppInfoController {

    private static final DateTimeFormatter BUILD_TIME_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final SimpleDateFormat CN_DATE = new SimpleDateFormat("yyyy年M月d日", Locale.CHINA);

    @Autowired private SysAppInfoMapper sysAppInfoMapper;

    @GetMapping
    @Operation(summary = "获取当前程序版本信息")
    public Result<Map<String, Object>> getAppInfo() {
        Map<String, Object> data = new LinkedHashMap<>();
        SysAppInfo info = readLatest();
        if (info != null) {
            data.put("version", info.getVersion());
            data.put("buildTime", info.getBuildTime() != null ? info.getBuildTime().format(BUILD_TIME_FMT) : null);
            data.put("gitSha", info.getGitSha());
            data.put("author", info.getAuthor());
            data.put("releaseNote", info.getReleaseNote());
        } else {
            // fallback:表未初始化(启动早期)· 走 SysAppInfoInitializer 常量
            data.put("version", SysAppInfoInitializer.CURRENT_VERSION);
            data.put("buildTime", LocalDateTime.now().format(BUILD_TIME_FMT));
            data.put("gitSha", null);
            data.put("author", SysAppInfoInitializer.CURRENT_AUTHOR);
            data.put("releaseNote", SysAppInfoInitializer.CURRENT_RELEASE_NOTE);
        }
        data.put("currentDate", CN_DATE.format(new Date()));
        return Result.success(data);
    }

    private SysAppInfo readLatest() {
        try {
            LambdaQueryWrapper<SysAppInfo> q = new LambdaQueryWrapper<>();
            q.orderByDesc(SysAppInfo::getId).last("LIMIT 1");
            return sysAppInfoMapper.selectOne(q);
        } catch (Exception e) {
            return null;
        }
    }
}
