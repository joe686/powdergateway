package com.powergateway.dao;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.dynamic.datasource.annotation.DS;
import com.powergateway.model.SqlAuditLog;
import org.apache.ibatis.annotations.Mapper;

/**
 * SQL 审计日志 Mapper（M2-9）
 *
 * FB-043 · 2026-07-27 更新：
 *   sql_audit_log 表已合并到配置库 powergateway_config（简化客户环境部署）。
 *   application.yml 里 audit datasource URL 已改为指向 config 库。
 *   本处 @DS("audit") 保留（B 方案原则不动代码），datasource bean 保留
 *   物理上等同 master，为未来若需真拆两库预留切换接口。
 */
@Mapper
@DS("audit")
public interface SqlAuditLogMapper extends BaseMapper<SqlAuditLog> {
}
