package com.powergateway.utils;

import com.powergateway.exception.BusinessException;
import com.powergateway.model.dto.ShardRuleJson;
import com.powergateway.model.dto.ShardRuleJson.DbSegment;
import com.powergateway.model.dto.ShardRuleJson.ShardItem;
import com.powergateway.model.dto.ShardRouteResult;

import java.util.List;

public class ShardRouter {

    public static ShardRouteResult route(ShardRuleJson rule, String fieldValue) {
        if (rule == null || rule.getAlgorithm() == null) {
            throw new BusinessException(400, "分片规则配置不完整");
        }
        if (fieldValue == null) {
            throw new BusinessException(400, "路由字段值为空");
        }
        long val;
        try {
            val = Long.parseLong(fieldValue.trim());
        } catch (NumberFormatException e) {
            throw new BusinessException(400, "路由字段值非数字: " + fieldValue);
        }
        String type = rule.getAlgorithm().getType();
        if ("MODULO".equalsIgnoreCase(type)) return routeModulo(rule, val);
        if ("RANGE".equalsIgnoreCase(type))  return routeRange(rule, val);
        throw new BusinessException(400, "不支持的路由算法: " + type);
    }

    private static ShardRouteResult routeModulo(ShardRuleJson rule, long val) {
        Integer divisor = rule.getAlgorithm().getDivisor();
        if (divisor == null || divisor <= 0) {
            throw new BusinessException(400, "取模路由除数必须大于 0");
        }
        int idx = (int)(val % divisor);
        if (idx < 0) idx += divisor;

        List<DbSegment> segments = rule.getDbSegments();
        if (segments == null || segments.isEmpty()) {
            throw new BusinessException(400, "取模路由未配置分段（dbSegments）");
        }
        for (DbSegment seg : segments) {
            if (idx >= seg.getIndexStart() && idx <= seg.getIndexEnd()) {
                int padding = seg.getIndexPadding() != null ? seg.getIndexPadding() : 0;
                ShardRouteResult result = new ShardRouteResult();
                result.setDbConnectionId(seg.getDbConnectionId());
                result.setTableName(seg.getTablePrefix() + pad(idx, padding));
                return result;
            }
        }
        throw new BusinessException(400, "取模索引 " + idx + " 无匹配分段，请检查 dbSegments 配置");
    }

    private static ShardRouteResult routeRange(ShardRuleJson rule, long val) {
        List<ShardItem> shards = rule.getShards();
        if (shards == null || shards.isEmpty()) {
            throw new BusinessException(400, "范围路由未配置分片列表（shards）");
        }
        for (ShardItem shard : shards) {
            if (val >= shard.getRangeStart() && val <= shard.getRangeEnd()) {
                ShardRouteResult result = new ShardRouteResult();
                result.setDbConnectionId(shard.getDbConnectionId());
                result.setTableName(shard.getTableName());
                return result;
            }
        }
        throw new BusinessException(400, "值 " + val + " 无匹配分片范围，请检查 shards 配置");
    }

    /** 供测试直接调用 */
    public static String pad(int idx, int padding) {
        if (padding <= 0) return String.valueOf(idx);
        return String.format("%0" + padding + "d", idx);
    }
}
