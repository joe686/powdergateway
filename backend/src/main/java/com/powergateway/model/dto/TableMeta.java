package com.powergateway.model.dto;

import lombok.Data;

import java.util.List;

@Data
public class TableMeta {
    private String tableName;
    private String comment;
    private List<ColumnMeta> columns;
}
