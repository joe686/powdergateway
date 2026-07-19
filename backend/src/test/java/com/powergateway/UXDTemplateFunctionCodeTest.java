package com.powergateway;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.powergateway.model.ConvertTemplate;
import com.powergateway.model.dto.TemplateQueryRequest;
import com.powergateway.model.dto.TemplateSaveRequest;
import com.powergateway.service.TemplateService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * UX-D Task 4：convert_template.function_code 字段测试
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class UXDTemplateFunctionCodeTest {

    @Autowired
    TemplateService svc;

    private Long saveTemplate(String name, String functionCode) {
        TemplateSaveRequest req = new TemplateSaveRequest();
        req.setName(name);
        req.setSrcFormat("JSON");
        req.setTargetFormat("JSON");
        req.setFunctionCode(functionCode);
        return svc.saveTemplate(req);
    }

    @Test
    void listTemplates_按functionCode过滤_仅返回匹配模板() {
        saveTemplate("TPL_FC_A", "FC_A");
        saveTemplate("TPL_FC_B", "FC_B");
        saveTemplate("TPL_FC_NULL", null);

        TemplateQueryRequest req = new TemplateQueryRequest();
        req.setPage(1);
        req.setSize(100);
        req.setLatestOnly(false);
        req.setFunctionCode("FC_A");
        Page<ConvertTemplate> page = svc.listTemplates(req);
        assertEquals(1, page.getRecords().size());
        assertEquals("FC_A", page.getRecords().get(0).getFunctionCode());
    }

    @Test
    void listTemplates_functionCode为空_返回全部() {
        saveTemplate("TPL_ALL_A", "FC_ALL_A");
        saveTemplate("TPL_ALL_B", "FC_ALL_B");

        TemplateQueryRequest req = new TemplateQueryRequest();
        req.setPage(1);
        req.setSize(100);
        req.setLatestOnly(false);
        req.setFunctionCode(null);
        Page<ConvertTemplate> page = svc.listTemplates(req);
        // 至少有刚插入的 2 条
        assertTrue(page.getRecords().size() >= 2);
    }
}
