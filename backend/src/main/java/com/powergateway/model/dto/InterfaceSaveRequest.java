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

    /** 是否允许批量删除：0=否，1=是；仅 DELETE 类型接口使用 */
    private Integer allowBatchDelete;

    /** 是否开启缓存：0=否，1=是；仅 SELECT 类型接口生效 */
    private Integer cacheEnabled;
    /** 缓存 TTL（秒），0=永不过期 */
    private Integer cacheTtlSeconds;
    /** key 模板，支持 {参数名} 占位符；为空则按参数排序自动生成 */
    private String cacheKeyTemplate;

    /** 关联分库分表配置 id（可选，null 表示不启用分片路由）*/
    private Long shardConfigId;

    /** FN-06 默认响应格式：JSON/XML/CSV/FORM_DATA */
    private String responseFormat;
    /** FN-06 自定义响应头 JSON 字符串，格式 {"X-Foo":"bar"} */
    private String responseHeaders;
}
