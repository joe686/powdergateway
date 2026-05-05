package com.powergateway;

import com.powergateway.model.dto.QueryConfigJson;
import com.powergateway.model.dto.QueryConfigJson.FieldDef;
import com.powergateway.model.dto.QueryConfigJson.TableDef;
import com.powergateway.utils.QueryBuilder;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.ActiveProfiles;

import java.util.Collections;
import java.util.HashMap;

import static org.junit.jupiter.api.Assertions.*;

@ActiveProfiles("test")
@DisplayName("M2-7 QueryBuilder 全量与分页")
class M27QueryBuilderTest {

    private QueryConfigJson simpleConfig() {
        QueryConfigJson c = new QueryConfigJson();
        TableDef t = new TableDef();
        t.setName("orders");
        t.setAlias("o");
        c.setTables(Collections.singletonList(t));
        FieldDef f = new FieldDef();
        f.setTable("o");
        f.setColumn("id");
        f.setAlias("id");
        c.setFields(Collections.singletonList(f));
        c.setConditions(Collections.emptyList());
        c.setJoins(Collections.emptyList());
        return c;
    }

    @Test
    void buildFull_不含LIMIT() {
        QueryBuilder.SqlResult r = QueryBuilder.buildFull(simpleConfig(), new HashMap<>());
        assertFalse(r.sql.contains("LIMIT"), "全量查询不应含 LIMIT，实际 SQL: " + r.sql);
    }

    @Test
    void buildPaginated_含正确LIMIT和OFFSET() {
        QueryBuilder.SqlResult r = QueryBuilder.buildPaginated(simpleConfig(), new HashMap<>(), 2, 5);
        assertTrue(r.sql.contains("LIMIT 5"), "应含 LIMIT 5，实际 SQL: " + r.sql);
        assertTrue(r.sql.contains("OFFSET 5"), "page=2 时应含 OFFSET 5，实际 SQL: " + r.sql);
    }

    @Test
    void buildPaginated_第一页OFFSET为零() {
        QueryBuilder.SqlResult r = QueryBuilder.buildPaginated(simpleConfig(), new HashMap<>(), 1, 10);
        assertTrue(r.sql.contains("LIMIT 10"), "实际 SQL: " + r.sql);
        assertTrue(r.sql.contains("OFFSET 0"), "第一页 OFFSET 应为 0，实际 SQL: " + r.sql);
    }

    @Test
    void buildPaginated_非法page_抛异常() {
        assertThrows(IllegalArgumentException.class,
            () -> QueryBuilder.buildPaginated(simpleConfig(), new HashMap<>(), 0, 10));
    }

    @Test
    void buildPaginated_非法pageSize_抛异常() {
        assertThrows(IllegalArgumentException.class,
            () -> QueryBuilder.buildPaginated(simpleConfig(), new HashMap<>(), 1, 0));
    }
}
