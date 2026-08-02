package com.powergateway.model.dto;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class DictMappingVO {
    private Long id;
    /** CR-004 · v0.2.5 · 1=接口转换M1侧 2=可视化接口M2侧 3=通用共享 */
    private Integer scope;
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
