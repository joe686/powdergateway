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

    /**
     * 按 (scope, system, key, direction) 查询全量字典条目（含 status=0，缓存装载用）。
     * CR-004 · v0.2.5:
     *   - scope=1 M1 侧 → 传 scope=1 · scope 参数会用于 IN(1,3) 的 fallback 到共享
     *   - scope=2 M2 侧 → 传 scope=2 · fallback 到共享
     *   - scope=3 → 只查共享
     *
     * 采用 IN(scope, 3) 的方式：当 scope=3 时 IN(3,3) 退化为 =3，语义正确不重复。
     */
    @Select("SELECT * FROM dict_mapping " +
            "WHERE system_code = #{system} AND dict_key = #{dictKey} " +
            "AND direction = #{direction} AND deleted = 0 " +
            "AND scope IN (#{scope}, 3)")
    List<DictMapping> selectByLookup(@Param("scope") Integer scope,
                                     @Param("system") String system,
                                     @Param("dictKey") String dictKey,
                                     @Param("direction") Integer direction);

    /** 查询已有的 system_code 去重列表（前端下拉用，按 scope 精确过滤 · scope 必填） */
    @Select("SELECT DISTINCT system_code FROM dict_mapping " +
            "WHERE deleted = 0 AND scope = #{scope} " +
            "ORDER BY system_code")
    List<String> selectDistinctSystems(@Param("scope") Integer scope);
}
