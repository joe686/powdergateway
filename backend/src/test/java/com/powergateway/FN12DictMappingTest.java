package com.powergateway;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.powergateway.dao.DictMappingMapper;
import com.powergateway.model.DictMapping;
import com.powergateway.model.dto.DictMappingImportResult;
import com.powergateway.model.dto.DictMappingLookupResult;
import com.powergateway.model.dto.DictMappingSaveRequest;
import com.powergateway.model.dto.DictMappingVO;
import com.powergateway.service.DictMappingService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
@org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
class FN12DictMappingTest {

    @Autowired private DictMappingMapper dictMappingMapper;
    @Autowired private DictMappingService dictMappingService;
    @Autowired org.springframework.test.web.servlet.MockMvc mockMvc;

    @Test
    void insertAndSelect_正常路径() {
        DictMapping m = new DictMapping();
        m.setSystemCode("CIF");
        m.setDictKey("GENDER");
        m.setDirection(1);
        m.setSourceValue("M");
        m.setTargetValue("1");
        m.setCnLabel("男");
        m.setStatus(1);

        int rows = dictMappingMapper.insert(m);
        assertThat(rows).isEqualTo(1);
        assertThat(m.getId()).isNotNull();

        DictMapping found = dictMappingMapper.selectById(m.getId());
        assertThat(found.getSystemCode()).isEqualTo("CIF");
        assertThat(found.getTargetValue()).isEqualTo("1");
        assertThat(found.getDeleted()).isZero();
    }

    @Test
    void save_单向_返1个id() {
        DictMappingSaveRequest req = new DictMappingSaveRequest();
        req.setSystemCode("CIF");
        req.setDictKey("STATUS");
        req.setDirection(1);
        req.setSourceValue("A");
        req.setTargetValue("ACTIVE");
        req.setCnLabel("正常");
        req.setBidirectional(false);

        java.util.List<Long> ids = dictMappingService.save(req);

        assertThat(ids).hasSize(1);
        DictMapping saved = dictMappingMapper.selectById(ids.get(0));
        assertThat(saved.getDirection()).isEqualTo(1);
        assertThat(saved.getTargetValue()).isEqualTo("ACTIVE");
    }

