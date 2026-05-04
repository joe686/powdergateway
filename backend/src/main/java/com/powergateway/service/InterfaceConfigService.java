package com.powergateway.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.powergateway.dao.DbConnectionMapper;
import com.powergateway.dao.InterfaceConfigMapper;
import com.powergateway.exception.BusinessException;
import com.powergateway.model.DbConnection;
import com.powergateway.model.InterfaceConfig;
import com.powergateway.model.dto.InsertConfigJson;
import com.powergateway.model.dto.InsertConfigJson.FieldInsertConfig;
import com.powergateway.model.dto.InsertConfigJson.TableInsertConfig;
import com.powergateway.model.dto.InterfaceSaveRequest;
import com.powergateway.model.dto.InterfacePreviewRequest;
import com.powergateway.model.dto.QueryConfigJson;
import com.powergateway.utils.AesUtil;
import com.powergateway.utils.ColumnValidator;
import com.powergateway.utils.DataSourceResolver;
import com.powergateway.utils.InsertBuilder;
import com.powergateway.utils.QueryBuilder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.sql.*;
import java.util.*;

/**
 * 接口配置业务层（M2-3 查询接口配置，M2-4 插入接口配置）。
 * M2-5/6 将在此类基础上扩展，不改变已有方法签名。
 */
@Slf4j
@Service
public class InterfaceConfigService {

    @Autowired
    private InterfaceConfigMapper interfaceConfigMapper;

    @Autowired
    private DbConnectionMapper dbConnectionMapper;

    @Autowired
    private AesUtil aesUtil;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private DataSourceResolver dataSourceResolver;

    @Autowired
    private ColumnValidator columnValidator;

    // ─── 保存 ──────────────────────────────────────────────────────────────────

    /**
     * 保存接口配置（新建或更新）。
     *
     * @return 接口 id
     */
    public Long save(InterfaceSaveRequest req) {
        if (req.getName() == null || req.getName().trim().isEmpty()) {
            throw new BusinessException(400, "接口名称不能为空");
        }
        if (req.getDbConnectionId() == null) {
            throw new BusinessException(400, "数据库连接不能为空");
        }
        if (req.getConfigJson() == null || req.getConfigJson().trim().isEmpty()) {
            throw new BusinessException(400, "配置内容不能为空");
        }

        InterfaceConfig entity = new InterfaceConfig();
        entity.setName(req.getName());
        entity.setDbConnectionId(req.getDbConnectionId());
        entity.setType(req.getType() != null ? req.getType() : "SELECT");
        entity.setConfigJson(req.getConfigJson());
        entity.setStatus("draft");
        entity.setLogEnabled(1);

        if (req.getId() != null) {
            entity.setId(req.getId());
            interfaceConfigMapper.updateById(entity);
            return req.getId();
        } else {
            interfaceConfigMapper.insert(entity);
            return entity.getId();
        }
    }

    // ─── 预览 ──────────────────────────────────────────────────────────────────

    /**
     * 预览查询接口：根据 config_json 构建 SQL，连接目标库执行，返回前10条结果。
     */
    public List<Map<String, Object>> preview(Long id, Map<String, Object> params) {
        InterfaceConfig config = interfaceConfigMapper.selectById(id);
        if (config == null) {
            throw new BusinessException(404, "接口配置不存在");
        }

        // 解析 config_json → QueryConfigJson
        QueryConfigJson queryConfig;
        try {
            queryConfig = objectMapper.readValue(config.getConfigJson(), QueryConfigJson.class);
        } catch (Exception e) {
            throw new BusinessException(400, "配置 JSON 解析失败: " + e.getMessage());
        }

        // 构建 SQL
        QueryBuilder.SqlResult sqlResult;
        try {
            sqlResult = QueryBuilder.build(queryConfig, params);
        } catch (Exception e) {
            throw new BusinessException(400, "SQL 构建失败: " + e.getMessage());
        }

        log.info("[M2-3] 预览 SQL: {}", sqlResult.sql);

        // 获取目标数据库连接信息
        DbConnection conn = dbConnectionMapper.selectById(config.getDbConnectionId());
        if (conn == null) {
            throw new BusinessException(404, "数据库连接不存在");
        }

        return executeQuery(conn, sqlResult);
    }

    // ─── 基础 CRUD ─────────────────────────────────────────────────────────────

    /** 查询接口配置列表 */
    public List<InterfaceConfig> list(String name, int page, int size) {
        LambdaQueryWrapper<InterfaceConfig> wrapper = new LambdaQueryWrapper<>();
        if (name != null && !name.trim().isEmpty()) {
            wrapper.like(InterfaceConfig::getName, name);
        }
        wrapper.orderByDesc(InterfaceConfig::getCreateTime);
        return interfaceConfigMapper.selectList(wrapper);
    }

