package com.powergateway;

import com.powergateway.model.dto.DeleteConfigJson.ConditionItem;
import com.powergateway.utils.DeleteBuilder;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.ActiveProfiles;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

@ActiveProfiles("test")
class M26DeleteBuilderTest {

    private ConditionItem cond(String field, String op, String paramKey) {
        ConditionItem c = new ConditionItem();
        c.setField(field);
        c.setOp(op);
        c.setParamKey(paramKey);
        return c;
    }

    @Test
    void 单条件EQ_生成正确SQL() {
        Map<String, Object> params = new HashMap<>();
        params.put("orderId", 42L);

        DeleteBuilder.SqlResult r = DeleteBuilder.build(
                "orders",
                Collections.singletonList(cond("id", "EQ", "orderId")),
                params);

        assertEquals("DELETE FROM orders WHERE id = ?", r.sql);
        assertEquals(Collections.singletonList(42L), r.params);
    }

    @Test
    void 多条件AND连接_生成正确SQL() {
        Map<String, Object> params = new HashMap<>();
        params.put("status", "DELETED");
        params.put("userId", 10L);

        DeleteBuilder.SqlResult r = DeleteBuilder.build(
                "orders",
                Arrays.asList(
                        cond("status", "EQ", "status"),
                        cond("user_id", "EQ", "userId")),
                params);

        assertEquals("DELETE FROM orders WHERE status = ? AND user_id = ?", r.sql);
        assertEquals(Arrays.asList("DELETED", 10L), r.params);
    }

    @Test
    void LIKE操作符_生成正确SQL() {
        Map<String, Object> params = new HashMap<>();
        params.put("nameKey", "%test%");

        DeleteBuilder.SqlResult r = DeleteBuilder.build(
                "items",
                Collections.singletonList(cond("name", "LIKE", "nameKey")),
                params);

        assertEquals("DELETE FROM items WHERE name LIKE ?", r.sql);
    }

    @Test
    void NE操作符_生成正确SQL() {
        Map<String, Object> params = new HashMap<>();
        params.put("typeKey", "VIP");

        DeleteBuilder.SqlResult r = DeleteBuilder.build(
                "items",
                Collections.singletonList(cond("type", "NE", "typeKey")),
                params);

        assertEquals("DELETE FROM items WHERE type <> ?", r.sql);
    }

    @Test
    void GT操作符_生成正确SQL() {
        Map<String, Object> params = new HashMap<>();
        params.put("ageKey", 18);

        DeleteBuilder.SqlResult r = DeleteBuilder.build(
                "users",
                Collections.singletonList(cond("age", "GT", "ageKey")),
                params);

        assertEquals("DELETE FROM users WHERE age > ?", r.sql);
    }

    @Test
    void 条件为空列表_抛IllegalArgumentException() {
        assertThrows(IllegalArgumentException.class, () ->
                DeleteBuilder.build("orders", Collections.emptyList(), Collections.emptyMap()));
    }

    @Test
    void 条件为null_抛IllegalArgumentException() {
        assertThrows(IllegalArgumentException.class, () ->
                DeleteBuilder.build("orders", null, Collections.emptyMap()));
    }
}
