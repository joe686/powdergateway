package com.powergateway.model.dto;

import com.powergateway.model.DbConnection;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 数据库连接返回视图对象，password 始终脱敏为 "***"
 */
@Data
public class DbConnectionVO {

    private Long id;
    private String name;
    private String dbType;
    private String url;
    private String username;
    private String password;
    private String env;
    private Integer poolSize;
    private Integer timeout;
    private LocalDateTime createTime;

    public static DbConnectionVO from(DbConnection conn) {
        DbConnectionVO vo = new DbConnectionVO();
        vo.setId(conn.getId());
        vo.setName(conn.getName());
        vo.setDbType(conn.getDbType());
        vo.setUrl(conn.getUrl());
        vo.setUsername(conn.getUsername());
        vo.setPassword("***");
        vo.setEnv(conn.getEnv());
        vo.setPoolSize(conn.getPoolSize());
        vo.setTimeout(conn.getTimeout());
        vo.setCreateTime(conn.getCreateTime());
        return vo;
    }
}
