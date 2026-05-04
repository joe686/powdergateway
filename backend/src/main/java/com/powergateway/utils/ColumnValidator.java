package com.powergateway.utils;

import com.powergateway.exception.BusinessException;
import com.powergateway.model.dto.ColumnMeta;
import com.powergateway.model.dto.TableMeta;
import com.powergateway.service.TableMetaService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * 基于表结构元数据的字段校验器（M2-4，M2-5 复用）。
 *
 * 校验规则：
 * <ol>
 *   <li>目标表必须存在于元数据中</li>
 *   <li>若配置中某字段的列不允许为空（nullable=false）且非自增主键，则该字段的值不能为 null</li>
 * </ol>
 */
@Component
public class ColumnValidator {

    @Autowired
    private TableMetaService tableMetaService;

    /**
     * 校验即将插入/更新的字段值是否合法。
     *
     * @param tableName   目标表名
     * @param fieldValues 字段名 → 值的映射（仅包含配置中声明的字段）
     * @param dbId        数据库连接 id（用于查元数据）
     */
    public void validate(String tableName, Map<String, Object> fieldValues, Long dbId) {
        List<TableMeta> tables = tableMetaService.getTables(dbId);

        TableMeta target = tables.stream()
                .filter(t -> t.getTableName().equalsIgnoreCase(tableName))
                .findFirst()
                .orElseThrow(() -> new BusinessException(400, "目标表不存在: " + tableName));

        for (ColumnMeta col : target.getColumns()) {
            // 仅校验配置中明确提供的字段：若该字段不允许为空且配置中已配置但值为 null，则报错
            if (fieldValues.containsKey(col.getName()) && !col.isNullable() && !col.isPrimary()) {
                Object value = fieldValues.get(col.getName());
                if (value == null) {
                    throw new BusinessException(400, "字段 '" + col.getName() + "' 不能为空");
                }
            }
        }
    }
}
