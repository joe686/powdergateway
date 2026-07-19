package com.powergateway;

import com.powergateway.dao.DbConnectionMapper;
import com.powergateway.dao.InterfaceConfigMapper;
import com.powergateway.model.DbConnection;
import com.powergateway.model.InterfaceConfig;
import com.powergateway.model.dto.ColumnMeta;
import com.powergateway.model.dto.FormulaValidateRequest;
import com.powergateway.model.dto.FormulaValidateResult;
import com.powergateway.model.dto.TableMeta;
import com.powergateway.service.FormulaValidator;
import com.powergateway.service.TableMetaService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
class UXC02FormulaValidatorTest {

    @Autowired FormulaValidator validator;

    @MockBean TableMetaService tableMetaService;
    @MockBean InterfaceConfigMapper interfaceConfigMapper;

    @BeforeEach
    void setUp() {
        ColumnMeta amount = new ColumnMeta(); amount.setName("amount");
        ColumnMeta status = new ColumnMeta(); status.setName("status");
        TableMeta orders = new TableMeta();
        orders.setTableName("orders");
        orders.setColumns(Arrays.asList(amount, status));
        Mockito.when(tableMetaService.getTables(1L)).thenReturn(Collections.singletonList(orders));
    }

    private FormulaValidateRequest req(String json) {
        FormulaValidateRequest r = new FormulaValidateRequest();
        r.setDbConnectionId(1L);
        r.setFormulaJson(json);
        return r;
    }

    @Test
    void 合法条件组_ok为true() {
        String json = "{\"type\":\"CONDITION_GROUP\",\"logic\":\"AND\",\"children\":["
                + "{\"nodeType\":\"CONDITION\",\"op\":\"GT\","
                + "\"left\":{\"kind\":\"COLUMN\",\"tableName\":\"orders\",\"columnName\":\"amount\"},"
                + "\"right\":{\"kind\":\"CONST\",\"constType\":\"NUMBER\",\"constValue\":100}}"
                + "]}";
        FormulaValidateResult r = validator.validate(req(json));
        assertTrue(r.isOk(), "错误：" + r.getErrors());
    }

    @Test
    void formulaJson为空_报错() {
        FormulaValidateResult r = validator.validate(req(null));
        assertFalse(r.isOk());
        assertEquals(1, r.getErrors().size());
    }

    @Test
    void JSON结构非法_报错() {
        FormulaValidateResult r = validator.validate(req("{broken"));
        assertFalse(r.isOk());
    }

    @Test
    void 根节点type非法_报错() {
        String json = "{\"type\":\"WHATEVER\",\"children\":[]}";
        FormulaValidateResult r = validator.validate(req(json));
        assertFalse(r.isOk());
    }

    @Test
    void CONDITION_GROUP_子节点为空_报错() {
        String json = "{\"type\":\"CONDITION_GROUP\",\"logic\":\"AND\",\"children\":[]}";
        FormulaValidateResult r = validator.validate(req(json));
        assertFalse(r.isOk());
    }

    @Test
    void NOT逻辑只能一个子节点_两个报错() {
        String json = "{\"type\":\"CONDITION_GROUP\",\"logic\":\"NOT\",\"children\":["
                + "{\"nodeType\":\"CONDITION\",\"op\":\"IS_NULL\","
                + "\"left\":{\"kind\":\"COLUMN\",\"tableName\":\"orders\",\"columnName\":\"amount\"}},"
                + "{\"nodeType\":\"CONDITION\",\"op\":\"IS_NULL\","
                + "\"left\":{\"kind\":\"COLUMN\",\"tableName\":\"orders\",\"columnName\":\"status\"}}"
                + "]}";
        FormulaValidateResult r = validator.validate(req(json));
        assertFalse(r.isOk());
    }

    @Test
    void IN操作符右操作数不是数组_报错() {
        String json = "{\"type\":\"CONDITION_GROUP\",\"logic\":\"AND\",\"children\":["
                + "{\"nodeType\":\"CONDITION\",\"op\":\"IN\","
                + "\"left\":{\"kind\":\"COLUMN\",\"tableName\":\"orders\",\"columnName\":\"status\"},"
                + "\"right\":{\"kind\":\"CONST\",\"constType\":\"STRING\",\"constValue\":\"PAID\"}}"
                + "]}";
        FormulaValidateResult r = validator.validate(req(json));
        assertFalse(r.isOk());
    }

    @Test
    void BETWEEN右操作数长度非2_报错() {
        String json = "{\"type\":\"CONDITION_GROUP\",\"logic\":\"AND\",\"children\":["
                + "{\"nodeType\":\"CONDITION\",\"op\":\"BETWEEN\","
                + "\"left\":{\"kind\":\"COLUMN\",\"tableName\":\"orders\",\"columnName\":\"amount\"},"
                + "\"right\":{\"kind\":\"CONST\",\"constType\":\"NUMBER_ARRAY\",\"constValue\":[1,2,3]}}"
                + "]}";
        FormulaValidateResult r = validator.validate(req(json));
        assertFalse(r.isOk());
    }

    @Test
    void COLUMN引用不存在的列_报错含path() {
        String json = "{\"type\":\"CONDITION_GROUP\",\"logic\":\"AND\",\"children\":["
                + "{\"nodeType\":\"CONDITION\",\"op\":\"EQ\","
                + "\"left\":{\"kind\":\"COLUMN\",\"tableName\":\"orders\",\"columnName\":\"nonexistent\"},"
                + "\"right\":{\"kind\":\"CONST\",\"constType\":\"NUMBER\",\"constValue\":1}}"
                + "]}";
        FormulaValidateResult r = validator.validate(req(json));
        assertFalse(r.isOk());
        assertTrue(r.getErrors().get(0).getPath().contains("children[0]"),
                "错误 path 应含 children[0]，实际=" + r.getErrors().get(0).getPath());
    }

    @Test
    void 多个错误一次性返回_不短路() {
        String json = "{\"type\":\"CONDITION_GROUP\",\"logic\":\"AND\",\"children\":["
                + "{\"nodeType\":\"CONDITION\",\"op\":\"IN\","
                + "\"left\":{\"kind\":\"COLUMN\",\"tableName\":\"orders\",\"columnName\":\"nonexistent\"},"
                + "\"right\":{\"kind\":\"CONST\",\"constType\":\"STRING\",\"constValue\":\"x\"}}"
                + "]}";
        FormulaValidateResult r = validator.validate(req(json));
        assertFalse(r.isOk());
        assertTrue(r.getErrors().size() >= 2, "应至少收集 2 个错误（IN 类型错误 + 列不存在），实际=" + r.getErrors());
    }

    @Test
    void interfaceRef接口不存在_报错() {
        Mockito.when(interfaceConfigMapper.selectById(999L)).thenReturn(null);
        String json = "{\"type\":\"CONDITION_GROUP\",\"logic\":\"AND\",\"children\":["
                + "{\"nodeType\":\"CONDITION\",\"op\":\"IS_NULL\","
                + "\"left\":{\"kind\":\"COLUMN\",\"tableName\":\"orders\",\"columnName\":\"amount\"}}"
                + "],\"interfaceRefs\":[{\"interfaceId\":999,\"paramKey\":\"x\"}]}";
        FormulaValidateResult r = validator.validate(req(json));
        assertFalse(r.isOk());
    }
}