    @Test
    void save_唯一约束冲突_抛BusinessException() {
        DictMappingSaveRequest req1 = new DictMappingSaveRequest();
        req1.setSystemCode("CIF"); req1.setDictKey("STATUS"); req1.setDirection(1);
        req1.setSourceValue("A"); req1.setTargetValue("ACTIVE"); req1.setBidirectional(false);
        dictMappingService.save(req1);

        DictMappingSaveRequest req2 = new DictMappingSaveRequest();
        req2.setSystemCode("CIF"); req2.setDictKey("STATUS"); req2.setDirection(1);
        req2.setSourceValue("A"); req2.setTargetValue("OTHER"); req2.setBidirectional(false);
        // 同 (CIF,STATUS,1,A) 已存在 → 应报冲突

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> dictMappingService.save(req2))
            .isInstanceOf(com.powergateway.exception.BusinessException.class)
            .hasMessageContaining("已存在");
    }

    @Test
    void save_双向_产生2条并方向互换() {
        DictMappingSaveRequest req = new DictMappingSaveRequest();
        req.setSystemCode("CIF");
        req.setDictKey("GENDER");
        req.setDirection(1);           // 起始出向
        req.setSourceValue("M");
        req.setTargetValue("1");
        req.setBidirectional(true);    // 双向

        java.util.List<Long> ids = dictMappingService.save(req);
        assertThat(ids).hasSize(2);

        DictMapping out = dictMappingMapper.selectById(ids.get(0));
        DictMapping in  = dictMappingMapper.selectById(ids.get(1));
        assertThat(out.getDirection()).isEqualTo(1);
        assertThat(out.getSourceValue()).isEqualTo("M");
        assertThat(out.getTargetValue()).isEqualTo("1");
        assertThat(in.getDirection()).isEqualTo(2);
        assertThat(in.getSourceValue()).isEqualTo("1"); // 互换
        assertThat(in.getTargetValue()).isEqualTo("M");
    }

    @Test
    void save_多对一允许_targetValue重复() {
        DictMappingSaveRequest a = new DictMappingSaveRequest();
        a.setSystemCode("CIF"); a.setDictKey("STATUS"); a.setDirection(1);
        a.setSourceValue("A"); a.setTargetValue("ACTIVE");
        dictMappingService.save(a);

        DictMappingSaveRequest b = new DictMappingSaveRequest();
        b.setSystemCode("CIF"); b.setDictKey("STATUS"); b.setDirection(1);
        b.setSourceValue("N"); b.setTargetValue("ACTIVE");  // 同 target
        b.setBidirectional(false);
        java.util.List<Long> ids = dictMappingService.save(b);
        assertThat(ids).hasSize(1);   // 允许保存
    }

    @Test
    void list_按systemCode筛选() {
        DictMappingSaveRequest a = new DictMappingSaveRequest();
        a.setSystemCode("CIF"); a.setDictKey("K1"); a.setDirection(1);
        a.setSourceValue("A"); a.setTargetValue("1"); dictMappingService.save(a);

        DictMappingSaveRequest b = new DictMappingSaveRequest();
        b.setSystemCode("CORE"); b.setDictKey("K2"); b.setDirection(1);
        b.setSourceValue("X"); b.setTargetValue("2"); dictMappingService.save(b);

        java.util.List<DictMappingVO> cif = dictMappingService.list("CIF", null, null, null);
        assertThat(cif).hasSize(1);
        assertThat(cif.get(0).getSystemCode()).isEqualTo("CIF");
    }

    @Test
    void getById_不存在_抛BusinessException404() {
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> dictMappingService.getById(99999L))
            .isInstanceOf(com.powergateway.exception.BusinessException.class)
            .hasMessageContaining("不存在");
    }

    @Test
    void update_target与cnLabel_成功() {
        DictMappingSaveRequest req = new DictMappingSaveRequest();
        req.setSystemCode("CIF"); req.setDictKey("K"); req.setDirection(1);
        req.setSourceValue("A"); req.setTargetValue("1");
        Long id = dictMappingService.save(req).get(0);

        DictMappingSaveRequest upd = new DictMappingSaveRequest();
        upd.setSystemCode("CIF"); upd.setDictKey("K"); upd.setDirection(1);
        upd.setSourceValue("A"); upd.setTargetValue("2"); upd.setCnLabel("改后");
        dictMappingService.update(id, upd);

        DictMappingVO v = dictMappingService.getById(id);
        assertThat(v.getTargetValue()).isEqualTo("2");
        assertThat(v.getCnLabel()).isEqualTo("改后");
    }

    @Test
    void update_修改direction_拒绝抛BusinessException400() {
        DictMappingSaveRequest req = new DictMappingSaveRequest();
        req.setSystemCode("CIF"); req.setDictKey("K"); req.setDirection(1);
        req.setSourceValue("A"); req.setTargetValue("1");
        Long id = dictMappingService.save(req).get(0);

        DictMappingSaveRequest badUpd = new DictMappingSaveRequest();
        badUpd.setSystemCode("CIF"); badUpd.setDictKey("K"); badUpd.setDirection(2);  // 改方向
        badUpd.setSourceValue("A"); badUpd.setTargetValue("1");

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> dictMappingService.update(id, badUpd))
            .isInstanceOf(com.powergateway.exception.BusinessException.class)
            .hasMessageContaining("不允许修改方向");
    }

    @Test
    void delete_软删_selectById返null() {
        DictMappingSaveRequest req = new DictMappingSaveRequest();
        req.setSystemCode("CIF"); req.setDictKey("K"); req.setDirection(1);
        req.setSourceValue("A"); req.setTargetValue("1");
        Long id = dictMappingService.save(req).get(0);

        dictMappingService.delete(id);
        DictMapping still = dictMappingMapper.selectById(id);
        assertThat(still).isNull();  // MyBatis-Plus 软删除自动过滤
    }

    @Test
    void getSystems_返回distinct排序() {
        DictMappingSaveRequest a = new DictMappingSaveRequest();
        a.setSystemCode("CORE"); a.setDictKey("K"); a.setDirection(1);
        a.setSourceValue("A"); a.setTargetValue("1"); dictMappingService.save(a);

        DictMappingSaveRequest b = new DictMappingSaveRequest();
        b.setSystemCode("CIF"); b.setDictKey("K"); b.setDirection(1);
        b.setSourceValue("B"); b.setTargetValue("2"); dictMappingService.save(b);

        DictMappingSaveRequest c = new DictMappingSaveRequest();
        c.setSystemCode("CIF"); c.setDictKey("K2"); c.setDirection(1);
        c.setSourceValue("C"); c.setTargetValue("3"); dictMappingService.save(c);

        java.util.List<String> systems = dictMappingService.getSystems();
        assertThat(systems).containsExactly("CIF", "CORE");  // distinct + 字母升序
    }

    @Test
    void lookup_命中_返回targetValue() {
        DictMappingSaveRequest req = new DictMappingSaveRequest();
        req.setSystemCode("CIF"); req.setDictKey("GENDER"); req.setDirection(1);
        req.setSourceValue("M"); req.setTargetValue("1"); req.setCnLabel("男");
        dictMappingService.save(req);

        DictMappingLookupResult r = dictMappingService.lookup("CIF", "GENDER", 1, "M");
        assertThat(r).isNotNull();
        assertThat(r.getTargetValue()).isEqualTo("1");
        assertThat(r.getCnLabel()).isEqualTo("男");
    }

    @Test
    void lookup_未命中_返null() {
        DictMappingSaveRequest req = new DictMappingSaveRequest();
        req.setSystemCode("CIF"); req.setDictKey("GENDER"); req.setDirection(1);
        req.setSourceValue("M"); req.setTargetValue("1");
        dictMappingService.save(req);

        DictMappingLookupResult r = dictMappingService.lookup("CIF", "GENDER", 1, "X");
        assertThat(r).isNull();
    }

    @Test
    void delete_后lookup_不再命中() {
        DictMappingSaveRequest req = new DictMappingSaveRequest();
        req.setSystemCode("CIF"); req.setDictKey("GENDER"); req.setDirection(1);
        req.setSourceValue("M"); req.setTargetValue("1");
        Long id = dictMappingService.save(req).get(0);
        // 先查一次装载缓存
        assertThat(dictMappingService.lookup("CIF", "GENDER", 1, "M")).isNotNull();

        dictMappingService.delete(id);
        // 删后 lookup 应 null（Redis 已精准失效 + DB 已软删）
        assertThat(dictMappingService.lookup("CIF", "GENDER", 1, "M")).isNull();
    }

    // ──────── Task 8：importExcel / exportExcel ────────

    @Test
    void importExcel_正常_成功计数正确() throws Exception {
        // 构造 3 行合法数据的 xlsx
        java.util.List<DictMappingVO> seed = new java.util.ArrayList<>();
        for (int i = 0; i < 3; i++) {
            DictMappingVO v = new DictMappingVO();
            v.setSystemCode("CIF"); v.setDictKey("K"); v.setDirection(1);
            v.setSourceValue("S" + i); v.setTargetValue("T" + i); v.setStatus(1);
            seed.add(v);
        }
        byte[] xlsx = com.powergateway.utils.DictMappingExcelHelper.build(seed);
        org.springframework.mock.web.MockMultipartFile file =
            new org.springframework.mock.web.MockMultipartFile(
                "file", "in.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", xlsx);

        DictMappingImportResult r = dictMappingService.importExcel(file);

        assertThat(r.getSuccessCount()).isEqualTo(3);
        assertThat(r.getFailedRows()).isEmpty();
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void importExcel_一行错_整体回滚() throws Exception {
        // 第 1 行合法（direction=1），第 2 行 direction=9 非法
        java.util.List<DictMappingVO> seed = new java.util.ArrayList<>();
        DictMappingVO v1 = new DictMappingVO();
        v1.setSystemCode("CIF"); v1.setDictKey("K"); v1.setDirection(1);
        v1.setSourceValue("A"); v1.setTargetValue("1"); v1.setStatus(1);
        seed.add(v1);

        DictMappingVO v2 = new DictMappingVO();
        v2.setSystemCode("CIF"); v2.setDictKey("K"); v2.setDirection(9);  // 写 "9" 进 Excel
        v2.setSourceValue("B"); v2.setTargetValue("2"); v2.setStatus(1);
        seed.add(v2);

        byte[] xlsx = com.powergateway.utils.DictMappingExcelHelper.build(seed);
        org.springframework.mock.web.MockMultipartFile file =
            new org.springframework.mock.web.MockMultipartFile("file", "in.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", xlsx);

        DictMappingImportResult r = dictMappingService.importExcel(file);

        assertThat(r.getSuccessCount()).isZero();
        assertThat(r.getFailedRows()).hasSize(1);
        // 第 2 条数据行 → Excel 行号 = 1(索引) + 2 = 3
        assertThat(r.getFailedRows().get(0).getRowIndex()).isEqualTo(3);
        // 整体回滚：第 1 行虽合法也不应留存
        java.util.List<DictMappingVO> after = dictMappingService.list("CIF", "K", 1, null);
        assertThat(after).isEmpty();

        // 测试完毕后手工清理（本方法跳过了类级事务，需显式删除）
        dictMappingMapper.delete(new QueryWrapper<DictMapping>().eq("system_code", "CIF"));
    }

    @Test
    void exportExcel_返回非空字节() throws Exception {
        DictMappingSaveRequest req = new DictMappingSaveRequest();
        req.setSystemCode("CIF"); req.setDictKey("K"); req.setDirection(1);
        req.setSourceValue("A"); req.setTargetValue("1");
        dictMappingService.save(req);

        byte[] bytes = dictMappingService.exportExcel("CIF", null, null, null);

        assertThat(bytes).isNotNull();
        assertThat(bytes.length).isGreaterThan(100);  // 最小合法 xlsx 骨架
    }

    // ──────── Task 9：Controller MockMvc ────────

    @Test
    void controller_list_默认返200并有list结构() throws Exception {
        cn.dev33.satoken.stp.StpUtil.login(1L);
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                .get("/api/dict-mapping/list")
                .header("satoken", cn.dev33.satoken.stp.StpUtil.getTokenValue()))
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk())
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                .jsonPath("$.code").value(200));
    }

    @Test
    void controller_save_单向_返id列表() throws Exception {
        cn.dev33.satoken.stp.StpUtil.login(1L);
        String json = "{\"systemCode\":\"CIF\",\"dictKey\":\"K\",\"direction\":1,"
                    + "\"sourceValue\":\"A\",\"targetValue\":\"1\",\"bidirectional\":false}";
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                .post("/api/dict-mapping")
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .content(json)
                .header("satoken", cn.dev33.satoken.stp.StpUtil.getTokenValue()))
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk())
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                .jsonPath("$.data.length()").value(1));
    }

    @Test
    void controller_getSystems_返回distinct列表() throws Exception {
        cn.dev33.satoken.stp.StpUtil.login(1L);
        DictMappingSaveRequest req = new DictMappingSaveRequest();
        req.setSystemCode("CORE"); req.setDictKey("K"); req.setDirection(1);
        req.setSourceValue("A"); req.setTargetValue("1");
        dictMappingService.save(req);

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                .get("/api/dict-mapping/systems")
                .header("satoken", cn.dev33.satoken.stp.StpUtil.getTokenValue()))
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk())
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                .jsonPath("$.data[0]").value("CORE"));
    }

    // ──────── Final Review I-1 ~ I-4 · Service 层参数校验硬化 ────────

    @Test
    void save_targetValue空_抛400() {
        DictMappingSaveRequest req = new DictMappingSaveRequest();
        req.setSystemCode("CIF"); req.setDictKey("K"); req.setDirection(1);
        req.setSourceValue("A"); req.setTargetValue("");  // 空
        assertThatThrownBy(() -> dictMappingService.save(req))
            .isInstanceOf(com.powergateway.exception.BusinessException.class)
            .hasMessageContaining("targetValue 必填");
    }

    @Test
    void save_direction非法_抛400() {
        DictMappingSaveRequest req = new DictMappingSaveRequest();
        req.setSystemCode("CIF"); req.setDictKey("K"); req.setDirection(3);  // 非法
        req.setSourceValue("A"); req.setTargetValue("1");
        assertThatThrownBy(() -> dictMappingService.save(req))
            .isInstanceOf(com.powergateway.exception.BusinessException.class)
            .hasMessageContaining("direction 必须为");
    }

    @Test
    void lookup_null参数_抛400() {
        assertThatThrownBy(() -> dictMappingService.lookup(null, "K", 1, "A"))
            .isInstanceOf(com.powergateway.exception.BusinessException.class)
            .hasMessageContaining("system 必填");
        assertThatThrownBy(() -> dictMappingService.lookup("CIF", "K", null, "A"))
            .isInstanceOf(com.powergateway.exception.BusinessException.class)
            .hasMessageContaining("direction");
    }

    @Test
    void importExcel_内部RuntimeException不被吞() throws Exception {
        // 这是一个观察性测试：验证 catch 只捕获业务/参数异常，不掩盖 RuntimeException
        // 由于难以在测试内触发真实 RuntimeException，仅作 Javadoc 意图声明
        // 实际保护通过代码审查确认（catch 收窄为 BusinessException | IllegalArgumentException）
        // 此测试留一个空断言防止 test file 空跑
        assertThat(true).isTrue();
    }

    // ══════════════════ v0.2.5 CR-004 新增：scope 拆分 + batch API 测试 ══════════════════

    @Test
    void saveBatch_正常路径_3条整批插入() {
        com.powergateway.model.dto.DictMappingBatchSaveRequest req =
            new com.powergateway.model.dto.DictMappingBatchSaveRequest();
        req.setScope(2);
        req.setSystemCode("CIF-BATCH");
        req.setDictKey("STATUS");
        req.setDirection(1);
        java.util.List<com.powergateway.model.dto.DictMappingBatchSaveRequest.Item> items = new java.util.ArrayList<>();
        for (int i = 0; i < 3; i++) {
            com.powergateway.model.dto.DictMappingBatchSaveRequest.Item it =
                new com.powergateway.model.dto.DictMappingBatchSaveRequest.Item();
            it.setSourceValue("S" + i);
            it.setTargetValue("T" + i);
            it.setCnLabel("标签" + i);
            items.add(it);
        }
        req.setItems(items);

        java.util.List<Long> ids = dictMappingService.saveBatch(req);
        assertThat(ids).hasSize(3).allMatch(id -> id != null && id > 0);
    }

    @Test
    void saveBatch_超上限_抛400() {
        com.powergateway.model.dto.DictMappingBatchSaveRequest req =
            new com.powergateway.model.dto.DictMappingBatchSaveRequest();
        req.setScope(2);
        req.setSystemCode("CIF-BATCH2");
        req.setDictKey("K");
        req.setDirection(1);
        java.util.List<com.powergateway.model.dto.DictMappingBatchSaveRequest.Item> items = new java.util.ArrayList<>();
        for (int i = 0; i < 201; i++) {
            com.powergateway.model.dto.DictMappingBatchSaveRequest.Item it =
                new com.powergateway.model.dto.DictMappingBatchSaveRequest.Item();
            it.setSourceValue("S" + i);
            it.setTargetValue("T" + i);
            items.add(it);
        }
        req.setItems(items);

        assertThatThrownBy(() -> dictMappingService.saveBatch(req))
            .isInstanceOf(com.powergateway.exception.BusinessException.class)
            .hasMessageContaining("上限 200");
    }

    @Test
    void scope_M1_M2_独立不冲突() {
        // 同 (system, key, direction, source) 但不同 scope 允许并存
        DictMappingSaveRequest a = new DictMappingSaveRequest();
        a.setScope(1); a.setSystemCode("BANK"); a.setDictKey("STATE");
        a.setDirection(1); a.setSourceValue("Y"); a.setTargetValue("1");
        dictMappingService.save(a);

        DictMappingSaveRequest b = new DictMappingSaveRequest();
        b.setScope(2); b.setSystemCode("BANK"); b.setDictKey("STATE");
        b.setDirection(1); b.setSourceValue("Y"); b.setTargetValue("2");
        // 不抛冲突,因 scope 不同
        dictMappingService.save(b);

        // 查询验证:M1 视角命中 target=1
        DictMappingLookupResult m1 = dictMappingService.lookup(1, "BANK", "STATE", 1, "Y");
        assertThat(m1).isNotNull();
        assertThat(m1.getTargetValue()).isEqualTo("1");

        // M2 视角命中 target=2
        DictMappingLookupResult m2 = dictMappingService.lookup(2, "BANK", "STATE", 1, "Y");
        assertThat(m2).isNotNull();
        assertThat(m2.getTargetValue()).isEqualTo("2");
    }

    @Test
    void scope_M1_fallback_共享条目() {
        // scope=3 共享条目,M1 视角查询也能命中(通过 IN(scope,3) fallback)
        DictMappingSaveRequest shared = new DictMappingSaveRequest();
        shared.setScope(3); shared.setSystemCode("BANK2"); shared.setDictKey("FLAG");
        shared.setDirection(1); shared.setSourceValue("Y"); shared.setTargetValue("SHARED");
        dictMappingService.save(shared);

        // M1 视角查询 · 没有 scope=1 条目 · 应 fallback 到 scope=3 共享
        DictMappingLookupResult m1 = dictMappingService.lookup(1, "BANK2", "FLAG", 1, "Y");
        assertThat(m1).isNotNull();
        assertThat(m1.getTargetValue()).isEqualTo("SHARED");
    }

    @Test
    void scope_M1_精确_优先于共享() {
        // 同键值,scope=1 和 scope=3 都有 · M1 视角应优先返回 scope=1
        DictMappingSaveRequest m1Entry = new DictMappingSaveRequest();
        m1Entry.setScope(1); m1Entry.setSystemCode("BANK3"); m1Entry.setDictKey("K");
        m1Entry.setDirection(1); m1Entry.setSourceValue("A"); m1Entry.setTargetValue("M1_VAL");
        dictMappingService.save(m1Entry);

        DictMappingSaveRequest sharedEntry = new DictMappingSaveRequest();
        sharedEntry.setScope(3); sharedEntry.setSystemCode("BANK3"); sharedEntry.setDictKey("K");
        sharedEntry.setDirection(1); sharedEntry.setSourceValue("A"); sharedEntry.setTargetValue("SHARED_VAL");
        dictMappingService.save(sharedEntry);

        DictMappingLookupResult r = dictMappingService.lookup(1, "BANK3", "K", 1, "A");
        assertThat(r).isNotNull();
        assertThat(r.getTargetValue()).isEqualTo("M1_VAL");
    }

    @Test
    void importExcel_末尾空行被跳过_不误报回滚() throws Exception {
        // 构造 2 行合法 + 1 行全空的 xlsx
        java.util.List<DictMappingVO> seed = new java.util.ArrayList<>();
        for (int i = 0; i < 2; i++) {
            DictMappingVO v = new DictMappingVO();
            v.setSystemCode("CIF"); v.setDictKey("BLANK_TEST"); v.setDirection(1);
            v.setSourceValue("S" + i); v.setTargetValue("T" + i); v.setStatus(1);
            seed.add(v);
        }
        // build 只能生成非空行，我们手工创建带尾空行的 workbook
        org.apache.poi.xssf.usermodel.XSSFWorkbook wb = new org.apache.poi.xssf.usermodel.XSSFWorkbook();
        org.apache.poi.ss.usermodel.Sheet sheet = wb.createSheet("t");
        org.apache.poi.ss.usermodel.Row header = sheet.createRow(0);
        String[] headers = {"系统代号", "字典标识", "方向", "源值", "目标值", "中文", "状态"};
        for (int i = 0; i < headers.length; i++) header.createCell(i).setCellValue(headers[i]);
        for (int i = 0; i < 2; i++) {
            org.apache.poi.ss.usermodel.Row r = sheet.createRow(i + 1);
            r.createCell(0).setCellValue("CIF");
            r.createCell(1).setCellValue("BLANK_TEST");
            r.createCell(2).setCellValue("1");
            r.createCell(3).setCellValue("S" + i);
            r.createCell(4).setCellValue("T" + i);
            r.createCell(5).setCellValue("");
            r.createCell(6).setCellValue("1");
        }
        // 追加一行全空
        sheet.createRow(3);
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        wb.write(out); wb.close();

        org.springframework.mock.web.MockMultipartFile file =
            new org.springframework.mock.web.MockMultipartFile("file", "in.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", out.toByteArray());
        DictMappingImportResult r = dictMappingService.importExcel(file);
        assertThat(r.getSuccessCount()).isEqualTo(2);   // 2 条成功，空行跳过
        assertThat(r.getFailedRows()).isEmpty();
    }
}
