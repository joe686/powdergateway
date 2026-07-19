package com.powergateway;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.powergateway.dao.DbConnectionMapper;
import com.powergateway.dao.FieldFormulaMapper;
import com.powergateway.exception.BusinessException;
import com.powergateway.model.DbConnection;
import com.powergateway.model.FieldFormula;
import com.powergateway.model.dto.*;
import com.powergateway.service.FieldFormulaService;
import com.powergateway.service.TableMetaService;
import com.powergateway.utils.AesUtil;
import org.junit.jupiter.api.*;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class UXC03FieldFormulaServiceTest {

    @Autowired FieldFormulaService service;
    @Autowired FieldFormulaMapper mapper;
    @Autowired DbConnectionMapper dbConnMapper;
    @Autowired AesUtil aesUtil;

    /**
     * MockBean 屏蔽真实业务库连接：Validator 会调用 TableMetaService.getTables，
     * 返回一张含 amount 列的假表，避免测试依赖 JDBC 连接。
     */
    @MockBean TableMetaService tableMetaService;

    private Long dbId;

    @BeforeEach
    void setup() {
        DbConnection conn = new DbConnection();
        conn.setName("uxc-test-db");
        conn.setDbType("MySQL");
        conn.setUrl("jdbc:h2:mem:uxctest;DB_CLOSE_DELAY=-1");
        conn.setUsername("sa");
        conn.setPassword(aesUtil.encrypt(""));
        conn.setEnv("dev");
        dbConnMapper.insert(conn);
        dbId = conn.getId();

        ColumnMeta amount = new ColumnMeta(); amount.setName("amount");
        TableMeta orders = new TableMeta();
        orders.setTableName("orders");
        orders.setColumns(Collections.singletonList(amount));
        Mockito.when(tableMetaService.getTables(dbId))
               .thenReturn(Collections.singletonList(orders));
    }

    private FormulaSaveRequest legalReq(String name) {
        FormulaSaveRequest r = new FormulaSaveRequest();
        r.setName(name);
        r.setScene("测试场景");
        r.setDbConnectionId(dbId);
        r.setFormulaJson("{\"type\":\"CONDITION_GROUP\",\"logic\":\"AND\",\"children\":["
                + "{\"nodeType\":\"CONDITION\",\"op\":\"GT\","
                + "\"left\":{\"kind\":\"COLUMN\",\"tableName\":\"orders\",\"columnName\":\"amount\"},"
                + "\"right\":{\"kind\":\"CONST\",\"constType\":\"NUMBER\",\"constValue\":100}}"
                + "]}");
        r.setRemark("单元测试用");
        return r;
    }

    @Test @Order(1)
    void 保存合法公式_返回正整数id() {
        Long id = service.save(legalReq("f-save-1"), "admin");
        assertNotNull(id);
        assertTrue(id > 0);
    }

    @Test @Order(2)
    void 保存同名公式_抛BusinessException400() {
        service.save(legalReq("f-dup"), "admin");
        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.save(legalReq("f-dup"), "admin"));
        assertEquals(400, ex.getCode());
        assertTrue(ex.getMessage().contains("已存在"));
    }

    @Test @Order(3)
    void 保存空formulaJson_抛BusinessException400() {
        FormulaSaveRequest r = legalReq("f-null-json");
        r.setFormulaJson(null);
        assertThrows(BusinessException.class, () -> service.save(r, "admin"));
    }

    @Test @Order(4)
    void 更新已存在公式_覆盖成功() {
        Long id = service.save(legalReq("f-upd"), "admin");
        FormulaSaveRequest upd = legalReq("f-upd");
        upd.setId(id);
        upd.setRemark("已更新");
        Long r = service.save(upd, "admin");
        assertEquals(id, r);
        FieldFormulaDto dto = service.getById(id);
        assertEquals("已更新", dto.getRemark());
    }

    @Test @Order(5)
    void 复制公式_新记录name带copy后缀_其余字段一致() {
        Long origin = service.save(legalReq("f-orig"), "admin");
        Long copy = service.duplicate(origin, "admin");
        assertNotEquals(origin, copy);
        FieldFormulaDto o = service.getById(origin);
        FieldFormulaDto c = service.getById(copy);
        assertTrue(c.getName().startsWith("f-orig_copy_"));
        assertEquals(o.getFormulaJson(), c.getFormulaJson());
        assertEquals(o.getScene(), c.getScene());
    }

    @Test @Order(6)
    void 软删除公式_后续getById返回null() {
        Long id = service.save(legalReq("f-del"), "admin");
        service.delete(id);
        assertNull(service.getById(id));
    }

    @Test @Order(7)
    void 分页查询按场景过滤() {
        FormulaSaveRequest a = legalReq("f-scene-a"); a.setScene("A");
        FormulaSaveRequest b = legalReq("f-scene-b"); b.setScene("B");
        service.save(a, "admin");
        service.save(b, "admin");
        IPage<FieldFormulaDto> page = service.list("A", null, 1, 20);
        assertTrue(page.getRecords().stream().allMatch(f -> "A".equals(f.getScene())));
    }

    @Test @Order(8)
    void validate透传Validator结果() {
        FormulaValidateRequest req = new FormulaValidateRequest();
        req.setDbConnectionId(dbId);
        req.setFormulaJson("{broken");
        FormulaValidateResult r = service.validate(req);
        assertFalse(r.isOk());
    }
}
