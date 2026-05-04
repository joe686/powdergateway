package com.powergateway.model.dto;

import lombok.Data;

import javax.validation.constraints.NotBlank;

@Data
public class DbConnectionSaveRequest {

    /** 更新时传入，新建时为 null */
    private Long id;

    @NotBlank(message = "连接名不能为空")
    private String name;

    @NotBlank(message = "数据库类型不能为空")
    private String dbType;

    @NotBlank(message = "JDBC URL 不能为空")
    private String url;

    @NotBlank(message = "用户名不能为空")
    private String username;

    /**
     * 新建时必填；更新时传 "***" 表示不修改原密码
     */
    private String password;

    @NotBlank(message = "环境不能为空")
    private String env;

    /** 连接池大小，默认 5 */
    private Integer poolSize;

    /** 连接超时（毫秒），默认 3000 */
    private Integer timeout;
}
