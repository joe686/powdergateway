package com.powergateway.config;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.powergateway.dao.SysAppInfoMapper;
import com.powergateway.model.SysAppInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.time.LocalDateTime;

/**
 * 程序版本信息启动 upsert(v0.3.1 CR-003 · Task 2)。
 *
 * <p>启动时若 sys_app_info 无对应 {@link #CURRENT_VERSION} 记录 · 自动插入一条。
 * 已存在则跳过(保留"防篡改"语义)。</p>
 */
@Component
public class SysAppInfoInitializer {

    private static final Logger log = LoggerFactory.getLogger(SysAppInfoInitializer.class);

    /** 当前版本(每次发版更新此常量 · v0.3.1 起用 · v0.3.10 CHG-052 顺延同步) */
    public static final String CURRENT_VERSION = "v0.3.10";
    public static final String CURRENT_AUTHOR = "光斓";
    public static final String CURRENT_RELEASE_NOTE = "当前仅为测试版本";

    @Autowired private SysAppInfoMapper sysAppInfoMapper;

    @PostConstruct
    public void init() {
        try {
            LambdaQueryWrapper<SysAppInfo> q = new LambdaQueryWrapper<>();
            q.eq(SysAppInfo::getVersion, CURRENT_VERSION);
            if (sysAppInfoMapper.selectCount(q) > 0) {
                log.info("CR-003 · sys_app_info 已有 {} 记录 · 跳过初始化", CURRENT_VERSION);
                return;
            }
            SysAppInfo info = new SysAppInfo();
            info.setVersion(CURRENT_VERSION);
            info.setBuildTime(LocalDateTime.now());
            info.setGitSha(readGitSha());
            info.setAuthor(CURRENT_AUTHOR);
            info.setReleaseNote(CURRENT_RELEASE_NOTE);
            sysAppInfoMapper.insert(info);
            log.info("CR-003 · sys_app_info 插入 {} · author={} · releaseNote={}",
                    CURRENT_VERSION, CURRENT_AUTHOR, CURRENT_RELEASE_NOTE);
        } catch (Exception e) {
            log.warn("CR-003 · sys_app_info 初始化失败(可能是启动早期表未就绪 · 不影响主流程):{}", e.getMessage());
        }
    }

    /** 从系统属性/环境变量读 git sha(打包时可通过 -Dgit.sha=xxx 或 GIT_SHA 环境变量注入)*/
    private String readGitSha() {
        String s = System.getProperty("git.sha");
        if (s != null && !s.isEmpty()) return s;
        s = System.getenv("GIT_SHA");
        return s;
    }
}
