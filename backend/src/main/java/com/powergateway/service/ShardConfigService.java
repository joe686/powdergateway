package com.powergateway.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.powergateway.dao.DbConnectionMapper;
import com.powergateway.dao.ShardConfigMapper;
import com.powergateway.exception.BusinessException;
import com.powergateway.model.DbConnection;
import com.powergateway.model.ShardConfig;
import com.powergateway.model.dto.ShardRuleJson;
import com.powergateway.model.dto.ShardRouteResult;
import com.powergateway.model.dto.ShardSaveRequest;
import com.powergateway.utils.AesUtil;
import com.powergateway.utils.ShardRouter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.sql.*;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
public class ShardConfigService {

    @Autowired private ShardConfigMapper shardConfigMapper;
    @Autowired private DbConnectionMapper dbConnectionMapper;
    @Autowired private AesUtil aesUtil;
    @Autowired private ObjectMapper objectMapper;

    public List<ShardConfig> list(String name, int page, int size) {
        LambdaQueryWrapper<ShardConfig> wrapper = new LambdaQueryWrapper<>();
        if (name != null && !name.trim().isEmpty()) {
            wrapper.like(ShardConfig::getName, name);
        }
        wrapper.orderByDesc(ShardConfig::getCreateTime);
        return shardConfigMapper.selectList(wrapper);
    }

    public Long save(ShardSaveRequest req) {
        if (req.getName() == null || req.getName().trim().isEmpty()) {
            throw new BusinessException(400, "配置名称不能为空");
        }
        if (req.getShardRule() == null || req.getShardRule().trim().isEmpty()) {
            throw new BusinessException(400, "分片规则不能为空");
        }
        ShardRuleJson rule;
        try {
            rule = objectMapper.readValue(req.getShardRule(), ShardRuleJson.class);
        } catch (Exception e) {
            throw new BusinessException(400, "分片规则 JSON 格式错误: " + e.getMessage());
        }

        ShardConfig entity = new ShardConfig();
        entity.setName(req.getName());
        entity.setShardRule(req.getShardRule());
        entity.setRequestField(rule.getRoutingField());

        if (req.getId() != null) {
            entity.setId(req.getId());
            shardConfigMapper.updateById(entity);
            return req.getId();
        }
        shardConfigMapper.insert(entity);
        return entity.getId();
    }

    public void delete(Long id) {
        if (shardConfigMapper.selectById(id) == null) {
            throw new BusinessException(404, "分片配置不存在");
        }
        shardConfigMapper.deleteById(id);
    }

    public ShardRouteResult preview(Long shardConfigId, Map<String, Object> params) {
        ShardConfig config = shardConfigMapper.selectById(shardConfigId);
        if (config == null) throw new BusinessException(404, "分片配置不存在");

        ShardRuleJson rule;
        try {
            rule = objectMapper.readValue(config.getShardRule(), ShardRuleJson.class);
        } catch (Exception e) {
            throw new BusinessException(400, "分片规则 JSON 解析失败: " + e.getMessage());
        }

        if (rule.getFieldLookup() != null) {
            String lookedUp = doFieldLookup(rule.getFieldLookup(), params);
            params.put(rule.getRoutingField(), lookedUp);
        }

        Object fieldVal = params.get(rule.getRoutingField());
        if (fieldVal == null) {
            throw new BusinessException(400, "路由字段 '" + rule.getRoutingField() + "' 不在请求参数中");
        }

        ShardRouteResult result = ShardRouter.route(rule, String.valueOf(fieldVal));

        DbConnection conn = dbConnectionMapper.selectById(result.getDbConnectionId());
        if (conn != null) result.setDbName(conn.getName());

        return result;
    }

    private String doFieldLookup(ShardRuleJson.FieldLookupConfig lookup, Map<String, Object> params) {
        Object condVal = params.get(lookup.getConditionParamKey());
        if (condVal == null) {
            throw new BusinessException(400, "补查条件字段 '" + lookup.getConditionParamKey() + "' 不在请求参数中");
        }
        DbConnection conn = dbConnectionMapper.selectById(lookup.getDbConnectionId());
        if (conn == null) throw new BusinessException(404, "补查数据源不存在");

        String password = aesUtil.decrypt(conn.getPassword());
        String sql = "SELECT " + lookup.getTargetColumn() +
                     " FROM "  + lookup.getTable() +
                     " WHERE " + lookup.getConditionColumn() + " = ?";

        try (Connection jdbc = DriverManager.getConnection(conn.getUrl(), conn.getUsername(), password);
             PreparedStatement ps = jdbc.prepareStatement(sql)) {
            ps.setObject(1, condVal);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    Object val = rs.getObject(1);
                    return val != null ? val.toString() : null;
                }
                throw new BusinessException(404, "补查无结果: " + lookup.getTable() +
                        " WHERE " + lookup.getConditionColumn() + "=" + condVal);
            }
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException(500, "补查执行失败: " + e.getMessage());
        }
    }
}