    /** 查询接口配置详情 */
    public InterfaceConfig getById(Long id) {
        InterfaceConfig config = interfaceConfigMapper.selectById(id);
        if (config == null) {
            throw new BusinessException(404, "接口配置不存在");
        }
        return config;
    }

    /** 删除接口配置（MyBatis-Plus 软删除） */
    public void delete(Long id) {
        if (interfaceConfigMapper.selectById(id) == null) {
            throw new BusinessException(404, "接口配置不存在");
        }
        interfaceConfigMapper.deleteById(id);
    }

    // ─── M2-4 INSERT 执行 ──────────────────────────────────────────────────────

    /**
     * 执行 INSERT 接口：多表事务，任意表失败则全部回滚。
     *
     * @param id     接口配置 id
     * @param params 请求参数（REQUEST 类型字段从此 Map 取值）
     * @return 总影响行数
     */
    public int executeInsert(Long id, Map<String, Object> params) {
        InterfaceConfig config = interfaceConfigMapper.selectById(id);
        if (config == null) throw new BusinessException(404, "接口配置不存在");
        if (!"INSERT".equals(config.getType())) throw new BusinessException(400, "非 INSERT 类型接口");

        InsertConfigJson insertConfig;
        try {
            insertConfig = objectMapper.readValue(config.getConfigJson(), InsertConfigJson.class);
        } catch (Exception e) {
            throw new BusinessException(400, "配置 JSON 解析失败: " + e.getMessage());
        }

        List<TableInsertConfig> tables = insertConfig.getTables();
        if (tables == null || tables.isEmpty()) throw new BusinessException(400, "未配置插入表");
        if (tables.size() > 3) throw new BusinessException(400, "最多支持3张表");

        DbConnection conn = dbConnectionMapper.selectById(config.getDbConnectionId());
        if (conn == null) throw new BusinessException(404, "数据库连接不存在");

        // 解析字段值 + 校验
        List<InsertBuilder.SqlResult> sqlResults = new ArrayList<>();
        for (TableInsertConfig tableConfig : tables) {
            Map<String, Object> fieldValues = new LinkedHashMap<>();
            for (FieldInsertConfig field : tableConfig.getFields()) {
                Object value = dataSourceResolver.resolve(field, params);
                fieldValues.put(field.getColumn(), value);
            }
            columnValidator.validate(tableConfig.getTableName(), fieldValues, config.getDbConnectionId());
            sqlResults.add(InsertBuilder.build(tableConfig.getTableName(), fieldValues));
        }

        log.info("[M2-4] 执行 INSERT，接口 id={}，共{}张表", id, sqlResults.size());

        // 多表事务执行（JDBC 手动事务）
        String password = aesUtil.decrypt(conn.getPassword());
        int totalAffected = 0;
        try (Connection jdbc = DriverManager.getConnection(conn.getUrl(), conn.getUsername(), password)) {
            jdbc.setAutoCommit(false);
            try {
                for (InsertBuilder.SqlResult sql : sqlResults) {
                    log.info("[M2-4] SQL: {}", sql.sql);
                    try (PreparedStatement ps = jdbc.prepareStatement(sql.sql)) {
                        for (int i = 0; i < sql.params.size(); i++) {
                            ps.setObject(i + 1, sql.params.get(i));
                        }
                        totalAffected += ps.executeUpdate();
                    }
                }
                jdbc.commit();
            } catch (Exception e) {
                jdbc.rollback();
                throw new BusinessException(500, "插入执行失败，已回滚: " + e.getMessage());
            }
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException(500, "数据库连接失败: " + e.getMessage());
        }

        return totalAffected;
    }

    // ─── 私有方法 ──────────────────────────────────────────────────────────────

    /** 连接目标库执行查询，返回结果集（行为 Map<列标签, 值>） */
    private List<Map<String, Object>> executeQuery(DbConnection conn, QueryBuilder.SqlResult sqlResult) {
        String password = aesUtil.decrypt(conn.getPassword());
        List<Map<String, Object>> results = new ArrayList<>();

        try (Connection jdbc = DriverManager.getConnection(conn.getUrl(), conn.getUsername(), password);
             PreparedStatement ps = jdbc.prepareStatement(sqlResult.sql)) {

            for (int i = 0; i < sqlResult.params.size(); i++) {
                ps.setObject(i + 1, sqlResult.params.get(i));
            }

            try (ResultSet rs = ps.executeQuery()) {
                ResultSetMetaData meta = rs.getMetaData();
                int colCount = meta.getColumnCount();
                while (rs.next()) {
                    Map<String, Object> row = new LinkedHashMap<>();
                    for (int i = 1; i <= colCount; i++) {
                        row.put(meta.getColumnLabel(i), rs.getObject(i));
                    }
                    results.add(row);
                }
            }
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException(500, "查询执行失败: " + e.getMessage());
        }

        return results;
    }
}
