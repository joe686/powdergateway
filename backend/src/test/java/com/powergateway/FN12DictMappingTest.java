package com.powergateway;

import com.powergateway.dao.DictMappingMapper;
import com.powergateway.model.DictMapping;
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
}
