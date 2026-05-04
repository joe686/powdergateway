package com.powergateway.dao;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.powergateway.model.ShardConfig;
import org.apache.ibatis.annotations.Mapper;

/**
 * 分库分表配置表 Mapper
 */
@Mapper
public interface ShardConfigMapper extends BaseMapper<ShardConfig> {
}
