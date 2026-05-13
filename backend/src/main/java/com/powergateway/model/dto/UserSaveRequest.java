package com.powergateway.model.dto;

import lombok.Data;

/**
 * 用户保存请求 DTO（SYS-3）。
 * id 为 null 时新增，非 null 时更新。
 * 更新时 password 为空则不修改密码。
 */
@Data
public class UserSaveRequest {
    /** null = 新增，非 null = 更新 */
    private Long id;
    /** 用户名，新增时必填 */
    private String username;
    /** 密码，新增时必填且 ≥6位；更新时为空=不改 */
    private String password;
    /** 角色：admin / user / readonly */
    private String role;
    /** 状态：1=启用，0=禁用 */
    private Integer status;
}
