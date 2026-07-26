package com.powergateway;

import com.powergateway.dao.DictMappingMapper;
import com.powergateway.model.DictMapping;
import com.powergateway.model.dto.DictMappingSaveRequest;
import com.powergateway.model.dto.DictMappingVO;
import com.powergateway.service.DictMappingService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class FN12DictMappingTest {

    @Autowired private DictMappingMapper dictMappingMapper;
    @Autowired private DictMappingService dictMappingService;

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
}
