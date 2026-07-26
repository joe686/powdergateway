package com.powergateway.dao;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.powergateway.model.DictMapping;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import java.util.List;

/**
 * 字典映射 Mapper（FN-12）
 */
@Mapper
public interface DictMappingMapper extends BaseMapper<DictMapping> {

    /** 按 (system, key, direction) 查询全量字典条目（含 status=0，缓存装载用） */
    @Select("SELECT * FROM dict_mapping " +
            "WHERE system_code = #{system} AND dict_key = #{dictKey} " +
            "AND direction = #{direction} AND deleted = 0")
    List<DictMapping> selectByLookup(@Param("system") String system,
                                     @Param("dictKey") String dictKey,
                                     @Param("direction") Integer direction);

    /** 查询已有的 system_code 去重列表（前端下拉用） */
    @Select("SELECT DISTINCT system_code FROM dict_mapping " +
            "WHERE deleted = 0 ORDER BY system_code")
    List<String> selectDistinctSystems();
}
