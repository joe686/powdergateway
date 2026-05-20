package com.powergateway.dao;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.powergateway.model.PerfAlert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface PerfAlertMapper extends BaseMapper<PerfAlert> {

    @Select("SELECT * FROM perf_alert WHERE resolved = 0 " +
            "ORDER BY check_time DESC LIMIT #{limit}")
    List<PerfAlert> selectUnresolvedLatest(@Param("limit") int limit);
}
