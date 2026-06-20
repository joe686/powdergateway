package com.powergateway.config;

import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import org.apache.ibatis.reflection.MetaObject;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * MyBatis-Plus 自动填充处理器（BUG-012 修复）
 * 配合实体类上的 @TableField(fill = FieldFill.INSERT) / @TableField(fill = FieldFill.INSERT_UPDATE) 注解，
 * 在插入和更新时自动填充 createTime / updateTime 字段。
 */
@Component
public class MyMetaObjectHandler implements MetaObjectHandler {

    @Override
    public void insertFill(MetaObject metaObject) {
        LocalDateTime now = LocalDateTime.now();
        // 插入时填充 createTime（如果字段存在且为 null）
        if (metaObject.hasGetter("createTime")) {
            Object createTime = getFieldValByName("createTime", metaObject);
            if (createTime == null) {
                setFieldValByName("createTime", now, metaObject);
            }
        }
        // 插入时填充 updateTime（如果字段存在且为 null）
        if (metaObject.hasGetter("updateTime")) {
            Object updateTime = getFieldValByName("updateTime", metaObject);
            if (updateTime == null) {
                setFieldValByName("updateTime", now, metaObject);
            }
        }
    }

    @Override
    public void updateFill(MetaObject metaObject) {
        // 更新时始终刷新 updateTime
        if (metaObject.hasGetter("updateTime")) {
            setFieldValByName("updateTime", LocalDateTime.now(), metaObject);
        }
    }
}
