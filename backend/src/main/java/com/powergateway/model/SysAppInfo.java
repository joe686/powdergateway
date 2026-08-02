package com.powergateway.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 程序版本信息表(v0.3.1 CR-003 · 存"防篡改" · Task 2)。
 */
@Data
@TableName("sys_app_info")
public class SysAppInfo {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 语义化版本 · 如 v0.3.1 */
    private String version;

    /** 构建时间 */
    private LocalDateTime buildTime;

    /** git commit id 短哈希 */
    private String gitSha;

    /** 作者(默认"光斓")*/
    private String author;

    /** 发布注 · 如 "当前仅为测试版本" */
    private String releaseNote;

    private LocalDateTime createdAt;
}
