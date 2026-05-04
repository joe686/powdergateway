package com.powergateway;

import com.powergateway.model.dto.UpdateConfigJson.ConditionConfig;
import com.powergateway.utils.UpdateBuilder;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.ActiveProfiles;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

@ActiveProfiles("test")
class UpdateBuilderTest {

    @Test
    void 单字段单条件_生成正确SQL() {
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("status", "active");

        ConditionConfig cond = new ConditionConfig();
        cond.setTableName("orders");
        cond.setField("id");
        cond.setOp("EQ");
        cond.setParamKey("orderId");

        Map<String, Object> params = new HashMap<>();
        params.put("orderId", 42L);

        UpdateBuilder.SqlResult result = UpdateBuilder.build(
                "orders", fields, Collections.singletonList(cond), params);

        assertEquals("UPDATE orders SET status = ? WHERE id = ?", result.sql);
        assertEquals(Arrays.asList("active", 42L), result.params);
    }

    @Test
    void 多字段多条件_生成正确SQL() {
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("price", 99.9);
        fields.put("category", "A");

        ConditionConfig c1 = new ConditionConfig();
        c1.setTableName("products");
        c1.setField("id");
        c1.setOp("EQ");
        c1.setParamKey("productId");

        ConditionConfig c2 = new ConditionConfig();
        c2.setTableName("products");
        c2.setField("brand");
        c2.setOp("EQ");
        c2.setParamKey("brand");

        Map<String, Object> params = new HashMap<>();
        params.put("productId", 10L);
        params.put("brand", "Nike");

        UpdateBuilder.SqlResult result = UpdateBuilder.build(
                "products", fields, Arrays.asList(c1, c2), params);

        assertEquals("UPDATE products SET price = ?, category = ? WHERE id = ? AND brand = ?", result.sql);
        assertEquals(Arrays.asList(99.9, "A", 10L, "Nike"), result.params);
    }

    @Test
    void 无修改字段_抛异常() {
        assertThrows(IllegalArgumentException.class, () ->
                UpdateBuilder.build("orders", Collections.emptyMap(),
                        Collections.emptyList(), Collections.emptyMap()));
    }

    @Test
    void 无条件_抛异常() {
        Map<String, Object> fields = new HashMap<>();
        fields.put("status", "x");
        assertThrows(IllegalArgumentException.class, () ->
                UpdateBuilder.build("orders", fields,
                        Collections.emptyList(), Collections.emptyMap()));
    }

    @Test
    void LIKE操作符_生成正确SQL() {
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("status", "active");

        ConditionConfig cond = new ConditionConfig();
        cond.setTableName("orders");
        cond.setField("name");
        cond.setOp("LIKE");
        cond.setParamKey("nameKey");

        Map<String, Object> params = new HashMap<>();
        params.put("nameKey", "%test%");

        UpdateBuilder.SqlResult result = UpdateBuilder.build(
                "orders", fields, Collections.singletonList(cond), params);

        assertEquals("UPDATE orders SET status = ? WHERE name LIKE ?", result.sql);
    }

    @Test
    void NE操作符_生成正确SQL() {
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("status", "inactive");

        ConditionConfig cond = new ConditionConfig();
        cond.setTableName("orders");
        cond.setField("type");
        cond.setOp("NE");
        cond.setParamKey("typeKey");

        Map<String, Object> params = new HashMap<>();
        params.put("typeKey", "VIP");

        UpdateBuilder.SqlResult result = UpdateBuilder.build(
                "orders", fields, Collections.singletonList(cond), params);

        assertEquals("UPDATE orders SET status = ? WHERE type <> ?", result.sql);
    }
}
