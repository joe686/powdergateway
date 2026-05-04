package com.powergateway.dao;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.dynamic.datasource.annotation.DS;
import com.powergateway.model.SqlAuditLog;
import org.apache.ibatis.annotations.Mapper;

/**
 * SQL 审计日志 Mapper（M2-9）
 * 使用 @DS("audit") 将所有操作路由到独立审计数据源
 */
@Mapper
@DS("audit")
public interface SqlAuditLogMapper extends BaseMapper<SqlAuditLog> {
}
