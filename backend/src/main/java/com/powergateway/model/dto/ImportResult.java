package com.powergateway.model.dto;

import lombok.Data;
import java.util.ArrayList;
import java.util.List;

/**
 * FN-11 导入操作结果汇总 DTO
 */
@Data
public class ImportResult {
    /** 成功导入的条目列表（type + name） */
    private List<String> imported = new ArrayList<>();
    /** 跳过的条目列表（含原因） */
    private List<String> skipped = new ArrayList<>();
    /** 失败的条目列表（含原因） */
    private List<String> failed = new ArrayList<>();
    /** 冲突条目（等待前端处理，仅 ASK 策略使用） */
    private List<ConflictItem> conflicts = new ArrayList<>();

    public void addImported(String msg) { imported.add(msg); }
    public void addSkipped(String msg)  { skipped.add(msg); }
    public void addFailed(String msg)   { failed.add(msg); }
    public void addConflict(ConflictItem item) { conflicts.add(item); }

    public int total() { return imported.size() + skipped.size() + failed.size(); }

    @Data
    public static class ConflictItem {
        private String type;   // TEMPLATE / INTERFACE
        private String name;
        private String detail;

        public ConflictItem(String type, String name, String detail) {
            this.type = type;
            this.name = name;
            this.detail = detail;
        }
    }
}
