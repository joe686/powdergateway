package com.powergateway.service;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.powergateway.dao.SysUserMapper;
import com.powergateway.exception.BusinessException;
import com.powergateway.model.SysUser;
import com.powergateway.model.dto.UserSaveRequest;
import com.powergateway.model.dto.UserVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class UserService {

    @Autowired
    private SysUserMapper sysUserMapper;

    private static final BCryptPasswordEncoder PASSWORD_ENCODER = new BCryptPasswordEncoder();
    private static final List<String> VALID_ROLES = Arrays.asList("admin", "user", "readonly", "tester");

    /** page/size 参数预留给将来分页，当前返回全量（与项目其他 list 接口一致）。 */
    public List<UserVO> list(String username, int page, int size) {
        LambdaQueryWrapper<SysUser> wrapper = new LambdaQueryWrapper<>();
        if (username != null && !username.trim().isEmpty()) {
            wrapper.like(SysUser::getUsername, username);
        }
        wrapper.orderByDesc(SysUser::getCreateTime);
        return sysUserMapper.selectList(wrapper).stream()
                .map(UserVO::from)
                .collect(Collectors.toList());
    }

    public Long save(UserSaveRequest req) {
        if (req.getRole() != null && !VALID_ROLES.contains(req.getRole())) {
            throw new BusinessException(400, "角色无效，只允许：admin、user、readonly、tester");
        }

        if (req.getId() == null) {
            if (req.getUsername() == null || req.getUsername().trim().isEmpty()) {
                throw new BusinessException(400, "用户名不能为空");
            }
            if (req.getPassword() == null || req.getPassword().length() < 6) {
                throw new BusinessException(400, "密码不能少于6位");
            }
            Long count = sysUserMapper.selectCount(
                    new LambdaQueryWrapper<SysUser>().eq(SysUser::getUsername, req.getUsername().trim()));
            if (count > 0) {
                throw new BusinessException(400, "用户名已存在");
            }
            SysUser entity = new SysUser();
            entity.setUsername(req.getUsername().trim());
            entity.setPassword(PASSWORD_ENCODER.encode(req.getPassword()));
            entity.setRole(req.getRole() != null ? req.getRole() : "user");
            entity.setStatus(req.getStatus() != null ? req.getStatus() : 1);
            sysUserMapper.insert(entity);
            return entity.getId();
        } else {
            SysUser existing = sysUserMapper.selectById(req.getId());
            if (existing == null) throw new BusinessException(404, "用户不存在");
            SysUser update = new SysUser();
            update.setId(req.getId());
            if (req.getPassword() != null && !req.getPassword().isEmpty()) {
                if (req.getPassword().length() < 6) {
                    throw new BusinessException(400, "密码不能少于6位");
                }
                update.setPassword(PASSWORD_ENCODER.encode(req.getPassword()));
            }
            if (req.getRole() != null) update.setRole(req.getRole());
            if (req.getStatus() != null) update.setStatus(req.getStatus());
            sysUserMapper.updateById(update);
            return req.getId();
        }
    }

    public void delete(Long id) {
        Long currentUserId = StpUtil.getLoginIdAsLong();
        if (currentUserId.equals(id)) {
            throw new BusinessException(400, "不能删除当前登录账号");
        }
        SysUser user = sysUserMapper.selectById(id);
        if (user == null) throw new BusinessException(404, "用户不存在");
        if ("admin".equals(user.getRole())) {
            Long adminCount = sysUserMapper.selectCount(
                    new LambdaQueryWrapper<SysUser>().eq(SysUser::getRole, "admin"));
            if (adminCount <= 1) {
                throw new BusinessException(400, "至少保留一个管理员账号，无法删除");
            }
        }
        sysUserMapper.deleteById(id);
    }
}
