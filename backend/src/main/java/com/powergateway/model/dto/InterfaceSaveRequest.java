package com.powergateway.model.dto;

import lombok.Data;

/**
 * 保存接口配置请求 DTO（M2-3 新建/更新查询接口配置）。
 */
@Data
public class InterfaceSaveRequest {

    /** 更新时必填，新建时为 null */
    private Long id;

    /** 接口名称，必填 */
    private String name;

    /** 关联数据库连接 id，必填 */
    private Long dbConnectionId;

    /** 接口类型：SELECT / INSERT / UPDATE / DELETE，默认 SELECT */
    private String type = "SELECT";

    /** 完整接口配置 JSON 字符串，必填 */
    private String configJson;
}
