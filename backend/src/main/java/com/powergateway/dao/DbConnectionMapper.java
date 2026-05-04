package com.powergateway.dao;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.powergateway.model.DbConnection;
import org.apache.ibatis.annotations.Mapper;

/**
 * 数据库连接配置表 Mapper
 */
@Mapper
public interface DbConnectionMapper extends BaseMapper<DbConnection> {
}
