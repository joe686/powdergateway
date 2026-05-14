package com.powergateway.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("perf_stat")
public class PerfStatRecord {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long interfaceId;
    private String opType;
    private Integer costMs;
    private Integer success;
    private LocalDateTime statTime;
}
