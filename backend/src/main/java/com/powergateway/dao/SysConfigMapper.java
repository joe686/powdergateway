package com.powergateway.dao;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.powergateway.model.SysConfig;
import org.apache.ibatis.annotations.Mapper;

/**
 * 系统配置表 Mapper（KV 结构，主键为 config_key）
 */
@Mapper
public interface SysConfigMapper extends BaseMapper<SysConfig> {
}
