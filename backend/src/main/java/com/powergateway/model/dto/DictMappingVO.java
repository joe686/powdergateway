package com.powergateway.model.dto;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class DictMappingVO {
    private Long id;
    private String systemCode;
    private String dictKey;
    private Integer direction;
    private String sourceValue;
    private String targetValue;
    private String cnLabel;
    private Integer status;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
