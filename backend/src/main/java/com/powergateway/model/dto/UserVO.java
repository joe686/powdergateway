package com.powergateway.model.dto;

import com.powergateway.model.SysUser;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 用户响应 VO（SYS-3），不含 password 字段。
 */
@Data
public class UserVO {
    private Long id;
    private String username;
    private String role;
    private Integer status;
    private LocalDateTime createTime;

    public static UserVO from(SysUser user) {
        UserVO vo = new UserVO();
        vo.setId(user.getId());
        vo.setUsername(user.getUsername());
        vo.setRole(user.getRole());
        vo.setStatus(user.getStatus());
        vo.setCreateTime(user.getCreateTime());
        return vo;
    }
}
