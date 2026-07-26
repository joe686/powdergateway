package com.powergateway.model.dto;

import lombok.Data;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;
import java.util.ArrayList;
import java.util.List;

/**
 * 字典映射批量导入结果（FN-12）
 */
@Data
@NoArgsConstructor
public class DictMappingImportResult {
    /** 成功写入的行数（整体回滚时置 0） */
    private int successCount;
    /** 失败行列表（整体回滚语义：只记录第一个错误行） */
    private List<FailedRow> failedRows = new ArrayList<>();

    /**
     * 失败行详情
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FailedRow {
        /** Excel 行号（表头=1，数据行从 2 开始） */
        private int rowIndex;
        /** 错误描述 */
        private String errorMsg;
    }
}
