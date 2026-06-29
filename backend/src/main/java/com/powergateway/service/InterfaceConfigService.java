package com.powergateway.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.powergateway.aop.AuditContext;
import com.powergateway.aop.AuditContextHolder;
import com.powergateway.aop.AuditLog;
import com.powergateway.dao.DbConnectionMapper;
import com.powergateway.dao.InterfaceConfigMapper;
import com.powergateway.exception.BusinessException;
import com.powergateway.model.DbConnection;
import com.powergateway.model.InterfaceConfig;
import com.powergateway.model.dto.ColumnMeta;
import com.powergateway.model.dto.InsertConfigJson;
import com.powergateway.model.dto.InsertConfigJson.FieldInsertConfig;
import com.powergateway.model.dto.InsertConfigJson.TableInsertConfig;
import com.powergateway.model.dto.InterfaceSaveRequest;
import com.powergateway.model.dto.InterfacePreviewRequest;
import com.powergateway.model.dto.QueryConfigJson;
import com.powergateway.model.dto.TableMeta;
import com.powergateway.model.dto.DeleteConfigJson;
import com.powergateway.model.dto.ShardRouteResult;
import com.powergateway.model.dto.UpdateConfigJson;
import com.powergateway.model.dto.UpdateConfigJson.ConditionConfig;
import com.powergateway.model.dto.UpdateConfigJson.TableUpdateConfig;
import com.powergateway.utils.AesUtil;
import com.powergateway.utils.DeleteBuilder;
import com.powergateway.utils.ColumnValidator;
import com.powergateway.utils.DataSourceResolver;
import com.powergateway.utils.InsertBuilder;
import com.powergateway.utils.QueryBuilder;
import com.powergateway.utils.UpdateBuilder;
import com.powergateway.service.QueryCacheManager;
import com.powergateway.service.ShardConfigService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.sql.*;
import java.util.*;
import java.util.stream.Collectors;

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

    @Autowired
    private TableMetaService tableMetaService;

    @Autowired
    private QueryCacheManager cacheManager;

    @Autowired
    private ShardConfigService shardConfigService;

    @Autowired
    private InterfaceOpTypeCache interfaceOpTypeCache;

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
        if (req.getCacheEnabled() != null)    entity.setCacheEnabled(req.getCacheEnabled());
        if (req.getCacheTtlSeconds() != null) entity.setCacheTtlSeconds(req.getCacheTtlSeconds());
        if (req.getCacheKeyTemplate() != null) entity.setCacheKeyTemplate(req.getCacheKeyTemplate());
        if (req.getShardConfigId() != null) entity.setShardConfigId(req.getShardConfigId());

        if (req.getId() == null) {
            entity.setAllowBatchDelete(req.getAllowBatchDelete() != null ? req.getAllowBatchDelete() : 0);
        } else if (req.getAllowBatchDelete() != null) {
            entity.setAllowBatchDelete(req.getAllowBatchDelete());
        }

        // UPDATE 类型：校验条件字段必须含主键或唯一索引
        if ("UPDATE".equals(entity.getType())) {
            try {
                UpdateConfigJson updateConfig = objectMapper.readValue(
                        req.getConfigJson(), UpdateConfigJson.class);
                validateUpdateConditions(updateConfig, req.getDbConnectionId());
            } catch (BusinessException e) {
                throw e;
            } catch (Exception e) {
                throw new BusinessException(400, "UPDATE 配置 JSON 解析失败: " + e.getMessage());
            }
        }

        if (req.getId() != null) {
            entity.setId(req.getId());
            interfaceConfigMapper.updateById(entity);
            cacheManager.evict(req.getId());
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

    // ─── 分片路由解析 ─────────────────────────────────────────────────────────────

    private ShardRouteResult resolveSharding(InterfaceConfig config, Map<String, Object> params) {
        if (config.getShardConfigId() == null) return null;
        if (Integer.valueOf(1).equals(config.getCacheEnabled())) {
            log.warn("[M2-8] 接口 id={} 同时配置了缓存和分片路由，分片路由已跳过（缓存优先）", config.getId());
            return null;
        }
        return shardConfigService.preview(config.getShardConfigId(), params);
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

    /** 查询所有 SELECT 类型接口（M2-10 缓存管理列表用） */
    public List<InterfaceConfig> listSelectInterfaces() {
        LambdaQueryWrapper<InterfaceConfig> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(InterfaceConfig::getType, "SELECT");
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

    // ─── M2-7 状态管理 ────────────────────────────────────────────────────────────

    /** 发布接口：status → published，path 写入 /api/exec/{id} */
    public void publish(Long id) {
        InterfaceConfig config = interfaceConfigMapper.selectById(id);
        if (config == null) throw new BusinessException(404, "接口配置不存在");
        InterfaceConfig update = new InterfaceConfig();
        update.setId(id);
        update.setStatus("published");
        update.setPath("/api/exec/" + id);
        interfaceConfigMapper.updateById(update);
        // BUG-009 修复：发布时预加载 opType 到缓存，避免首次调用时主线程 DB 查询
        interfaceOpTypeCache.preload(id);
        log.info("[M2-7] 接口 id={} 发布成功，path={}", id, update.getPath());
    }

    /** 禁用接口：status → disabled（draft/published 均可） */
    public void disable(Long id) {
        InterfaceConfig config = interfaceConfigMapper.selectById(id);
        if (config == null) throw new BusinessException(404, "接口配置不存在");
        InterfaceConfig update = new InterfaceConfig();
        update.setId(id);
        update.setStatus("disabled");
        interfaceConfigMapper.updateById(update);
        // BUG-009 修复：禁用时清除 opType 缓存
        interfaceOpTypeCache.evict(id);
        log.info("[M2-7] 接口 id={} 已禁用", id);
    }

    /** 删除接口配置（已发布状态不允许删除，需先禁用） */
    public void delete(Long id) {
        InterfaceConfig config = interfaceConfigMapper.selectById(id);
        if (config == null) throw new BusinessException(404, "接口配置不存在");
        if ("published".equals(config.getStatus())) {
            throw new BusinessException(400, "接口已发布，请先禁用后再删除");
        }
        interfaceConfigMapper.deleteById(id);
        // BUG-009 修复：删除时清除 opType 缓存
        interfaceOpTypeCache.evict(id);
    }

    /** 单独绑定/解绑分库分表配置（M2-8），shardConfigId=null 表示解绑 */
    public void bindShardConfig(Long id, Long shardConfigId) {
        if (interfaceConfigMapper.selectById(id) == null) {
            throw new BusinessException(404, "接口配置不存在");
        }
        interfaceConfigMapper.update(null,
                new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<InterfaceConfig>()
                        .eq(InterfaceConfig::getId, id)
                        .set(InterfaceConfig::getShardConfigId, shardConfigId));
    }

    // ─── M2-7 SELECT 执行（全量/分页）──────────────────────────────────────────────

    /**
     * 执行已发布 SELECT 接口。
     * page/pageSize 均不为 null 时分页（page 从 1 开始），否则全量返回。
     * M2-10：全量查询且 cache_enabled=1 时走双层缓存。
     */
    public List<Map<String, Object>> executeQuery(Long id, Map<String, Object> params,
                                                   Integer page, Integer pageSize) {
        InterfaceConfig config = interfaceConfigMapper.selectById(id);
        if (config == null) throw new BusinessException(404, "接口配置不存在");
        if (!"SELECT".equals(config.getType())) throw new BusinessException(400, "非 SELECT 类型接口");
        if (!"published".equals(config.getStatus())) {
            throw new BusinessException(403, "接口未发布或已禁用，无法执行");
        }

        if (Integer.valueOf(1).equals(config.getCacheEnabled()) && page == null) {
            return cacheManager.executeWithCache(id, config, params,
                    () -> doExecuteQuery(config, params, null, null));
        }
        return doExecuteQuery(config, params, page, pageSize);
    }

    private List<Map<String, Object>> doExecuteQuery(InterfaceConfig config,
                                                      Map<String, Object> params,
                                                      Integer page, Integer pageSize) {
        QueryConfigJson queryConfig;
        try {
            queryConfig = objectMapper.readValue(config.getConfigJson(), QueryConfigJson.class);
        } catch (Exception e) {
            throw new BusinessException(400, "配置 JSON 解析失败: " + e.getMessage());
        }

        ShardRouteResult shard = resolveSharding(config, params);
        if (shard != null && queryConfig.getTables() != null && !queryConfig.getTables().isEmpty()) {
            queryConfig.getTables().get(0).setName(shard.getTableName());
        }
        Long queryDbConnId = shard != null ? shard.getDbConnectionId() : config.getDbConnectionId();

        QueryBuilder.SqlResult sqlResult;
        try {
            if (page != null && pageSize != null) {
                sqlResult = QueryBuilder.buildPaginated(queryConfig, params, page, pageSize);
            } else {
                sqlResult = QueryBuilder.buildFull(queryConfig, params);
            }
        } catch (Exception e) {
            throw new BusinessException(400, "SQL 构建失败: " + e.getMessage());
        }

        log.info("[M2-7] 执行 SELECT SQL: {}", sqlResult.sql);

        DbConnection conn = dbConnectionMapper.selectById(queryDbConnId);
        if (conn == null) throw new BusinessException(404, "数据库连接不存在");
        return executeQuery(conn, sqlResult);
    }

    // ─── M2-4 INSERT 执行 ──────────────────────────────────────────────────────

    /**
     * 执行 INSERT 接口：多表事务，任意表失败则全部回滚。
     *
     * @param id     接口配置 id
     * @param params 请求参数（REQUEST 类型字段从此 Map 取值）
     * @return 总影响行数
     */
    @AuditLog
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

        ShardRouteResult insertShard = resolveSharding(config, params);
        if (insertShard != null && insertConfig.getTables() != null && !insertConfig.getTables().isEmpty()) {
            insertConfig.getTables().get(0).setTableName(insertShard.getTableName());
        }
        Long insertDbConnId = insertShard != null ? insertShard.getDbConnectionId() : config.getDbConnectionId();

        List<TableInsertConfig> tables = insertConfig.getTables();
        if (tables == null || tables.isEmpty()) throw new BusinessException(400, "未配置插入表");
        if (tables.size() > 3) throw new BusinessException(400, "最多支持3张表");

        AuditContext auditCtx = AuditContextHolder.get();
        if (auditCtx != null) {
            auditCtx.setTargetTable(tables.stream()
                    .map(TableInsertConfig::getTableName)
                    .collect(java.util.stream.Collectors.joining(",")));
        }

        DbConnection conn = dbConnectionMapper.selectById(insertDbConnId);
        if (conn == null) throw new BusinessException(404, "数据库连接不存在");

        // 解析字段值 + 校验
        List<InsertBuilder.SqlResult> sqlResults = new ArrayList<>();
        for (TableInsertConfig tableConfig : tables) {
            Map<String, Object> fieldValues = new LinkedHashMap<>();
            for (FieldInsertConfig field : tableConfig.getFields()) {
                Object value = dataSourceResolver.resolve(field, params);
                fieldValues.put(field.getColumn(), value);
            }
            columnValidator.validate(tableConfig.getTableName(), fieldValues, insertDbConnId);
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

    // ─── M2-5 UPDATE 执行 ──────────────────────────────────────────────────────

    @AuditLog
    public int executeUpdate(Long id, Map<String, Object> params) {
        InterfaceConfig config = interfaceConfigMapper.selectById(id);
        if (config == null) throw new BusinessException(404, "接口配置不存在");
        if (!"UPDATE".equals(config.getType())) throw new BusinessException(400, "非 UPDATE 类型接口");

        UpdateConfigJson updateConfig;
        try {
            updateConfig = objectMapper.readValue(config.getConfigJson(), UpdateConfigJson.class);
        } catch (Exception e) {
            throw new BusinessException(400, "配置 JSON 解析失败: " + e.getMessage());
        }

        ShardRouteResult updateShard = resolveSharding(config, params);
        if (updateShard != null && updateConfig.getTables() != null && !updateConfig.getTables().isEmpty()) {
            String origName = updateConfig.getTables().get(0).getTableName();
            updateConfig.getTables().get(0).setTableName(updateShard.getTableName());
            if (updateConfig.getConditions() != null) {
                updateConfig.getConditions().stream()
                        .filter(c -> origName.equalsIgnoreCase(c.getTableName()))
                        .forEach(c -> c.setTableName(updateShard.getTableName()));
            }
        }
        Long updateDbConnId = updateShard != null ? updateShard.getDbConnectionId() : config.getDbConnectionId();

        List<TableUpdateConfig> tables = updateConfig.getTables();
        if (tables == null || tables.isEmpty()) throw new BusinessException(400, "未配置修改表");
        if (tables.size() > 3) throw new BusinessException(400, "最多支持3张表");

        DbConnection conn = dbConnectionMapper.selectById(updateDbConnId);
        if (conn == null) throw new BusinessException(404, "数据库连接不存在");

        List<UpdateBuilder.SqlResult> sqlResults = new ArrayList<>();
        List<String> tableNames = new ArrayList<>();
        for (TableUpdateConfig tableConfig : tables) {
            Map<String, Object> fieldValues = new LinkedHashMap<>();
            for (InsertConfigJson.FieldInsertConfig field : tableConfig.getFields()) {
                Object value = dataSourceResolver.resolve(field, params);
                fieldValues.put(field.getColumn(), value);
            }
            columnValidator.validate(tableConfig.getTableName(), fieldValues, updateDbConnId);
            List<ConditionConfig> tableConditions = filterConditions(
                    updateConfig.getConditions(), tableConfig.getTableName());
            if (tableConditions.isEmpty()) {
                throw new BusinessException(400, "表 " + tableConfig.getTableName() + " 无对应 WHERE 条件");
            }
            sqlResults.add(UpdateBuilder.build(tableConfig.getTableName(), fieldValues, tableConditions, params));
            tableNames.add(tableConfig.getTableName());
        }

        log.info("[M2-5] 执行 UPDATE，接口 id={}，共{}张表", id, sqlResults.size());

        String password = aesUtil.decrypt(conn.getPassword());
        int totalAffected = 0;

        try (Connection jdbc = DriverManager.getConnection(conn.getUrl(), conn.getUsername(), password)) {
            jdbc.setAutoCommit(false);
            try {
                AuditContext auditCtx = AuditContextHolder.get();
                if (auditCtx != null) {
                    String snapshot = captureSnapshot(jdbc, updateConfig.getConditions(), params, tableNames, conn.getDbType());
                    auditCtx.setBeforeSnapshot(snapshot);
                    auditCtx.setTargetTable(String.join(",", tableNames));
                    StringBuilder sqlText = new StringBuilder();
                    for (UpdateBuilder.SqlResult r : sqlResults) {
                        if (sqlText.length() > 0) sqlText.append("; ");
                        sqlText.append(r.sql);
                    }
                    auditCtx.setSqlText(sqlText.toString());
                }

                for (UpdateBuilder.SqlResult sql : sqlResults) {
                    log.info("[M2-5] SQL: {}", sql.sql);
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
                throw new BusinessException(500, "修改执行失败，已回滚: " + e.getMessage());
            }
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException(500, "数据库连接失败: " + e.getMessage());
        }

        return totalAffected;
    }

    // ─── M2-6 DELETE 预览 ──────────────────────────────────────────────────────

    public Map<String, List<Map<String, Object>>> deletePreview(Long id, Map<String, Object> params) {
        InterfaceConfig config = interfaceConfigMapper.selectById(id);
        if (config == null) throw new BusinessException(404, "接口配置不存在");
        if (!"DELETE".equals(config.getType())) throw new BusinessException(400, "非 DELETE 类型接口");

        DeleteConfigJson deleteConfig = parseDeleteConfig(config.getConfigJson());
        List<DeleteConfigJson.TableDeleteConfig> tables = deleteConfig.getTables();
        if (tables == null || tables.isEmpty()) throw new BusinessException(400, "未配置删除表");

        DbConnection conn = dbConnectionMapper.selectById(config.getDbConnectionId());
        if (conn == null) throw new BusinessException(404, "数据库连接不存在");

        String password = aesUtil.decrypt(conn.getPassword());
        boolean isOracle = "Oracle".equalsIgnoreCase(conn.getDbType());
        Map<String, List<Map<String, Object>>> result = new LinkedHashMap<>();

        try (Connection jdbc = DriverManager.getConnection(conn.getUrl(), conn.getUsername(), password)) {
            for (DeleteConfigJson.TableDeleteConfig tableConfig : tables) {
                List<Object> bindParams = new ArrayList<>();
                String where = buildDeleteWhere(tableConfig.getConditions(), params, bindParams);
                String sql = isOracle
                        ? "SELECT * FROM (SELECT * FROM " + tableConfig.getTableName() + where + ") WHERE ROWNUM <= 10"
                        : "SELECT * FROM " + tableConfig.getTableName() + where + " LIMIT 10";

                List<Map<String, Object>> rows = new ArrayList<>();
                try (PreparedStatement ps = jdbc.prepareStatement(sql)) {
                    for (int i = 0; i < bindParams.size(); i++) ps.setObject(i + 1, bindParams.get(i));
                    try (ResultSet rs = ps.executeQuery()) {
                        ResultSetMetaData meta = rs.getMetaData();
                        int colCount = meta.getColumnCount();
                        while (rs.next()) {
                            Map<String, Object> row = new LinkedHashMap<>();
                            for (int i = 1; i <= colCount; i++) row.put(meta.getColumnLabel(i), rs.getObject(i));
                            rows.add(row);
                        }
                    }
                }
                result.put(tableConfig.getTableName(), rows);
            }
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException(500, "预览查询失败: " + e.getMessage());
        }
        return result;
    }

    private DeleteConfigJson parseDeleteConfig(String configJson) {
        try {
            return objectMapper.readValue(configJson, DeleteConfigJson.class);
        } catch (Exception e) {
            throw new BusinessException(400, "配置 JSON 解析失败: " + e.getMessage());
        }
    }

    private String buildDeleteWhere(List<DeleteConfigJson.ConditionItem> conditions,
                                     Map<String, Object> params,
                                     List<Object> bindParams) {
        try {
            return DeleteBuilder.buildWhere(conditions, params, bindParams);
        } catch (IllegalArgumentException e) {
            throw new BusinessException(400, e.getMessage());
        }
    }

    // ─── M2-6 DELETE 执行 ──────────────────────────────────────────────────────

    @AuditLog
    public int executeDelete(Long id, Map<String, Object> params) {
        InterfaceConfig config = interfaceConfigMapper.selectById(id);
        if (config == null) throw new BusinessException(404, "接口配置不存在");
        if (!"DELETE".equals(config.getType())) throw new BusinessException(400, "非 DELETE 类型接口");

        DeleteConfigJson deleteConfig = parseDeleteConfig(config.getConfigJson());
        ShardRouteResult deleteShard = resolveSharding(config, params);
        if (deleteShard != null && deleteConfig.getTables() != null && !deleteConfig.getTables().isEmpty()) {
            deleteConfig.getTables().get(0).setTableName(deleteShard.getTableName());
        }
        Long deleteDbConnId = deleteShard != null ? deleteShard.getDbConnectionId() : config.getDbConnectionId();

        List<DeleteConfigJson.TableDeleteConfig> tables = deleteConfig.getTables();
        if (tables == null || tables.isEmpty()) throw new BusinessException(400, "未配置删除表");
        if (tables.size() > 3) throw new BusinessException(400, "最多支持3张表");

        DbConnection conn = dbConnectionMapper.selectById(deleteDbConnId);
        if (conn == null) throw new BusinessException(404, "数据库连接不存在");

        String password = aesUtil.decrypt(conn.getPassword());

        try (Connection jdbc = DriverManager.getConnection(conn.getUrl(), conn.getUsername(), password)) {
            // Step 1: COUNT 预检
            Map<String, Integer> countMap = new LinkedHashMap<>();
            int totalCount = 0;
            for (DeleteConfigJson.TableDeleteConfig tableConfig : tables) {
                int cnt = countDeleteRows(jdbc, tableConfig, params);
                countMap.put(tableConfig.getTableName(), cnt);
                totalCount += cnt;
            }

            // Step 2: 批量删除保护
            int allowBatch = config.getAllowBatchDelete() != null ? config.getAllowBatchDelete() : 0;
            if (totalCount > 1 && allowBatch == 0) {
                StringBuilder msg = new StringBuilder("批量删除保护：待删记录共 ")
                        .append(totalCount).append(" 条，各表：");
                countMap.forEach((table, cnt) -> msg.append(table).append("=").append(cnt).append(" "));
                throw new BusinessException(400, msg.toString().trim() + "，请先开启允许批量删除");
            }

            // Step 3: 构建 DELETE SQL
            List<DeleteBuilder.SqlResult> sqlResults = new ArrayList<>();
            for (DeleteConfigJson.TableDeleteConfig tableConfig : tables) {
                sqlResults.add(DeleteBuilder.build(tableConfig.getTableName(),
                        tableConfig.getConditions(), params));
            }

            // Step 4: 写审计上下文
            AuditContext auditCtx = AuditContextHolder.get();
            if (auditCtx != null) {
                try { auditCtx.setBeforeSnapshot(objectMapper.writeValueAsString(countMap)); }
                catch (Exception ignored) {}
                auditCtx.setTargetTable(tables.stream()
                        .map(DeleteConfigJson.TableDeleteConfig::getTableName)
                        .collect(Collectors.joining(",")));
                StringBuilder sqlText = new StringBuilder();
                for (DeleteBuilder.SqlResult r : sqlResults) {
                    if (sqlText.length() > 0) sqlText.append("; ");
                    sqlText.append(r.sql);
                }
                auditCtx.setSqlText(sqlText.toString());
            }

            // Step 5: 事务执行 DELETE
            jdbc.setAutoCommit(false);
            int totalAffected = 0;
            try {
                for (DeleteBuilder.SqlResult sql : sqlResults) {
                    log.info("[M2-6] SQL: {}", sql.sql);
                    try (PreparedStatement ps = jdbc.prepareStatement(sql.sql)) {
                        for (int i = 0; i < sql.params.size(); i++) ps.setObject(i + 1, sql.params.get(i));
                        totalAffected += ps.executeUpdate();
                    }
                }
                jdbc.commit();
            } catch (Exception e) {
                jdbc.rollback();
                throw new BusinessException(500, "删除执行失败，已回滚: " + e.getMessage());
            }
            return totalAffected;

        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException(500, "数据库连接失败: " + e.getMessage());
        }
    }

    private int countDeleteRows(Connection jdbc,
                                 DeleteConfigJson.TableDeleteConfig tableConfig,
                                 Map<String, Object> params) {
        List<Object> bindParams = new ArrayList<>();
        String where = buildDeleteWhere(tableConfig.getConditions(), params, bindParams);
        String sql = "SELECT COUNT(*) FROM " + tableConfig.getTableName() + where;
        try (PreparedStatement ps = jdbc.prepareStatement(sql)) {
            for (int i = 0; i < bindParams.size(); i++) ps.setObject(i + 1, bindParams.get(i));
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        } catch (Exception e) {
            throw new BusinessException(500, "COUNT 查询失败: " + e.getMessage());
        }
    }

    private String captureSnapshot(Connection jdbc,
                                    List<ConditionConfig> allConditions,
                                    Map<String, Object> params,
                                    List<String> tableNames,
                                    String dbType) {
        List<Map<String, Object>> allRows = new ArrayList<>();
        for (String tableName : tableNames) {
            List<ConditionConfig> tableConds = filterConditions(allConditions, tableName);
            if (tableConds.isEmpty()) continue;

            List<Object> bindParams = new ArrayList<>();
            String selectSql;
            if ("Oracle".equalsIgnoreCase(dbType)) {
                // Oracle 不支持 LIMIT，用子查询 + ROWNUM
                StringBuilder inner = new StringBuilder("SELECT * FROM ").append(tableName).append(" WHERE ");
                for (int i = 0; i < tableConds.size(); i++) {
                    ConditionConfig cond = tableConds.get(i);
                    if (i > 0) inner.append(" AND ");
                    inner.append(cond.getField()).append(" = ?");
                    bindParams.add(params.get(cond.getParamKey()));
                }
                selectSql = "SELECT * FROM (" + inner + ") WHERE ROWNUM <= 100";
            } else {
                StringBuilder sb = new StringBuilder("SELECT * FROM ").append(tableName).append(" WHERE ");
                for (int i = 0; i < tableConds.size(); i++) {
                    ConditionConfig cond = tableConds.get(i);
                    if (i > 0) sb.append(" AND ");
                    sb.append(cond.getField()).append(" = ?");
                    bindParams.add(params.get(cond.getParamKey()));
                }
                sb.append(" LIMIT 100");
                selectSql = sb.toString();
            }

            try (PreparedStatement ps = jdbc.prepareStatement(selectSql)) {
                for (int i = 0; i < bindParams.size(); i++) {
                    ps.setObject(i + 1, bindParams.get(i));
                }
                try (ResultSet rs = ps.executeQuery()) {
                    ResultSetMetaData metaData = rs.getMetaData();
                    int colCount = metaData.getColumnCount();
                    while (rs.next()) {
                        Map<String, Object> row = new LinkedHashMap<>();
                        row.put("__table__", tableName);
                        for (int i = 1; i <= colCount; i++) {
                            row.put(metaData.getColumnLabel(i), rs.getObject(i));
                        }
                        allRows.add(row);
                    }
                }
            } catch (Exception e) {
                log.warn("[M2-5] 快照查询失败，表={}: {}", tableName, e.getMessage());
            }
        }

        try {
            return objectMapper.writeValueAsString(allRows);
        } catch (JsonProcessingException e) {
            return "[]";
        }
    }

    private void validateUpdateConditions(UpdateConfigJson config, Long dbId) {
        if (config.getConditions() == null || config.getConditions().isEmpty()) {
            throw new BusinessException(400, "UPDATE 接口必须配置 WHERE 条件");
        }

        List<TableMeta> tables = tableMetaService.getTables(dbId);

        // 至少一张表的条件字段必须是主键或唯一索引
        boolean anyTableHasUniqueKey = false;

        for (TableUpdateConfig tableConfig : config.getTables()) {
            String tableName = tableConfig.getTableName();
            List<ConditionConfig> tableConds = filterConditions(config.getConditions(), tableName);

            if (tableConds.isEmpty()) {
                throw new BusinessException(400, "表 " + tableName + " 未配置 WHERE 条件");
            }

            TableMeta meta = tables.stream()
                    .filter(t -> t.getTableName().equalsIgnoreCase(tableName))
                    .findFirst()
                    .orElseThrow(() -> new BusinessException(400, "目标表不存在: " + tableName));

            boolean tableHasUniqueKey = tableConds.stream().anyMatch(cond -> {
                String field = cond.getField();
                return meta.getColumns().stream()
                        .filter(col -> col.getName().equalsIgnoreCase(field))
                        .anyMatch(col -> col.isPrimary() || col.isUnique());
            });

            if (tableHasUniqueKey) {
                anyTableHasUniqueKey = true;
            }
        }

        if (!anyTableHasUniqueKey) {
            throw new BusinessException(400, "UPDATE 接口的 WHERE 条件中必须至少包含一个主键或唯一索引字段");
        }
    }

    private List<ConditionConfig> filterConditions(List<ConditionConfig> all, String tableName) {
        if (all == null) return Collections.emptyList();
        return all.stream()
                .filter(c -> tableName.equalsIgnoreCase(c.getTableName()))
                .collect(Collectors.toList());
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
