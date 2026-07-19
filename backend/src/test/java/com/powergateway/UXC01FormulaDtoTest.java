package com.powergateway;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.powergateway.model.dto.FormulaJson;
import com.powergateway.model.dto.FormulaSaveRequest;
import com.powergateway.model.dto.FormulaValidateResult;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.*;

@ActiveProfiles("test")
class UXC01FormulaDtoTest {

    private final ObjectMapper om = new ObjectMapper();

    @Test
    void 反序列化条件组_含嵌套算术表达式() throws Exception {
        String json = "{\"version\":1,\"type\":\"CONDITION_GROUP\",\"logic\":\"AND\",\"children\":["
                + "{\"nodeType\":\"CONDITION\",\"op\":\"GT\","
                + "\"left\":{\"kind\":\"COLUMN\",\"tableName\":\"orders\",\"columnName\":\"amount\"},"
                + "\"right\":{\"kind\":\"CONST\",\"constType\":\"NUMBER\",\"constValue\":100}}"
                + "],\"interfaceRefs\":[]}";
        FormulaJson f = om.readValue(json, FormulaJson.class);
        assertEquals("CONDITION_GROUP", f.getType());
        assertEquals("AND", f.getLogic());
        assertEquals(1, f.getChildren().size());
    }

    @Test
    void FormulaSaveRequest_id可空() throws Exception {
        String json = "{\"name\":\"formula-a\",\"dbConnectionId\":1,\"formulaJson\":\"{}\"}";
        FormulaSaveRequest req = om.readValue(json, FormulaSaveRequest.class);
        assertNull(req.getId());
        assertEquals("formula-a", req.getName());
    }

    @Test
    void FormulaValidateResult_默认okTrue_errors非null() {
        FormulaValidateResult r = new FormulaValidateResult();
        assertTrue(r.isOk());
        assertNotNull(r.getErrors());
    }
}
