package com.powergateway.dao;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.powergateway.model.SysLog;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.time.LocalDateTime;

@Mapper
public interface SysLogMapper extends BaseMapper<SysLog> {
    @Insert("INSERT INTO sys_log_history (module, action, operator, op_ip, op_time, level, error_msg, cost_ms, archived_time) " +
            "SELECT module, action, operator, op_ip, op_time, level, error_msg, cost_ms, NOW() " +
            "FROM sys_log WHERE op_time < #{threshold}")
    int archiveTo(@Param("threshold") LocalDateTime threshold);
}
