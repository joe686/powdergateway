package com.powergateway.dao;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.powergateway.model.SysLog;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.time.LocalDateTime;

@Mapper
public interface SysLogMapper extends BaseMapper<SysLog> {
    /**
     * 将 sys_log 中超期记录批量归档到 sys_log_history（CHG-006）。
     * 使用单条 INSERT...SELECT 语句，效率高于逐条循环。
     */
    @Insert("INSERT INTO sys_log_history (module, action, operator, op_ip, op_time, level, error_msg, cost_ms, archived_time) " +
            "SELECT module, action, operator, op_ip, op_time, level, error_msg, cost_ms, NOW() " +
            "FROM sys_log WHERE op_time < #{threshold}")
    int archiveTo(@Param("threshold") LocalDateTime threshold);
}
