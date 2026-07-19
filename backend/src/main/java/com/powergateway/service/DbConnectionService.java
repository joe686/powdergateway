package com.powergateway.service;

import com.baomidou.dynamic.datasource.DynamicRoutingDataSource;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.powergateway.dao.DbConnectionMapper;
import com.powergateway.exception.BusinessException;
import com.powergateway.model.DbConnection;
import com.powergateway.model.dto.DbConnectionSaveRequest;
import com.powergateway.model.dto.DbConnectionVO;
import com.powergateway.model.dto.TestConnectionResult;
import com.powergateway.utils.AesUtil;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DriverManager;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
public class DbConnectionService {

    @Autowired
    private DbConnectionMapper dbConnectionMapper;

    @Autowired
    private AesUtil aesUtil;

    @Autowired
    private DataSource dataSource;

    public List<DbConnectionVO> list() {
        return dbConnectionMapper.selectList(
                new LambdaQueryWrapper<DbConnection>().orderByDesc(DbConnection::getId)
        ).stream().map(DbConnectionVO::from).collect(Collectors.toList());
    }

    /** FN-10 导出数据源列表 Excel（密码固定输出 ***，不导出明文） */
    public byte[] exportList(String keyword) {
        LambdaQueryWrapper<DbConnection> wrapper = new LambdaQueryWrapper<>();
        if (keyword != null && !keyword.trim().isEmpty()) wrapper.like(DbConnection::getName, keyword.trim());
        wrapper.orderByDesc(DbConnection::getId);
        List<DbConnection> rows = dbConnectionMapper.selectList(wrapper);
        return com.powergateway.utils.ExcelExportUtil.export("数据源列表", java.util.Arrays.asList(
            new com.powergateway.utils.ExcelExportUtil.Column<>("名称",  DbConnection::getName),
            new com.powergateway.utils.ExcelExportUtil.Column<>("类型",  DbConnection::getDbType),
            new com.powergateway.utils.ExcelExportUtil.Column<>("URL",   DbConnection::getUrl),
            new com.powergateway.utils.ExcelExportUtil.Column<>("账号",  DbConnection::getUsername),
            new com.powergateway.utils.ExcelExportUtil.Column<>("密码",  r -> "***"),
            new com.powergateway.utils.ExcelExportUtil.Column<>("环境",  DbConnection::getEnv)
        ), rows);
    }

    public Long save(DbConnectionSaveRequest req) {
        DbConnection conn;
        if (req.getId() != null) {
            conn = dbConnectionMapper.selectById(req.getId());
            if (conn == null) throw new BusinessException(404, "连接不存在");
        } else {
            conn = new DbConnection();
        }

        conn.setName(req.getName());
        conn.setDbType(req.getDbType());
        conn.setUrl(req.getUrl());
        conn.setUsername(req.getUsername());
        conn.setEnv(req.getEnv());
        conn.setPoolSize(req.getPoolSize() != null ? req.getPoolSize() : 5);
        conn.setTimeout(req.getTimeout() != null ? req.getTimeout() : 3000);

        // 密码处理：传 "***" 表示不修改；新建时必须提供
        if (!"***".equals(req.getPassword())) {
            if (req.getPassword() == null || req.getPassword().isEmpty()) {
                if (req.getId() == null) {
                    throw new BusinessException(400, "新建连接时密码不能为空");
                }
                // 更新且未传密码：保留原值，不修改
            } else {
                conn.setPassword(aesUtil.encrypt(req.getPassword()));
            }
        }

        if (req.getId() == null) {
            dbConnectionMapper.insert(conn);
        } else {
            dbConnectionMapper.updateById(conn);
        }

        registerDataSource(conn);
        return conn.getId();
    }

    public void delete(Long id) {
        DbConnection conn = dbConnectionMapper.selectById(id);
        if (conn == null) throw new BusinessException(404, "连接不存在");
        dbConnectionMapper.deleteById(id);
        removeDataSource(id);
    }

    public TestConnectionResult testConnection(Long id) {
        DbConnection conn = dbConnectionMapper.selectById(id);
        if (conn == null) throw new BusinessException(404, "连接不存在");
        String password = aesUtil.decrypt(conn.getPassword());
        long start = System.currentTimeMillis();
        DriverManager.setLoginTimeout(3);
        try (Connection c = DriverManager.getConnection(conn.getUrl(), conn.getUsername(), password)) {
            long elapsed = System.currentTimeMillis() - start;
            return new TestConnectionResult(true, "连接成功，耗时 " + elapsed + "ms");
        } catch (Exception e) {
            return new TestConnectionResult(false, e.getMessage());
        }
    }

    /**
     * 向 DynamicRoutingDataSource 注册数据源；已存在时先移除再注册（更新场景）。
     * 供 save() 和 DbConnectionInitializer 复用。
     */
    public void registerDataSource(DbConnection conn) {
        DynamicRoutingDataSource dds = (DynamicRoutingDataSource) dataSource;
        String key = "db_" + conn.getId();
        if (dds.getDataSources().containsKey(key)) {
            dds.removeDataSource(key);
        }
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(conn.getUrl());
        config.setUsername(conn.getUsername());
        config.setPassword(aesUtil.decrypt(conn.getPassword()));
        config.setDriverClassName(resolveDriverClass(conn.getDbType()));
        config.setMaximumPoolSize(conn.getPoolSize() != null ? conn.getPoolSize() : 5);
        config.setConnectionTimeout(conn.getTimeout() != null ? conn.getTimeout() : 3000L);
        config.setInitializationFailTimeout(-1); // 目标库不可达时不抛异常
        config.setMinimumIdle(0);                // 启动时不预先建连接
        HikariDataSource ds = new HikariDataSource(config);
        dds.addDataSource(key, ds);
        log.debug("[M2-1] 注册数据源: {} ({})", key, conn.getName());
    }

    private void removeDataSource(Long id) {
        DynamicRoutingDataSource dds = (DynamicRoutingDataSource) dataSource;
        String key = "db_" + id;
        if (dds.getDataSources().containsKey(key)) {
            dds.removeDataSource(key);
        }
    }

    private String resolveDriverClass(String dbType) {
        switch (dbType) {
            case "MySQL":      return "com.mysql.cj.jdbc.Driver";
            case "Oracle":     return "oracle.jdbc.OracleDriver";
            case "PostgreSQL": return "org.postgresql.Driver";
            default: throw new BusinessException(400, "不支持的数据库类型: " + dbType);
        }
    }
}
