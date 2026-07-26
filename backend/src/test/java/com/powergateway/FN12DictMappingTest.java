package com.powergateway;

import com.powergateway.dao.DictMappingMapper;
import com.powergateway.model.DictMapping;
import com.powergateway.model.dto.DictMappingSaveRequest;
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
}
