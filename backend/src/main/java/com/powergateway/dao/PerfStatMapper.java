package com.powergateway.dao;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.powergateway.model.PerfStatRecord;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Mapper
public interface PerfStatMapper extends BaseMapper<PerfStatRecord> {

    @Select("SELECT DATE_FORMAT(stat_time, '%H:00') AS label, " +
            "SUM(CASE WHEN success = 1 THEN 1 ELSE 0 END) AS successCount, " +
            "SUM(CASE WHEN success = 0 THEN 1 ELSE 0 END) AS failCount, " +
            "ROUND(COALESCE(AVG(cost_ms), 0), 0) AS avgCostMs " +
            "FROM perf_stat WHERE stat_time >= #{from} AND stat_time < #{to} " +
            "GROUP BY DATE_FORMAT(stat_time, '%H:00') " +
            "ORDER BY DATE_FORMAT(stat_time, '%H:00')")
    List<Map<String, Object>> groupByHour(@Param("from") LocalDateTime from,
                                          @Param("to") LocalDateTime to);

    @Select("SELECT DATE_FORMAT(stat_time, '%Y-%m-%d') AS label, " +
            "SUM(CASE WHEN success = 1 THEN 1 ELSE 0 END) AS successCount, " +
            "SUM(CASE WHEN success = 0 THEN 1 ELSE 0 END) AS failCount, " +
            "ROUND(COALESCE(AVG(cost_ms), 0), 0) AS avgCostMs " +
            "FROM perf_stat WHERE stat_time >= #{from} AND stat_time < #{to} " +
            "GROUP BY DATE_FORMAT(stat_time, '%Y-%m-%d') " +
            "ORDER BY DATE_FORMAT(stat_time, '%Y-%m-%d')")
    List<Map<String, Object>> groupByDay(@Param("from") LocalDateTime from,
                                         @Param("to") LocalDateTime to);

    @Select("SELECT COUNT(*) AS total, " +
            "SUM(CASE WHEN success = 0 THEN 1 ELSE 0 END) AS failCount, " +
            "COALESCE(AVG(cost_ms), 0) AS avgMs " +
            "FROM perf_stat WHERE stat_time >= #{from} AND stat_time < #{to}")
    Map<String, Object> statBetween(@Param("from") LocalDateTime from,
                                    @Param("to") LocalDateTime to);
}
