package com.powergateway.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("perf_alert")
public class PerfAlert {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String alertType;
    private BigDecimal alertValue;
    private BigDecimal threshold;
    private String message;
    private LocalDateTime checkTime;
    private Integer resolved;
}
