package com.powergateway;

import com.powergateway.model.FieldFormula;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.ActiveProfiles;

import java.lang.reflect.Field;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

@ActiveProfiles("test")
class UXC00FieldFormulaEntityTest {

    @Test
    void 实体应包含updateTime字段_并被MyBatisPlus自动填充注解标注() throws NoSuchFieldException {
        Field updateTime = FieldFormula.class.getDeclaredField("updateTime");
        assertEquals(LocalDateTime.class, updateTime.getType(), "updateTime 类型必须为 LocalDateTime");
        com.baomidou.mybatisplus.annotation.TableField tf =
                updateTime.getAnnotation(com.baomidou.mybatisplus.annotation.TableField.class);
        assertNotNull(tf, "updateTime 必须有 @TableField 注解");
        assertEquals(com.baomidou.mybatisplus.annotation.FieldFill.INSERT_UPDATE, tf.fill(),
                "updateTime 必须配置 FieldFill.INSERT_UPDATE");
    }
}
