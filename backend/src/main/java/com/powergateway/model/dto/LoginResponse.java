package com.powergateway.model.dto;

import lombok.Data;

/**
 * 登录响应 DTO
 */
@Data
public class LoginResponse {

    /** Sa-Token 颁发的 token */
    private String token;

    /** 当前登录用户信息 */
    private UserInfo userInfo;

    @Data
    public static class UserInfo {
        private Long id;
        private String username;
        private String role;
    }
}
