package com.powergateway;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.powergateway.model.InterfaceConfig;
import com.powergateway.model.dto.DeleteConfigJson;
import com.powergateway.model.dto.InsertConfigJson;
import com.powergateway.model.dto.QueryConfigJson;
import com.powergateway.model.dto.UpdateConfigJson;
import com.powergateway.service.codec.ExcelConfigCodec;
import com.powergateway.service.codec.IncompatibleSchemaException;
import com.powergateway.service.codec.InvalidExcelStructureException;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.SheetVisibility;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.ActiveProfiles;

import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * FN-11 Task 2 · ExcelConfigCodec 接口配置 ↔ xlsx 双向编解码测试
 *
 * 本轮只覆盖 QUERY (SELECT) 类型；INSERT/UPDATE/DELETE 下轮扩展。
 */
@ActiveProfiles("test")
class FN11ExcelConfigCodecTest {

    private ObjectMapper objectMapper;
    private ExcelConfigCodec codec;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        codec = new ExcelConfigCodec(objectMapper);
    }

    // ============ QUERY encode ============

    @Test
    void encode_QUERY_单表_能生成合法xlsx() throws Exception {
        InterfaceConfig cfg = buildSingleTableQueryConfig();
        byte[] bytes = codec.encode(cfg);
        assertThat(bytes).isNotEmpty();

        try (Workbook wb = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
            assertThat(wb.getSheet("元数据")).as("元数据 sheet 必须存在").isNotNull();
            assertThat(wb.getSheet("表配置")).as("表配置 sheet 必须存在").isNotNull();
            assertThat(wb.getSheet("字段列表")).as("字段列表 sheet 必须存在").isNotNull();
            assertThat(wb.getSheet("条件配置")).as("条件配置 sheet 必须存在").isNotNull();

            Sheet meta = wb.getSheet("_meta");
            assertThat(meta).as("_meta sheet 必须存在").isNotNull();
            assertThat(wb.getSheetVisibility(wb.getSheetIndex(meta)))
                    .as("_meta sheet 应隐藏")
                    .isEqualTo(SheetVisibility.HIDDEN);
        }
    }

    @Test
    void encode_元数据sheet_首行是表头() throws Exception {
        byte[] bytes = codec.encode(buildSingleTableQueryConfig());
        try (Workbook wb = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
            Sheet meta = wb.getSheet("元数据");
            assertThat(meta.getRow(0).getCell(0).getStringCellValue()).isEqualTo("字段编码");
            assertThat(meta.getRow(0).getCell(1).getStringCellValue()).isEqualTo("字段名称");
            assertThat(meta.getRow(0).getCell(2).getStringCellValue()).isEqualTo("值");
        }
    }

    @Test
    void encode_QUERY_缓存开启_元数据sheet含缓存字段() throws Exception {
        InterfaceConfig cfg = buildSingleTableQueryConfig();
        cfg.setCacheEnabled(1);
        cfg.setCacheTtlSeconds(300);
        cfg.setCacheKeyTemplate("user:{userId}");

        byte[] bytes = codec.encode(cfg);
        try (Workbook wb = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
            Sheet meta = wb.getSheet("元数据");
            boolean hasCacheEnabled = false, hasTtl = false, hasKey = false;
            for (int i = 1; i <= meta.getLastRowNum(); i++) {
                String code = meta.getRow(i).getCell(0).getStringCellValue();
                if ("cacheEnabled".equals(code)) hasCacheEnabled = true;
                if ("cacheTtlSeconds".equals(code)) hasTtl = true;
                if ("cacheKeyTemplate".equals(code)) hasKey = true;
            }
            assertThat(hasCacheEnabled).isTrue();
            assertThat(hasTtl).isTrue();
            assertThat(hasKey).isTrue();
        }
    }

    // ============ QUERY 往返 ============

    @Test
    void encode_decode_QUERY_单表_configJson语义等价() throws Exception {
        InterfaceConfig cfg = buildSingleTableQueryConfig();
        byte[] bytes = codec.encode(cfg);
        InterfaceConfig back = codec.decode(new ByteArrayInputStream(bytes));

        assertThat(back.getName()).isEqualTo(cfg.getName());
        assertThat(back.getType()).isEqualTo(cfg.getType());
        assertThat(back.getDbConnectionId()).isEqualTo(cfg.getDbConnectionId());

        QueryConfigJson origQuery = objectMapper.readValue(cfg.getConfigJson(), QueryConfigJson.class);
        QueryConfigJson backQuery = objectMapper.readValue(back.getConfigJson(), QueryConfigJson.class);
        assertThat(backQuery.getTables()).hasSameSizeAs(origQuery.getTables());
        assertThat(backQuery.getFields()).hasSameSizeAs(origQuery.getFields());
        assertThat(backQuery.getConditions()).hasSameSizeAs(origQuery.getConditions());

        assertThat(backQuery.getTables().get(0).getName()).isEqualTo("sys_user");
        assertThat(backQuery.getTables().get(0).getAlias()).isEqualTo("u");
        assertThat(backQuery.getFields().get(0).getColumn()).isEqualTo("id");
        assertThat(backQuery.getFields().get(0).getAlias()).isEqualTo("userId");
        assertThat(backQuery.getConditions().get(0).getField()).isEqualTo("u.username");
        assertThat(backQuery.getConditions().get(0).getOp()).isEqualTo("EQ");
        assertThat(backQuery.getConditions().get(0).getParamKey()).isEqualTo("username");
    }

    @Test
    void encode_decode_QUERY_多表JOIN_configJson语义等价() throws Exception {
        InterfaceConfig cfg = buildJoinQueryConfig();
        byte[] bytes = codec.encode(cfg);
        InterfaceConfig back = codec.decode(new ByteArrayInputStream(bytes));

        QueryConfigJson q = objectMapper.readValue(back.getConfigJson(), QueryConfigJson.class);
        assertThat(q.getTables()).hasSize(2);
        assertThat(q.getJoins()).hasSize(1);
        assertThat(q.getJoins().get(0).getLeftTable()).isEqualTo("u");
        assertThat(q.getJoins().get(0).getRightTable()).isEqualTo("ic");
        assertThat(q.getJoins().get(0).getType()).isEqualTo("LEFT");
    }

    @Test
    void encode_decode_QUERY_缓存字段_往返一致() throws Exception {
        InterfaceConfig cfg = buildSingleTableQueryConfig();
        cfg.setCacheEnabled(1);
        cfg.setCacheTtlSeconds(600);
        cfg.setCacheKeyTemplate("query:{id}");

        byte[] bytes = codec.encode(cfg);
        InterfaceConfig back = codec.decode(new ByteArrayInputStream(bytes));
        assertThat(back.getCacheEnabled()).isEqualTo(1);
        assertThat(back.getCacheTtlSeconds()).isEqualTo(600);
        assertThat(back.getCacheKeyTemplate()).isEqualTo("query:{id}");
    }

    // ============ INSERT 往返 ============

    @Test
    void encode_decode_INSERT_三种数据来源_往返一致() throws Exception {
        InterfaceConfig cfg = buildInsertConfig();
        byte[] bytes = codec.encode(cfg);
        InterfaceConfig back = codec.decode(new ByteArrayInputStream(bytes));

        assertThat(back.getType()).isEqualTo("INSERT");
        InsertConfigJson orig = objectMapper.readValue(cfg.getConfigJson(), InsertConfigJson.class);
        InsertConfigJson roundTrip = objectMapper.readValue(back.getConfigJson(), InsertConfigJson.class);
        assertThat(roundTrip.getTables()).hasSize(1);
        assertThat(roundTrip.getTables().get(0).getTableName()).isEqualTo("m24_order");
        assertThat(roundTrip.getTables().get(0).getFields()).hasSize(3);

        InsertConfigJson.FieldInsertConfig f1 = roundTrip.getTables().get(0).getFields().get(0);
        assertThat(f1.getColumn()).isEqualTo("user_id");
        assertThat(f1.getSourceType()).isEqualTo("REQUEST");
        assertThat(f1.getParamKey()).isEqualTo("userId");

        InsertConfigJson.FieldInsertConfig f2 = roundTrip.getTables().get(0).getFields().get(1);
        assertThat(f2.getColumn()).isEqualTo("status");
        assertThat(f2.getSourceType()).isEqualTo("CONST");
        assertThat(f2.getConstValue()).isEqualTo("PENDING");

        InsertConfigJson.FieldInsertConfig f3 = roundTrip.getTables().get(0).getFields().get(2);
        assertThat(f3.getColumn()).isEqualTo("total_price");
        assertThat(f3.getSourceType()).isEqualTo("CALC");
        assertThat(f3.getExpression()).isEqualTo("price*quantity");
    }

    @Test
    void encode_INSERT_数据来源sheet存在_元数据sheet无cache字段() throws Exception {
        byte[] bytes = codec.encode(buildInsertConfig());
        try (Workbook wb = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
            assertThat(wb.getSheet("数据来源")).isNotNull();
            assertThat(wb.getSheet("字段列表")).as("INSERT 不用字段列表 sheet").isNull();
        }
    }

    // ============ UPDATE 往返 ============

    @Test
    void encode_decode_UPDATE_多表条件_往返一致() throws Exception {
        InterfaceConfig cfg = buildUpdateConfig();
        byte[] bytes = codec.encode(cfg);
        InterfaceConfig back = codec.decode(new ByteArrayInputStream(bytes));

        assertThat(back.getType()).isEqualTo("UPDATE");
        UpdateConfigJson roundTrip = objectMapper.readValue(back.getConfigJson(), UpdateConfigJson.class);
        assertThat(roundTrip.getTables()).hasSize(1);
        assertThat(roundTrip.getTables().get(0).getTableName()).isEqualTo("m25_product");
        assertThat(roundTrip.getTables().get(0).getFields()).hasSize(1);
        assertThat(roundTrip.getTables().get(0).getFields().get(0).getColumn()).isEqualTo("category");

        assertThat(roundTrip.getConditions()).hasSize(1);
        assertThat(roundTrip.getConditions().get(0).getTableName()).isEqualTo("m25_product");
        assertThat(roundTrip.getConditions().get(0).getField()).isEqualTo("id");
        assertThat(roundTrip.getConditions().get(0).getOp()).isEqualTo("EQ");
        assertThat(roundTrip.getConditions().get(0).getParamKey()).isEqualTo("productId");
    }

    // ============ DELETE 往返 ============

    @Test
    void encode_decode_DELETE_每表独立条件_往返一致() throws Exception {
        InterfaceConfig cfg = buildDeleteConfig();
        byte[] bytes = codec.encode(cfg);
        InterfaceConfig back = codec.decode(new ByteArrayInputStream(bytes));

        assertThat(back.getType()).isEqualTo("DELETE");
        assertThat(back.getAllowBatchDelete()).isEqualTo(1);
        DeleteConfigJson roundTrip = objectMapper.readValue(back.getConfigJson(), DeleteConfigJson.class);
        assertThat(roundTrip.getTables()).hasSize(1);
        assertThat(roundTrip.getTables().get(0).getTableName()).isEqualTo("m26_log");
        assertThat(roundTrip.getTables().get(0).getConditions()).hasSize(1);
        DeleteConfigJson.ConditionItem c = roundTrip.getTables().get(0).getConditions().get(0);
        assertThat(c.getField()).isEqualTo("created_at");
        assertThat(c.getOp()).isEqualTo("LT");
        assertThat(c.getParamKey()).isEqualTo("cutoffDate");
    }

    @Test
    void encode_DELETE_无数据来源sheet_有条件配置sheet() throws Exception {
        byte[] bytes = codec.encode(buildDeleteConfig());
        try (Workbook wb = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
            assertThat(wb.getSheet("条件配置")).isNotNull();
            assertThat(wb.getSheet("数据来源")).as("DELETE 不用数据来源 sheet").isNull();
            assertThat(wb.getSheet("字段列表")).as("DELETE 不用字段列表 sheet").isNull();
        }
    }

    // ============ 校验异常 ============

    @Test
    void decode_schemaVersion不匹配_抛IncompatibleSchemaException() throws Exception {
        byte[] tamperedBytes = tamperSchemaVersion(codec.encode(buildSingleTableQueryConfig()), "99");
        assertThatThrownBy(() -> codec.decode(new ByteArrayInputStream(tamperedBytes)))
                .isInstanceOf(IncompatibleSchemaException.class)
                .hasMessageContaining("99");
    }

    @Test
    void decode_缺失元数据sheet_抛InvalidExcelStructureException() throws Exception {
        byte[] bytes = codec.encode(buildSingleTableQueryConfig());
        byte[] stripped = removeSheet(bytes, "元数据");
        assertThatThrownBy(() -> codec.decode(new ByteArrayInputStream(stripped)))
                .isInstanceOf(InvalidExcelStructureException.class)
                .hasMessageContaining("元数据");
    }

    // ============ 辅助构造器 ============

    private InterfaceConfig buildSingleTableQueryConfig() throws Exception {
        QueryConfigJson q = new QueryConfigJson();
        QueryConfigJson.TableDef t = new QueryConfigJson.TableDef();
        t.setName("sys_user");
        t.setAlias("u");
        q.setTables(new ArrayList<>(Collections.singletonList(t)));

        QueryConfigJson.FieldDef f1 = new QueryConfigJson.FieldDef();
        f1.setTable("u"); f1.setColumn("id"); f1.setAlias("userId");
        QueryConfigJson.FieldDef f2 = new QueryConfigJson.FieldDef();
        f2.setTable("u"); f2.setColumn("username"); f2.setAlias("username");
        q.setFields(new ArrayList<>(Arrays.asList(f1, f2)));

        QueryConfigJson.ConditionDef c = new QueryConfigJson.ConditionDef();
        c.setField("u.username"); c.setOp("EQ"); c.setParamKey("username");
        q.setConditions(new ArrayList<>(Collections.singletonList(c)));

        q.setJoins(new ArrayList<>());
        q.setProcessRules(new ArrayList<>());

        InterfaceConfig cfg = new InterfaceConfig();
        cfg.setId(101L);
        cfg.setName("按用户名查用户");
        cfg.setPath("/api/exec/101");
        cfg.setType("SELECT");
        cfg.setDbConnectionId(1L);
        cfg.setStatus("published");
        cfg.setResponseFormat("JSON");
        cfg.setConfigJson(objectMapper.writeValueAsString(q));
        return cfg;
    }

    private InterfaceConfig buildJoinQueryConfig() throws Exception {
        QueryConfigJson q = new QueryConfigJson();
        QueryConfigJson.TableDef t1 = new QueryConfigJson.TableDef();
        t1.setName("sys_user"); t1.setAlias("u");
        QueryConfigJson.TableDef t2 = new QueryConfigJson.TableDef();
        t2.setName("interface_config"); t2.setAlias("ic");
        q.setTables(new ArrayList<>(Arrays.asList(t1, t2)));

        QueryConfigJson.JoinDef j = new QueryConfigJson.JoinDef();
        j.setLeftTable("u"); j.setLeftCol("id");
        j.setRightTable("ic"); j.setRightCol("db_connection_id");
        j.setType("LEFT");
        q.setJoins(new ArrayList<>(Collections.singletonList(j)));

        QueryConfigJson.FieldDef f = new QueryConfigJson.FieldDef();
        f.setTable("u"); f.setColumn("id"); f.setAlias("userId");
        q.setFields(new ArrayList<>(Collections.singletonList(f)));

        q.setConditions(new ArrayList<>());
        q.setProcessRules(new ArrayList<>());

        InterfaceConfig cfg = new InterfaceConfig();
        cfg.setName("用户+接口 JOIN 查");
        cfg.setType("SELECT");
        cfg.setDbConnectionId(1L);
        cfg.setStatus("draft");
        cfg.setConfigJson(objectMapper.writeValueAsString(q));
        return cfg;
    }

    private InterfaceConfig buildInsertConfig() throws Exception {
        InsertConfigJson insertConfig = new InsertConfigJson();
        InsertConfigJson.TableInsertConfig t = new InsertConfigJson.TableInsertConfig();
        t.setTableName("m24_order");

        InsertConfigJson.FieldInsertConfig f1 = new InsertConfigJson.FieldInsertConfig();
        f1.setColumn("user_id");
        f1.setSourceType("REQUEST");
        f1.setParamKey("userId");

        InsertConfigJson.FieldInsertConfig f2 = new InsertConfigJson.FieldInsertConfig();
        f2.setColumn("status");
        f2.setSourceType("CONST");
        f2.setConstValue("PENDING");

        InsertConfigJson.FieldInsertConfig f3 = new InsertConfigJson.FieldInsertConfig();
        f3.setColumn("total_price");
        f3.setSourceType("CALC");
        f3.setExpression("price*quantity");

        t.setFields(new ArrayList<>(Arrays.asList(f1, f2, f3)));
        insertConfig.setTables(new ArrayList<>(Collections.singletonList(t)));

        InterfaceConfig cfg = new InterfaceConfig();
        cfg.setName("新增订单");
        cfg.setType("INSERT");
        cfg.setDbConnectionId(1L);
        cfg.setStatus("draft");
        cfg.setConfigJson(objectMapper.writeValueAsString(insertConfig));
        return cfg;
    }

    private InterfaceConfig buildUpdateConfig() throws Exception {
        UpdateConfigJson updateConfig = new UpdateConfigJson();
        UpdateConfigJson.TableUpdateConfig t = new UpdateConfigJson.TableUpdateConfig();
        t.setTableName("m25_product");

        InsertConfigJson.FieldInsertConfig f = new InsertConfigJson.FieldInsertConfig();
        f.setColumn("category");
        f.setSourceType("REQUEST");
        f.setParamKey("category");
        t.setFields(new ArrayList<>(Collections.singletonList(f)));

        UpdateConfigJson.ConditionConfig c = new UpdateConfigJson.ConditionConfig();
        c.setTableName("m25_product");
        c.setField("id");
        c.setOp("EQ");
        c.setParamKey("productId");

        updateConfig.setTables(new ArrayList<>(Collections.singletonList(t)));
        updateConfig.setConditions(new ArrayList<>(Collections.singletonList(c)));

        InterfaceConfig cfg = new InterfaceConfig();
        cfg.setName("改商品分类");
        cfg.setType("UPDATE");
        cfg.setDbConnectionId(1L);
        cfg.setStatus("draft");
        cfg.setConfigJson(objectMapper.writeValueAsString(updateConfig));
        return cfg;
    }

    private InterfaceConfig buildDeleteConfig() throws Exception {
        DeleteConfigJson deleteConfig = new DeleteConfigJson();
        DeleteConfigJson.TableDeleteConfig t = new DeleteConfigJson.TableDeleteConfig();
        t.setTableName("m26_log");

        DeleteConfigJson.ConditionItem c = new DeleteConfigJson.ConditionItem();
        c.setField("created_at");
        c.setOp("LT");
        c.setParamKey("cutoffDate");
        t.setConditions(new ArrayList<>(Collections.singletonList(c)));

        deleteConfig.setTables(new ArrayList<>(Collections.singletonList(t)));

        InterfaceConfig cfg = new InterfaceConfig();
        cfg.setName("清理日志");
        cfg.setType("DELETE");
        cfg.setDbConnectionId(1L);
        cfg.setStatus("draft");
        cfg.setAllowBatchDelete(1);
        cfg.setConfigJson(objectMapper.writeValueAsString(deleteConfig));
        return cfg;
    }

    private byte[] tamperSchemaVersion(byte[] bytes, String badVersion) throws Exception {
        try (Workbook wb = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
            Sheet meta = wb.getSheet("_meta");
            for (int i = 0; i <= meta.getLastRowNum(); i++) {
                if ("schemaVersion".equals(meta.getRow(i).getCell(0).getStringCellValue())) {
                    meta.getRow(i).getCell(1).setCellValue(badVersion);
                    break;
                }
            }
            java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
            wb.write(out);
            return out.toByteArray();
        }
    }

    private byte[] removeSheet(byte[] bytes, String sheetName) throws Exception {
        try (Workbook wb = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
            int idx = wb.getSheetIndex(sheetName);
            if (idx >= 0) wb.removeSheetAt(idx);
            java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
            wb.write(out);
            return out.toByteArray();
        }
    }
}
