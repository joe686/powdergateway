# UX-E · 可视化接口扩展 · 实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 为 PowerGateway 已发布可视化接口/已配置转换模板补齐 6 项最后一公里能力（多报文格式响应、字段清单 Excel、报文调试 XML 高亮、接口文档下载、报表下载、配置导入导出），复用 M1-1 `FormatConverter`、M2-2 POI Excel、SpringDoc，不引入 PDF/Word 库。

**Architecture:** 6 子项按 FN-06→FN-07→FN-10→FN-08→FN-09→FN-11 顺序分批交付；`ExecController` 前置 `AcceptNegotiator` 做 Accept/query 协商，非 JSON 分支包装成 `ResponseEntity<String>`，JSON 分支保留 `Result<T>` 原语义；新增 `ExcelExportUtil`（POI 5.2.3 `XSSFWorkbook`）供 FN-07 字段清单和 FN-10 九个列表页共用；导入导出使用 JDK `ZipOutputStream` 打包 `manifest.json + JSON 条目文件`，导入按主键三元组/name 判定，`OVERWRITE/SKIP/ASK` 三策略、整体事务；`interface_config` 新增 `response_format` 与 `response_headers` 两列走幂等 `migration-response-format.sql`。

**Tech Stack:** Spring Boot 2.7.18, MyBatis-Plus 3.5.7, POI 5.2.3（已装）, Jackson / jackson-dataformat-xml / Dom4j 2.1.4 / OpenCSV 5.9（已装）, Sa-Token 1.37.0, springdoc-openapi-ui 1.7.0, JDK 8 `ZipOutputStream`, Vue 3 + Vite, Element Plus, CodeMirror 5.65.x + vue-codemirror 6.x（**新增前端依赖，仅 FN-08 用**）

## Global Constraints

- **语言**：所有代码注释、文档、提交信息一律**中文**；对话回复中文
- **前端请求链路**：严格使用 `frontend/src/api/request.js` 统一封装，禁止直接 `axios` 或 `fetch`
- **拖拽组件**：本单元不新增拖拽功能；若涉及既有拖拽保持 `vue-draggable-next` default slot + v-for 用法，不改
- **ExecController 兼容性**：`Accept: application/json`（或未指定 Accept）的默认路径必须仍返回 `Result<T>` JSON 体，不能破坏 M2-7 契约；只有 `Accept != json` 或 `?format=xml|csv|form` 时才走非 JSON 分支
- **不改老导出**：`SysLogService.exportExcel`（SYS-1 已用）与 `TableMetaService.exportExcel`（M2-2 已用）**不回填**到 `ExcelExportUtil`，只新增的导出走新工具，防回归
- **数据库变更**：`interface_config` 加列走**幂等**迁移脚本 `backend/src/main/resources/db/migration-response-format.sql`，同时更新 `init.sql`（供新库直接生效）
- **打包**：zip 导入导出只用 JDK `java.util.zip.ZipOutputStream / ZipInputStream`，**不引入 zip4j 等第三方**
- **不引入 PDF/Word 库**（iText、PDFBox、docx4j 全部禁止）；FN-09 只输出 Markdown + HTML
- **不引入 monaco-editor**（体积过大），FN-08 只用 CodeMirror 5.x（gzip < 200KB）
- **不引入第二个 Excel 库**：全部走 POI 5.2.3
- **测试规约**：新增测试类必须加 `@ActiveProfiles("test")`；测试命名 `FN{XX}{功能}Test.java`；Service 层测试加 `@Transactional` 自动回滚
- **TDD 强制**：严格 Red → Green → Refactor，每 Task 必须先失败再实现
- **响应格式协商测试覆盖**：JSON / XML / CSV / FORM_DATA **四种**必须各有断言用例
- **审计与操作日志**：导入操作必须挂 `@SysLogRecord(action="导入配置")`；发布/禁用/导出复用已有埋点
- **权限**：`/api/exec/**` 保持 Sa-Token 白名单；`/api/interface/**`、`/api/doc/**`、`/api/config/import/**`、`/api/config/export/**` 走登录鉴权（不新增权限项，继承 list 端点权限）
- **枚举值命名**：`FormatType.FORM_DATA` 是既有枚举名（不是 `FORM`），前端 `responseFormat` 存字符串 `"JSON"/"XML"/"CSV"/"FORM_DATA"`，与 `FormatType.name()` 保持一致
- **CHG-020 与问题清单**：全部 Task 完成后追加 `CHG-020` 到 `docs/03-开发/变更记录.md`，并把 `docs/03-开发/问题清单.md` 中 FN-06 ～ FN-11 六条从"待解决"移入"已解决"

---

## 文件清单总览

### 新建

| 分类 | 路径 | 对应子项 |
|-----|-----|---------|
| 后端工具 | `backend/src/main/java/com/powergateway/utils/AcceptNegotiator.java` | FN-06 |
| 后端工具 | `backend/src/main/java/com/powergateway/utils/ExcelExportUtil.java` | FN-07/10 |
| 后端 DTO | `backend/src/main/java/com/powergateway/model/dto/FieldSchemaRow.java` | FN-07 |
| 后端 DTO | `backend/src/main/java/com/powergateway/model/dto/DocumentModel.java` | FN-09 |
| 后端 DTO | `backend/src/main/java/com/powergateway/model/dto/ImportManifest.java` | FN-11 |
| 后端 DTO | `backend/src/main/java/com/powergateway/model/dto/ImportResult.java` | FN-11 |
| 后端 Service | `backend/src/main/java/com/powergateway/service/InterfaceFieldSchemaService.java` | FN-07 |
| 后端 Service | `backend/src/main/java/com/powergateway/service/InterfaceDocumentService.java` | FN-09 |
| 后端 Service | `backend/src/main/java/com/powergateway/service/ConfigExportService.java` | FN-11 |
| 后端 Service | `backend/src/main/java/com/powergateway/service/ConfigImportService.java` | FN-11 |
| 后端 Controller | `backend/src/main/java/com/powergateway/controller/InterfaceFieldSchemaController.java` | FN-07 |
| 后端 Controller | `backend/src/main/java/com/powergateway/controller/InterfaceDocumentController.java` | FN-09 |
| 后端 Controller | `backend/src/main/java/com/powergateway/controller/ConfigImportExportController.java` | FN-11 |
| 数据库 | `backend/src/main/resources/db/migration-response-format.sql` | FN-06 |
| 前端工具 | `frontend/src/utils/download.js` | FN-07/09/10/11 |
| 前端组件 | `frontend/src/components/interface/ResponseHeadersEditor.vue` | FN-06 |
| 前端组件 | `frontend/src/components/tools/CodeEditor.vue` | FN-08 |
| 前端页面 | `frontend/src/views/interface/InterfaceDocument.vue` | FN-09 |
| 前端页面 | `frontend/src/views/interface/InterfaceImportExport.vue` | FN-11 |
| 前端 API | `frontend/src/api/interfaceFieldSchema.js` | FN-07 |
| 前端 API | `frontend/src/api/interfaceDoc.js` | FN-09 |
| 前端 API | `frontend/src/api/interfaceImportExport.js` | FN-11 |

### 修改

| 路径 | 变更 | 对应子项 |
|-----|-----|---------|
| `backend/.../controller/ExecController.java` | Accept 协商 + 响应序列化分支 | FN-06 |
| `backend/.../model/InterfaceConfig.java` | 新增 `responseFormat` / `responseHeaders` | FN-06 |
| `backend/.../model/dto/InterfaceSaveRequest.java` | 同步二字段 | FN-06 |
| `backend/.../service/InterfaceConfigService.java` | `save` 处理新字段 + 新增 `exportList` | FN-06/10 |
| `backend/.../controller/InterfaceConfigController.java` | 新增 `/list/export` 端点 | FN-10 |
| `backend/.../service/TemplateService.java` | 新增 `exportList` | FN-10 |
| `backend/.../controller/TemplateController.java` | 新增 `/list/export` 端点 | FN-10 |
| `backend/.../service/ChannelConfigService.java` | 新增 `exportList` | FN-10 |
| `backend/.../controller/ChannelConfigController.java` | 新增 `/list/export` 端点 | FN-10 |
| `backend/.../service/PortRouteService.java` | 新增 `exportList` | FN-10 |
| `backend/.../controller/PortRouteController.java` | 新增 `/list/export` 端点 | FN-10 |
| `backend/.../service/PerfStatService.java` | 新增 `exportList` | FN-10 |
| `backend/.../controller/StatsController.java` | 新增 `/list/export` 端点 | FN-10 |
| `backend/.../service/CacheService.java`（或 `QueryCacheManager` 侧） | 新增 `exportList` | FN-10 |
| `backend/.../controller/CacheController.java` | 新增 `/list/export` 端点 | FN-10 |
| `backend/.../service/DbConnectionService.java` | 新增 `exportList` | FN-10 |
| `backend/.../controller/DbConnectionController.java` | 新增 `/list/export` 端点 | FN-10 |
| `backend/.../service/SqlAuditLogService.java`（或 `SqlAuditLogController` 侧新增 service 依赖） | 新增 `exportList` | FN-10 |
| `backend/.../controller/SqlAuditLogController.java` | 新增 `/list/export` 端点 | FN-10 |
| `backend/src/main/resources/db/init.sql` | `interface_config` DDL 追加两列 + 注释指向 migration 脚本 | FN-06 |
| `frontend/src/views/interface/InterfaceList.vue` | 新增「响应格式」列 + 「导出字段」 + 「导出报表」按钮 | FN-06/07/10 |
| `frontend/src/views/interface/QueryConfig.vue` | 「基础信息」新增 responseFormat + responseHeaders 表单项 | FN-06 |
| `frontend/src/views/interface/InsertConfig.vue` | 同上 | FN-06 |
| `frontend/src/views/interface/UpdateConfig.vue` | 同上 | FN-06 |
| `frontend/src/views/interface/DeleteConfig.vue` | 同上 | FN-06 |
| `frontend/src/views/tools/MessageDebug.vue` | textarea 换 CodeEditor + 语言切换 + srcFormat 透传 | FN-08 |
| `frontend/src/views/convert/Template.vue` | 头部新增「导出报表」按钮 | FN-10 |
| `frontend/src/views/convert/ChannelConfig.vue` | 同上 | FN-10 |
| `frontend/src/views/convert/PortRoute.vue` | 同上 | FN-10 |
| `frontend/src/views/system/LogList.vue` | SQL 审计 Tab 新增「导出报表」按钮 | FN-10 |
| `frontend/src/views/system/Stats.vue` | 头部新增「导出报表」按钮 | FN-10 |
| `frontend/src/views/interface/CacheList.vue` | 头部新增「导出报表」按钮 | FN-10 |
| `frontend/src/views/db/Datasource.vue` | 头部新增「导出报表」按钮 | FN-10 |
| `frontend/src/api/interface.js` | 补 `exportInterfaceList` / `exportFieldSchema` / 新增字段透传 | FN-06/07/10 |
| `frontend/src/router/index.js` | append-only 新增 `/interface/document` 与 `/interface/import-export` | FN-09/11 |
| `frontend/package.json` | 新增 `codemirror` / `vue-codemirror` 依赖 | FN-08 |
| `docs/03-开发/变更记录.md` | 追加 CHG-020 | 最终 |
| `docs/03-开发/问题清单.md` | FN-06 ~ FN-11 搬到「已解决」 | 最终 |

### 新增测试

| 路径 | 覆盖点 |
|-----|-------|
| `backend/src/test/java/com/powergateway/FN06AcceptNegotiatorTest.java` | 工具类五种优先级组合 |
| `backend/src/test/java/com/powergateway/FN06ExecFormatTest.java` | JSON/XML/CSV/FORM_DATA 四格式 + query 覆盖 + config 默认 + 未知 format |
| `backend/src/test/java/com/powergateway/FN07FieldSchemaTest.java` | SELECT/INSERT/UPDATE/DELETE 四类接口的双 Sheet 结构 + 中文注释 |
| `backend/src/test/java/com/powergateway/FN09DocumentServiceTest.java` | 转换接口 Markdown 生成 + 可视化接口 HTML 生成 + zip 全量打包 |
| `backend/src/test/java/com/powergateway/FN10ExcelExportUtilTest.java` | 空列表 / 100 条 / sheet 名安全化 / 自适应宽度 |
| `backend/src/test/java/com/powergateway/FN10InterfaceListExportTest.java` | 按 name 过滤后导出行数一致 |
| `backend/src/test/java/com/powergateway/FN11ConfigExportServiceTest.java` | 全量导出 zip 结构 + manifest 完备 + 不含 id/时间 |
| `backend/src/test/java/com/powergateway/FN11ConfigImportServiceTest.java` | OVERWRITE / SKIP / ASK / 事务回滚 / 主键冲突 |

---

## Task 1: 数据库幂等迁移脚本 + 实体字段 · FN-06

**Files:**
- Create: `backend/src/main/resources/db/migration-response-format.sql`
- Modify: `backend/src/main/resources/db/init.sql`
- Modify: `backend/src/main/java/com/powergateway/model/InterfaceConfig.java`
- Modify: `backend/src/main/java/com/powergateway/model/dto/InterfaceSaveRequest.java`
- Modify: `backend/src/main/java/com/powergateway/service/InterfaceConfigService.java`（`save` 保存两字段）
- Test: `backend/src/test/java/com/powergateway/FN06InterfaceConfigFieldTest.java`

**Interfaces:**
- Produces: `InterfaceConfig.getResponseFormat() : String`、`InterfaceConfig.getResponseHeaders() : String`
- Consumes: `InterfaceSaveRequest.getResponseFormat() / getResponseHeaders()`

- [ ] **Step 1**：写失败测试 `FN06InterfaceConfigFieldTest.java`

```java
package com.powergateway;

import com.powergateway.model.InterfaceConfig;
import com.powergateway.model.dto.InterfaceSaveRequest;
import com.powergateway.service.InterfaceConfigService;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
@DisplayName("FN-06 interface_config responseFormat/responseHeaders 字段落库")
class FN06InterfaceConfigFieldTest {

    @Autowired private InterfaceConfigService service;

    @Test
    void 保存接口_默认responseFormat为JSON() {
        InterfaceSaveRequest req = new InterfaceSaveRequest();
        req.setName("FN06默认格式_" + System.nanoTime());
        req.setDbConnectionId(1L);
        req.setType("SELECT");
        req.setConfigJson("{\"tables\":[],\"fields\":[],\"conditions\":[],\"joins\":[]}");
        Long id = service.save(req);
        InterfaceConfig cfg = service.getById(id);
        assertEquals("JSON", cfg.getResponseFormat(), "未指定响应格式时应默认为 JSON");
    }

    @Test
    void 保存接口_指定XML与自定义响应头_可回读() {
        InterfaceSaveRequest req = new InterfaceSaveRequest();
        req.setName("FN06XML_" + System.nanoTime());
        req.setDbConnectionId(1L);
        req.setType("SELECT");
        req.setConfigJson("{\"tables\":[],\"fields\":[],\"conditions\":[],\"joins\":[]}");
        req.setResponseFormat("XML");
        req.setResponseHeaders("{\"X-Foo\":\"bar\"}");
        Long id = service.save(req);
        InterfaceConfig cfg = service.getById(id);
        assertEquals("XML", cfg.getResponseFormat());
        assertTrue(cfg.getResponseHeaders().contains("X-Foo"));
    }
}
```

- [ ] **Step 2**：运行确认失败

```bash
cd backend && mvn test -Dtest=FN06InterfaceConfigFieldTest -q 2>&1 | tail -20
```

预期 `BUILD FAILURE`，报 `setResponseFormat` 方法不存在或字段未持久化。

- [ ] **Step 3**：创建幂等迁移脚本 `backend/src/main/resources/db/migration-response-format.sql`

```sql
-- CHG-020 UX-E FN-06：为 interface_config 补齐响应格式相关字段（幂等）
-- 适用于在 CHG-020 之前创建的旧库；新库通过 init.sql 直接生效
DELIMITER $$

DROP PROCEDURE IF EXISTS pg_add_response_format$$

CREATE PROCEDURE pg_add_response_format()
BEGIN
  IF NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS
                 WHERE TABLE_SCHEMA = DATABASE()
                   AND TABLE_NAME = 'interface_config'
                   AND COLUMN_NAME = 'response_format') THEN
    ALTER TABLE interface_config
      ADD COLUMN response_format VARCHAR(16) DEFAULT 'JSON'
      COMMENT 'FN-06 用户默认响应格式：JSON/XML/CSV/FORM_DATA';
  END IF;

  IF NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS
                 WHERE TABLE_SCHEMA = DATABASE()
                   AND TABLE_NAME = 'interface_config'
                   AND COLUMN_NAME = 'response_headers') THEN
    ALTER TABLE interface_config
      ADD COLUMN response_headers TEXT DEFAULT NULL
      COMMENT 'FN-06 自定义响应头 JSON，格式 {"X-Foo":"bar"}';
  END IF;
END$$

DELIMITER ;

CALL pg_add_response_format();
DROP PROCEDURE pg_add_response_format;
```

- [ ] **Step 4**：在 `init.sql` 中 `interface_config` 建表 DDL 追加两列，并在文件头注释增加：

```sql
-- 兼容旧库（CHG-020 UX-E FN-06）：如已存在 interface_config 表，请执行 migration-response-format.sql
```

- [ ] **Step 5**：`InterfaceConfig.java` 新增字段

```java
private String responseFormat;   // FN-06 用户默认响应格式，默认 "JSON"
private String responseHeaders;  // FN-06 自定义响应头 JSON 字符串
```

- [ ] **Step 6**：`InterfaceSaveRequest.java` 同步

```java
private String responseFormat;
private String responseHeaders;
```

- [ ] **Step 7**：`InterfaceConfigService.save()` 在入库前兜底

```java
config.setResponseFormat(
    req.getResponseFormat() == null || req.getResponseFormat().isEmpty()
        ? "JSON" : req.getResponseFormat());
config.setResponseHeaders(req.getResponseHeaders());
```

（H2 测试环境走 `init.sql`，无需执行 migration；生产库需手工跑一次 `migration-response-format.sql`。）

- [ ] **Step 8**：运行确认通过 + 回归全量

```bash
cd backend && mvn test -Dtest=FN06InterfaceConfigFieldTest -q 2>&1 | tail -10
cd backend && mvn test -q 2>&1 | tail -15
```

- [ ] **Step 9**：提交

```bash
git add backend/src/main/resources/db/migration-response-format.sql \
        backend/src/main/resources/db/init.sql \
        backend/src/main/java/com/powergateway/model/InterfaceConfig.java \
        backend/src/main/java/com/powergateway/model/dto/InterfaceSaveRequest.java \
        backend/src/main/java/com/powergateway/service/InterfaceConfigService.java \
        backend/src/test/java/com/powergateway/FN06InterfaceConfigFieldTest.java
git commit -m "feat(FN-06): interface_config 新增 responseFormat/responseHeaders 字段与幂等迁移脚本"
```

---

## Task 2: AcceptNegotiator 工具类 · FN-06

**Files:**
- Create: `backend/src/main/java/com/powergateway/utils/AcceptNegotiator.java`
- Test: `backend/src/test/java/com/powergateway/FN06AcceptNegotiatorTest.java`

**Interfaces:**
- Produces: `AcceptNegotiator.negotiate(HttpServletRequest, String queryFormat, String configDefault) : FormatType`
- Consumes: `com.powergateway.utils.FormatType`（既有）

- [ ] **Step 1**：写失败测试

```java
package com.powergateway;

import com.powergateway.utils.AcceptNegotiator;
import com.powergateway.utils.FormatType;
import org.junit.jupiter.api.*;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.*;

@ActiveProfiles("test")
@DisplayName("FN-06 AcceptNegotiator 五种优先级")
class FN06AcceptNegotiatorTest {

    private MockHttpServletRequest req() { return new MockHttpServletRequest(); }

    @Test
    void query参数优先级最高() {
        MockHttpServletRequest r = req();
        r.addHeader("Accept", "application/xml");
        assertEquals(FormatType.CSV,
            AcceptNegotiator.negotiate(r, "csv", "XML"));
    }

    @Test
    void accept头次之_xml() {
        MockHttpServletRequest r = req();
        r.addHeader("Accept", "application/xml");
        assertEquals(FormatType.XML, AcceptNegotiator.negotiate(r, null, "CSV"));
    }

    @Test
    void accept头_textCsv() {
        MockHttpServletRequest r = req();
        r.addHeader("Accept", "text/csv");
        assertEquals(FormatType.CSV, AcceptNegotiator.negotiate(r, null, null));
    }

    @Test
    void accept头_formUrlencoded() {
        MockHttpServletRequest r = req();
        r.addHeader("Accept", "application/x-www-form-urlencoded");
        assertEquals(FormatType.FORM_DATA, AcceptNegotiator.negotiate(r, null, null));
    }

    @Test
    void 无query与accept_使用config默认() {
        assertEquals(FormatType.XML, AcceptNegotiator.negotiate(req(), null, "XML"));
    }

    @Test
    void 全部缺失_兜底JSON() {
        assertEquals(FormatType.JSON, AcceptNegotiator.negotiate(req(), null, null));
    }

    @Test
    void acceptStar_兜底JSON() {
        MockHttpServletRequest r = req();
        r.addHeader("Accept", "*/*");
        assertEquals(FormatType.JSON, AcceptNegotiator.negotiate(r, null, null));
    }
}
```

- [ ] **Step 2**：运行失败

```bash
cd backend && mvn test -Dtest=FN06AcceptNegotiatorTest -q 2>&1 | tail -20
```

- [ ] **Step 3**：新建 `AcceptNegotiator.java`

```java
package com.powergateway.utils;

import org.springframework.http.HttpHeaders;
import javax.servlet.http.HttpServletRequest;

/** FN-06 Accept/query/config 三级优先级协商响应格式。 */
public final class AcceptNegotiator {

    private AcceptNegotiator() {}

    public static FormatType negotiate(HttpServletRequest req,
                                       String queryParamFormat,
                                       String configDefault) {
        if (queryParamFormat != null && !queryParamFormat.isEmpty()) {
            return FormatType.parse(queryParamFormat);
        }
        String accept = req.getHeader(HttpHeaders.ACCEPT);
        if (accept != null && !accept.isEmpty() && !"*/*".equals(accept.trim())) {
            if (accept.contains("application/xml") || accept.contains("text/xml")) return FormatType.XML;
            if (accept.contains("text/csv")) return FormatType.CSV;
            if (accept.contains("application/x-www-form-urlencoded")) return FormatType.FORM_DATA;
            if (accept.contains("application/json")) return FormatType.JSON;
        }
        if (configDefault != null && !configDefault.isEmpty()) {
            return FormatType.parse(configDefault);
        }
        return FormatType.JSON;
    }
}
```

- [ ] **Step 4**：运行通过

```bash
cd backend && mvn test -Dtest=FN06AcceptNegotiatorTest -q 2>&1 | tail -10
```

- [ ] **Step 5**：提交

```bash
git add backend/src/main/java/com/powergateway/utils/AcceptNegotiator.java \
        backend/src/test/java/com/powergateway/FN06AcceptNegotiatorTest.java
git commit -m "feat(FN-06): 新增 AcceptNegotiator 协商响应格式（query > Accept > 配置 > JSON 兜底）"
```

---

## Task 3: ExecController 响应格式协商与非 JSON 分支 · FN-06

**Files:**
- Modify: `backend/src/main/java/com/powergateway/controller/ExecController.java`
- Test: `backend/src/test/java/com/powergateway/FN06ExecFormatTest.java`

**Interfaces:**
- Consumes: `FormatConverter.serializeMap(Map, FormatType)`（既有）、`AcceptNegotiator.negotiate(...)`、`InterfaceConfig.getResponseFormat()`
- Produces: `POST /api/exec/{id}` 支持 Accept application/json | application/xml | text/csv | application/x-www-form-urlencoded 以及 `?format=xml|csv|form_data|json`

- [ ] **Step 1**：写失败测试 `FN06ExecFormatTest.java`

用 `@SpringBootTest + @AutoConfigureMockMvc + @ActiveProfiles("test")`，参考 M27ExecControllerTest 建 H2 表 + 保存并发布 SELECT 接口，用例：

```java
@Test @Order(1)
void 默认Accept_返回JSON_Result包装保持兼容() throws Exception {
    Long id = saveAndPublishSelect();
    mockMvc.perform(post("/api/exec/" + id)
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"params\":{}}"))
        .andExpect(status().isOk())
        .andExpect(header().string("Content-Type", containsString("application/json")))
        .andExpect(jsonPath("$.code").value(200))
        .andExpect(jsonPath("$.data").isArray());
}

@Test @Order(2)
void Accept_XML_返回XML() throws Exception {
    Long id = saveAndPublishSelect();
    MvcResult r = mockMvc.perform(post("/api/exec/" + id)
            .header("Accept", "application/xml")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"params\":{}}"))
        .andExpect(status().isOk())
        .andExpect(header().string("Content-Type", containsString("xml")))
        .andReturn();
    String body = r.getResponse().getContentAsString();
    assertTrue(body.startsWith("<"), "XML 响应必须以 < 开头，实际: " + body);
    assertTrue(body.contains("rows") || body.contains("row"),
        "XML 响应必须包裹 rows/row 节点");
}

@Test @Order(3)
void Query_format_csv_覆盖Accept() throws Exception {
    Long id = saveAndPublishSelect();
    MvcResult r = mockMvc.perform(post("/api/exec/" + id + "?format=csv")
            .header("Accept", "application/xml")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"params\":{}}"))
        .andExpect(status().isOk())
        .andExpect(header().string("Content-Type", containsString("csv")))
        .andReturn();
    String body = r.getResponse().getContentAsString();
    assertTrue(body.length() > 0);
    // CSV 首行为列头
    assertTrue(body.split("\n")[0].contains("id") || body.split("\n")[0].contains("name"));
}

@Test @Order(4)
void Accept_FormUrlEncoded_返回FormData() throws Exception {
    Long id = saveAndPublishSelect();
    MvcResult r = mockMvc.perform(post("/api/exec/" + id)
            .header("Accept", "application/x-www-form-urlencoded")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"params\":{}}"))
        .andExpect(status().isOk())
        .andReturn();
    String body = r.getResponse().getContentAsString();
    assertTrue(body.contains("="), "FormData 响应必须含 key=value");
}

@Test @Order(5)
void 未指定Accept_读取config默认responseFormat_XML() throws Exception {
    Long id = saveAndPublishSelectWithFormat("XML");
    MvcResult r = mockMvc.perform(post("/api/exec/" + id)
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"params\":{}}"))
        .andReturn();
    assertTrue(r.getResponse().getContentAsString().startsWith("<"));
}

@Test @Order(6)
void 未知format参数_400() throws Exception {
    Long id = saveAndPublishSelect();
    mockMvc.perform(post("/api/exec/" + id + "?format=protobuf")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"params\":{}}"))
        .andExpect(status().is4xxClientError());
}

@Test @Order(7)
void INSERT_Accept_XML_返回XML包装的影响行数() throws Exception {
    Long id = saveAndPublishInsert();
    MvcResult r = mockMvc.perform(post("/api/exec/" + id)
            .header("Accept", "application/xml")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"params\":{\"name\":\"新增项\"}}"))
        .andExpect(status().isOk())
        .andReturn();
    String body = r.getResponse().getContentAsString();
    assertTrue(body.startsWith("<"));
    assertTrue(body.contains("affected") || body.contains("data"));
}
```

（`saveAndPublishSelectWithFormat("XML")` 与 `saveAndPublishInsert()` 是本测试内私有辅助方法，先保存接口指定 `responseFormat`，再走 `/publish`。）

- [ ] **Step 2**：运行失败

```bash
cd backend && mvn test -Dtest=FN06ExecFormatTest -q 2>&1 | tail -30
```

- [ ] **Step 3**：改造 `ExecController.execute`

```java
@RestController
@RequestMapping("/api/exec")
@Tag(name = "接口执行")
public class ExecController {

    @Autowired private InterfaceConfigService service;
    @Autowired private FormatConverter formatConverter;
    @Autowired private ObjectMapper objectMapper;

    @PostMapping(value = "/{interfaceId}",
        produces = { MediaType.APPLICATION_JSON_VALUE,
                     MediaType.APPLICATION_XML_VALUE,
                     "text/csv",
                     MediaType.APPLICATION_FORM_URLENCODED_VALUE })
    @Operation(summary = "执行已发布接口（支持 Accept/format 协商 JSON/XML/CSV/FORM_DATA）")
    public ResponseEntity<?> execute(HttpServletRequest httpReq,
                                     @PathVariable Long interfaceId,
                                     @RequestParam(value = "format", required = false) String format,
                                     @RequestBody(required = false) ExecRequest req) {
        if (req == null) req = new ExecRequest();
        Map<String, Object> params = req.getParams() != null ? req.getParams() : new HashMap<>();
        InterfaceConfig config = service.getById(interfaceId);

        if ("disabled".equals(config.getStatus())) {
            return ResponseEntity.ok(Result.fail(403, "接口已禁用"));
        }
        if ("draft".equals(config.getStatus())) {
            return ResponseEntity.ok(Result.fail(400, "接口未发布"));
        }

        Object dataObj = dispatchByType(config, interfaceId, params, req);

        FormatType target;
        try {
            target = AcceptNegotiator.negotiate(httpReq, format, config.getResponseFormat());
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Result.fail(400, "未知响应格式: " + format));
        }

        if (target == FormatType.JSON) {
            return ResponseEntity.ok(Result.success(dataObj));
        }
        return serializeNonJson(dataObj, target, config);
    }

    private Object dispatchByType(InterfaceConfig config, Long id,
                                  Map<String, Object> params, ExecRequest req) {
        switch (config.getType()) {
            case "SELECT":
                return service.executeQuery(id, params, req.getPage(), req.getPageSize());
            case "INSERT":
                return service.executeInsert(id, params);
            case "UPDATE":
                AuditContextHolder.set(new AuditContext()
                        .setInterfaceId(id).setOpType("UPDATE")
                        .setTargetDb(String.valueOf(config.getDbConnectionId())));
                return service.executeUpdate(id, params);
            case "DELETE":
                AuditContextHolder.set(new AuditContext()
                        .setInterfaceId(id).setOpType("DELETE")
                        .setTargetDb(String.valueOf(config.getDbConnectionId())));
                return service.executeDelete(id, params);
            default:
                throw new BusinessException(400, "不支持的接口类型: " + config.getType());
        }
    }

    @SuppressWarnings("unchecked")
    private ResponseEntity<String> serializeNonJson(Object data, FormatType target,
                                                    InterfaceConfig config) {
        Map<String, Object> wrapped = new LinkedHashMap<>();
        if (data instanceof List) {
            // XML 需要单一根节点，包装为 {rows: [...]}
            wrapped.put("rows", data);
        } else {
            wrapped.put("data", data);
        }
        String body = formatConverter.serializeMap(wrapped, target);

        HttpHeaders h = new HttpHeaders();
        h.setContentType(mediaTypeOf(target));
        // 自定义响应头透传
        if (config.getResponseHeaders() != null && !config.getResponseHeaders().isEmpty()) {
            try {
                Map<String, String> extra = objectMapper.readValue(config.getResponseHeaders(), Map.class);
                extra.forEach(h::add);
            } catch (Exception ignore) { /* 配置格式错时忽略，不阻塞主流程 */ }
        }
        return new ResponseEntity<>(body, h, HttpStatus.OK);
    }

    private MediaType mediaTypeOf(FormatType f) {
        switch (f) {
            case XML: return MediaType.APPLICATION_XML;
            case CSV: return MediaType.parseMediaType("text/csv;charset=UTF-8");
            case FORM_DATA: return MediaType.APPLICATION_FORM_URLENCODED;
            default: return MediaType.APPLICATION_JSON;
        }
    }
}
```

- [ ] **Step 4**：运行通过 + 全量回归

```bash
cd backend && mvn test -Dtest=FN06ExecFormatTest -q 2>&1 | tail -20
cd backend && mvn test -q 2>&1 | tail -20
```

- [ ] **Step 5**：提交

```bash
git add backend/src/main/java/com/powergateway/controller/ExecController.java \
        backend/src/test/java/com/powergateway/FN06ExecFormatTest.java
git commit -m "feat(FN-06): ExecController Accept 协商 + 非 JSON 分支序列化"
```

---

## Task 4: 前端接口配置页与列表页响应格式支持 · FN-06

**Files:**
- Create: `frontend/src/components/interface/ResponseHeadersEditor.vue`
- Modify: `frontend/src/api/interface.js`（save/update 透传新字段）
- Modify: `frontend/src/views/interface/QueryConfig.vue` / `InsertConfig.vue` / `UpdateConfig.vue` / `DeleteConfig.vue`
- Modify: `frontend/src/views/interface/InterfaceList.vue`（新增「响应格式」列）

**Interfaces:**
- Produces: 表单字段 `form.responseFormat` / `form.responseHeaders`
- Consumes: 后端 `/api/interface/save`（已有），透传两个新字段

- [ ] **Step 1**：新建 `ResponseHeadersEditor.vue`

```vue
<template>
  <div class="rh-editor">
    <el-table :data="rows" size="small" border>
      <el-table-column label="Header 名" width="220">
        <template #default="{ row, $index }">
          <el-input v-model="row.k" size="small" @change="emitChange" />
        </template>
      </el-table-column>
      <el-table-column label="Header 值">
        <template #default="{ row }">
          <el-input v-model="row.v" size="small" @change="emitChange" />
        </template>
      </el-table-column>
      <el-table-column label="操作" width="80">
        <template #default="{ $index }">
          <el-button size="small" text type="danger" @click="remove($index)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>
    <el-button size="small" @click="add">添加 Header</el-button>
  </div>
</template>

<script setup>
import { ref, watch } from 'vue'

const props = defineProps({ modelValue: { type: String, default: '' } })
const emit = defineEmits(['update:modelValue'])

const rows = ref([])

function parse(json) {
  if (!json) return []
  try {
    const obj = JSON.parse(json)
    return Object.entries(obj).map(([k, v]) => ({ k, v: String(v) }))
  } catch { return [] }
}

function emitChange() {
  const obj = {}
  rows.value.forEach(r => { if (r.k) obj[r.k] = r.v })
  emit('update:modelValue', Object.keys(obj).length ? JSON.stringify(obj) : '')
}
function add() { rows.value.push({ k: '', v: '' }); emitChange() }
function remove(i) { rows.value.splice(i, 1); emitChange() }

watch(() => props.modelValue, v => { rows.value = parse(v) }, { immediate: true })
</script>
```

- [ ] **Step 2**：在四个接口配置页「基础信息」区加两项。以 `QueryConfig.vue` 为例：

```vue
<el-form-item label="默认响应格式">
  <el-select v-model="form.responseFormat" style="width:180px">
    <el-option label="JSON" value="JSON" />
    <el-option label="XML" value="XML" />
    <el-option label="CSV" value="CSV" />
    <el-option label="FormData" value="FORM_DATA" />
  </el-select>
  <el-tooltip content="调用方可通过 Accept 头或 ?format= 覆盖此默认值">
    <el-icon style="margin-left:6px"><QuestionFilled /></el-icon>
  </el-tooltip>
</el-form-item>
<el-form-item label="自定义响应头">
  <ResponseHeadersEditor v-model="form.responseHeaders" />
</el-form-item>
```

在 `form` 初始化处加 `responseFormat: 'JSON', responseHeaders: ''`。`InsertConfig.vue` / `UpdateConfig.vue` / `DeleteConfig.vue` 同上处理。

- [ ] **Step 3**：`InterfaceList.vue` 表格新增一列

```vue
<el-table-column label="响应格式" width="110">
  <template #default="{ row }">
    <el-tag size="small">{{ row.responseFormat || 'JSON' }}</el-tag>
  </template>
</el-table-column>
```

- [ ] **Step 4**：`frontend/src/api/interface.js` 确认 save 请求已透传全对象（`InterfaceSaveRequest` 一次序列化整个 form），无需增字段。

- [ ] **Step 5**：本地起前端手工验证

```bash
cd frontend && npm run dev
```

打开任一 SELECT 接口配置页，切「默认响应格式=XML」后保存，回列表页确认「响应格式」列显示 `XML`。

- [ ] **Step 6**：提交

```bash
git add frontend/src/components/interface/ResponseHeadersEditor.vue \
        frontend/src/views/interface/QueryConfig.vue \
        frontend/src/views/interface/InsertConfig.vue \
        frontend/src/views/interface/UpdateConfig.vue \
        frontend/src/views/interface/DeleteConfig.vue \
        frontend/src/views/interface/InterfaceList.vue
git commit -m "feat(FN-06): 接口配置页新增响应格式/响应头字段，列表页展示"
```

---

## Task 5: ExcelExportUtil 通用工具 · FN-07/10 基石

**Files:**
- Create: `backend/src/main/java/com/powergateway/utils/ExcelExportUtil.java`
- Test: `backend/src/test/java/com/powergateway/FN10ExcelExportUtilTest.java`

**Interfaces:**
- Produces: `ExcelExportUtil.Column<T>`（POJO：header、valueGetter）
- Produces: `ExcelExportUtil.export(String sheetName, List<Column<T>> columns, List<T> rows) : byte[]`
- Produces: `ExcelExportUtil.exportMultiSheet(Map<String, SheetSpec<?>>) : byte[]`（供 FN-07 双 Sheet 使用）
- Consumes: POI 5.2.3 `XSSFWorkbook`

- [ ] **Step 1**：写失败测试

```java
package com.powergateway;

import com.powergateway.utils.ExcelExportUtil;
import com.powergateway.utils.ExcelExportUtil.Column;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.*;
import org.springframework.test.context.ActiveProfiles;

import java.io.ByteArrayInputStream;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

@ActiveProfiles("test")
@DisplayName("FN-10 ExcelExportUtil")
class FN10ExcelExportUtilTest {

    private static class Item {
        String name; int qty;
        Item(String n, int q) { this.name=n; this.qty=q; }
    }

    private final List<Column<Item>> columns = Arrays.asList(
        new Column<>("名称", i -> i.name),
        new Column<>("数量", i -> i.qty)
    );

    @Test
    void 空列表_仅生成header行() throws Exception {
        byte[] bytes = ExcelExportUtil.export("测试", columns, Collections.emptyList());
        try (Workbook wb = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
            Sheet s = wb.getSheetAt(0);
            assertEquals(0, s.getLastRowNum(), "空数据时应只有 header 行");
            Row header = s.getRow(0);
            assertEquals("名称", header.getCell(0).getStringCellValue());
            assertEquals("数量", header.getCell(1).getStringCellValue());
        }
    }

    @Test
    void 一百条数据_行数正确() throws Exception {
        List<Item> data = new ArrayList<>();
        for (int i = 0; i < 100; i++) data.add(new Item("n" + i, i));
        byte[] bytes = ExcelExportUtil.export("测试", columns, data);
        try (Workbook wb = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
            assertEquals(100, wb.getSheetAt(0).getLastRowNum(), "100 条 + header = lastRowNum 100");
        }
    }

    @Test
    void sheet名安全化_特殊字符替换() throws Exception {
        byte[] bytes = ExcelExportUtil.export("含/非法\\字符?", columns, Collections.emptyList());
        try (Workbook wb = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
            String name = wb.getSheetAt(0).getSheetName();
            assertFalse(name.contains("/"));
            assertFalse(name.contains("\\"));
            assertFalse(name.contains("?"));
        }
    }

    @Test
    void 自适应宽度_列宽大于零() throws Exception {
        byte[] bytes = ExcelExportUtil.export("测试", columns,
            Collections.singletonList(new Item("苹果", 3)));
        try (Workbook wb = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
            assertTrue(wb.getSheetAt(0).getColumnWidth(0) > 0);
        }
    }

    @Test
    void null值不抛异常_写空串() throws Exception {
        Item i = new Item(null, 0);
        byte[] bytes = ExcelExportUtil.export("测试", columns,
            Collections.singletonList(i));
        try (Workbook wb = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
            Cell c = wb.getSheetAt(0).getRow(1).getCell(0);
            assertNotNull(c);
        }
    }
}
```

- [ ] **Step 2**：运行失败

```bash
cd backend && mvn test -Dtest=FN10ExcelExportUtilTest -q 2>&1 | tail -15
```

- [ ] **Step 3**：新建 `ExcelExportUtil.java`

```java
package com.powergateway.utils;

import com.powergateway.exception.BusinessException;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.ByteArrayOutputStream;
import java.util.*;
import java.util.function.Function;

/** FN-07 / FN-10 通用 Excel 导出工具（POI 5.2.3，XSSFWorkbook 内存模式）。 */
public final class ExcelExportUtil {

    private ExcelExportUtil() {}

    public static final class Column<T> {
        public final String header;
        public final Function<T, Object> valueGetter;
        public Column(String header, Function<T, Object> getter) {
            this.header = header; this.valueGetter = getter;
        }
    }

    /** 单 Sheet 导出。 */
    public static <T> byte[] export(String sheetName, List<Column<T>> columns, List<T> rows) {
        try (XSSFWorkbook wb = new XSSFWorkbook();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            writeSheet(wb, sheetName, columns, rows);
            wb.write(out);
            return out.toByteArray();
        } catch (Exception e) {
            throw new BusinessException(500, "Excel 导出失败: " + e.getMessage());
        }
    }

    /** 多 Sheet 导出（供 FN-07 请求/响应字段双 Sheet）。 */
    public static byte[] exportMultiSheet(LinkedHashMap<String, SheetSpec<?>> sheets) {
        try (XSSFWorkbook wb = new XSSFWorkbook();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            for (Map.Entry<String, SheetSpec<?>> e : sheets.entrySet()) {
                writeSheetRaw(wb, e.getKey(), e.getValue());
            }
            wb.write(out);
            return out.toByteArray();
        } catch (Exception e) {
            throw new BusinessException(500, "Excel 多 Sheet 导出失败: " + e.getMessage());
        }
    }

    public static final class SheetSpec<T> {
        public final List<Column<T>> columns;
        public final List<T> rows;
        public SheetSpec(List<Column<T>> c, List<T> r) { this.columns=c; this.rows=r; }
    }

    private static <T> void writeSheet(XSSFWorkbook wb, String name,
                                       List<Column<T>> columns, List<T> rows) {
        Sheet sheet = wb.createSheet(safeSheetName(name));
        CellStyle headerStyle = createHeaderStyle(wb);
        Row header = sheet.createRow(0);
        for (int i = 0; i < columns.size(); i++) {
            Cell c = header.createCell(i);
            c.setCellValue(columns.get(i).header);
            c.setCellStyle(headerStyle);
        }
        for (int r = 0; r < rows.size(); r++) {
            Row row = sheet.createRow(r + 1);
            T item = rows.get(r);
            for (int c = 0; c < columns.size(); c++) {
                Object v = columns.get(c).valueGetter.apply(item);
                writeCell(row.createCell(c), v);
            }
        }
        for (int i = 0; i < columns.size(); i++) sheet.autoSizeColumn(i);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void writeSheetRaw(XSSFWorkbook wb, String name, SheetSpec spec) {
        writeSheet(wb, name, spec.columns, spec.rows);
    }

    private static void writeCell(Cell cell, Object v) {
        if (v == null) { cell.setCellValue(""); return; }
        if (v instanceof Number) cell.setCellValue(((Number) v).doubleValue());
        else if (v instanceof Boolean) cell.setCellValue((Boolean) v);
        else cell.setCellValue(v.toString());
    }

    private static CellStyle createHeaderStyle(Workbook wb) {
        CellStyle s = wb.createCellStyle();
        Font f = wb.createFont();
        f.setBold(true);
        s.setFont(f);
        s.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
        s.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        s.setAlignment(HorizontalAlignment.CENTER);
        s.setBorderTop(BorderStyle.THIN);
        s.setBorderBottom(BorderStyle.THIN);
        s.setBorderLeft(BorderStyle.THIN);
        s.setBorderRight(BorderStyle.THIN);
        return s;
    }

    /** Excel sheet 名不能含 / \ ? * [ ] :，长度 ≤ 31。 */
    private static String safeSheetName(String raw) {
        if (raw == null || raw.isEmpty()) return "Sheet";
        String s = raw.replaceAll("[/\\\\?*\\[\\]:]", "_");
        return s.length() > 31 ? s.substring(0, 31) : s;
    }
}
```

- [ ] **Step 4**：运行通过

```bash
cd backend && mvn test -Dtest=FN10ExcelExportUtilTest -q 2>&1 | tail -10
```

- [ ] **Step 5**：提交

```bash
git add backend/src/main/java/com/powergateway/utils/ExcelExportUtil.java \
        backend/src/test/java/com/powergateway/FN10ExcelExportUtilTest.java
git commit -m "feat(FN-10): 新增通用 ExcelExportUtil（单/多 Sheet + 表头样式 + 安全 Sheet 名）"
```

---

## Task 6: 字段清单导出 Service + Controller · FN-07

**Files:**
- Create: `backend/src/main/java/com/powergateway/model/dto/FieldSchemaRow.java`
- Create: `backend/src/main/java/com/powergateway/service/InterfaceFieldSchemaService.java`
- Create: `backend/src/main/java/com/powergateway/controller/InterfaceFieldSchemaController.java`
- Test: `backend/src/test/java/com/powergateway/FN07FieldSchemaTest.java`

**Interfaces:**
- Produces: `GET /api/interface/{id}/field-schema/export → application/vnd.openxmlformats-officedocument.spreadsheetml.sheet`
- Produces: `InterfaceFieldSchemaService.exportExcel(InterfaceConfig) : byte[]`
- Consumes: `TableMetaService.getTables(dbId)`（M2-2 缓存）、`ExcelExportUtil.exportMultiSheet`、`ObjectMapper`

- [ ] **Step 1**：定义 DTO

```java
package com.powergateway.model.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data @AllArgsConstructor @NoArgsConstructor
public class FieldSchemaRow {
    private Integer index;      // 序号，从 1 开始
    private String fieldName;   // 英文字段名
    private String comment;     // 中文含义（表结构注释）
    private String dataType;    // VARCHAR / INT / DATETIME
    private String length;      // 长度
    private String required;    // Y/N
    private String source;      // REQUEST / CONST=xx / CALC=xx / -
}
```

- [ ] **Step 2**：写失败测试 `FN07FieldSchemaTest.java`（Service 层，`@SpringBootTest + @Transactional`）

```java
@Test
void SELECT接口导出_请求Sheet含条件字段_响应Sheet含返回字段() throws Exception {
    Long id = createPublishedSelect();  // 辅助方法：建 H2 表 + 保存接口
    InterfaceConfig cfg = service.getById(id);
    byte[] bytes = schemaService.exportExcel(cfg);
    try (Workbook wb = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
        assertEquals(2, wb.getNumberOfSheets());
        assertEquals("请求字段", wb.getSheetAt(0).getSheetName());
        assertEquals("响应字段", wb.getSheetAt(1).getSheetName());
        Sheet respSheet = wb.getSheetAt(1);
        assertTrue(respSheet.getLastRowNum() >= 1, "响应字段至少 1 条");
    }
}

@Test
void INSERT接口导出_响应Sheet固定为影响行数() throws Exception {
    Long id = createPublishedInsert();
    byte[] bytes = schemaService.exportExcel(service.getById(id));
    try (Workbook wb = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
        Sheet resp = wb.getSheetAt(1);
        assertEquals(1, resp.getLastRowNum());
        assertTrue(resp.getRow(1).getCell(1).getStringCellValue().contains("affected"));
    }
}

@Test
void 不存在的接口_抛404() {
    assertThrows(BusinessException.class,
        () -> schemaService.exportExcel(null));
}
```

- [ ] **Step 3**：运行失败

- [ ] **Step 4**：实现 `InterfaceFieldSchemaService.java`

```java
@Service
@RequiredArgsConstructor
public class InterfaceFieldSchemaService {

    private final TableMetaService tableMetaService;
    private final ObjectMapper objectMapper;

    public byte[] exportExcel(InterfaceConfig config) {
        if (config == null) throw new BusinessException(404, "接口配置不存在");
        List<FieldSchemaRow> reqRows;
        List<FieldSchemaRow> respRows;
        try {
            switch (config.getType()) {
                case "SELECT":
                    QueryConfigJson q = objectMapper.readValue(
                        config.getConfigJson(), QueryConfigJson.class);
                    reqRows  = buildFromQueryConditions(q, config.getDbConnectionId());
                    respRows = buildFromQueryFields(q, config.getDbConnectionId());
                    break;
                case "INSERT":
                    InsertConfigJson i = objectMapper.readValue(
                        config.getConfigJson(), InsertConfigJson.class);
                    reqRows  = buildFromInsertFields(i, config.getDbConnectionId());
                    respRows = affectedRowsSchema();
                    break;
                case "UPDATE":
                    UpdateConfigJson u = objectMapper.readValue(
                        config.getConfigJson(), UpdateConfigJson.class);
                    reqRows  = buildFromUpdateFieldsAndConditions(u, config.getDbConnectionId());
                    respRows = affectedRowsSchema();
                    break;
                case "DELETE":
                    DeleteConfigJson d = objectMapper.readValue(
                        config.getConfigJson(), DeleteConfigJson.class);
                    reqRows  = buildFromDeleteConditions(d, config.getDbConnectionId());
                    respRows = affectedRowsSchema();
                    break;
                default:
                    throw new BusinessException(400, "不支持的接口类型: " + config.getType());
            }
        } catch (BusinessException be) { throw be; }
        catch (Exception e) {
            throw new BusinessException(400, "配置解析失败: " + e.getMessage());
        }

        List<ExcelExportUtil.Column<FieldSchemaRow>> cols = Arrays.asList(
            new ExcelExportUtil.Column<>("序号",       FieldSchemaRow::getIndex),
            new ExcelExportUtil.Column<>("英文字段名", FieldSchemaRow::getFieldName),
            new ExcelExportUtil.Column<>("中文含义",   FieldSchemaRow::getComment),
            new ExcelExportUtil.Column<>("数据类型",   FieldSchemaRow::getDataType),
            new ExcelExportUtil.Column<>("长度",       FieldSchemaRow::getLength),
            new ExcelExportUtil.Column<>("必填",       FieldSchemaRow::getRequired),
            new ExcelExportUtil.Column<>("备注",       FieldSchemaRow::getSource)
        );
        LinkedHashMap<String, ExcelExportUtil.SheetSpec<?>> sheets = new LinkedHashMap<>();
        sheets.put("请求字段", new ExcelExportUtil.SheetSpec<>(cols, reqRows));
        sheets.put("响应字段", new ExcelExportUtil.SheetSpec<>(cols, respRows));
        return ExcelExportUtil.exportMultiSheet(sheets);
    }

    private List<FieldSchemaRow> affectedRowsSchema() {
        return Collections.singletonList(new FieldSchemaRow(
            1, "affectedRows", "影响行数", "INT", "-", "Y", "-"));
    }
    // buildFromQueryConditions / buildFromQueryFields / ... 走 TableMetaService.getTables 取
    // ColumnMeta.remarks 作中文注释、type 作 dataType、size 作长度、!nullable 作必填。
    // 备注列拼接：mapping 来源为 REQUEST/CONST=xx/CALC=xx；主键/唯一索引额外附注。
}
```

（各 `buildFromXxx` 方法参考 `TableMetaService.getColumns` 返回的 `ColumnMeta` 结构；找不到表结构元数据时中文含义留空但不报错。）

- [ ] **Step 5**：Controller

```java
@RestController
@RequestMapping("/api/interface")
@RequiredArgsConstructor
public class InterfaceFieldSchemaController {

    private final InterfaceConfigService interfaceService;
    private final InterfaceFieldSchemaService schemaService;

    @GetMapping("/{id}/field-schema/export")
    @Operation(summary = "FN-07 导出接口字段清单 Excel（双 Sheet）")
    public ResponseEntity<byte[]> export(@PathVariable Long id) throws Exception {
        InterfaceConfig cfg = interfaceService.getById(id);
        byte[] data = schemaService.exportExcel(cfg);
        String filename = URLEncoder.encode(cfg.getName() + "_字段清单.xlsx",
            StandardCharsets.UTF_8.name());
        HttpHeaders h = new HttpHeaders();
        h.setContentType(MediaType.parseMediaType(
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"));
        h.set(HttpHeaders.CONTENT_DISPOSITION,
            "attachment; filename=\"" + filename + "\"; filename*=UTF-8''" + filename);
        return ResponseEntity.ok().headers(h).body(data);
    }
}
```

- [ ] **Step 6**：运行通过 + 全量回归

```bash
cd backend && mvn test -Dtest=FN07FieldSchemaTest -q 2>&1 | tail -15
cd backend && mvn test -q 2>&1 | tail -15
```

- [ ] **Step 7**：提交

```bash
git add backend/src/main/java/com/powergateway/model/dto/FieldSchemaRow.java \
        backend/src/main/java/com/powergateway/service/InterfaceFieldSchemaService.java \
        backend/src/main/java/com/powergateway/controller/InterfaceFieldSchemaController.java \
        backend/src/test/java/com/powergateway/FN07FieldSchemaTest.java
git commit -m "feat(FN-07): 字段清单导出 Excel 双 Sheet（请求/响应）"
```

---

## Task 7: 前端字段清单下载按钮 + 通用 downloadBlob · FN-07

**Files:**
- Create: `frontend/src/utils/download.js`
- Create: `frontend/src/api/interfaceFieldSchema.js`
- Modify: `frontend/src/views/interface/InterfaceList.vue`（新增「导出字段」按钮）
- Modify: `frontend/src/api/interface.js`（复用 request）

**Interfaces:**
- Produces: `downloadBlob(blob, filename)` 全局工具
- Consumes: `GET /api/interface/{id}/field-schema/export`

- [ ] **Step 1**：新建 `frontend/src/utils/download.js`

```js
/** FN-07/09/10/11 通用 Blob 下载工具。 */
export function downloadBlob(blob, filename) {
  const url = window.URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = filename
  document.body.appendChild(a)
  a.click()
  setTimeout(() => {
    document.body.removeChild(a)
    window.URL.revokeObjectURL(url)
  }, 0)
}
```

- [ ] **Step 2**：新建 `frontend/src/api/interfaceFieldSchema.js`

```js
import request from './request'

export function exportFieldSchema(id) {
  return request.get(`/interface/${id}/field-schema/export`, {
    responseType: 'blob'
  })
}
```

- [ ] **Step 3**：`InterfaceList.vue` 操作列末尾追加

```vue
<el-button size="small" @click="handleExportFields(row)">导出字段</el-button>
```

```js
import { exportFieldSchema } from '@/api/interfaceFieldSchema'
import { downloadBlob } from '@/utils/download'

async function handleExportFields(row) {
  const blob = await exportFieldSchema(row.id)
  downloadBlob(blob, `${row.name}_字段清单.xlsx`)
}
```

- [ ] **Step 4**：手工验证：随便选一个已发布 SELECT 接口点「导出字段」，下载 xlsx，打开验证双 Sheet 与列头。

- [ ] **Step 5**：提交

```bash
git add frontend/src/utils/download.js \
        frontend/src/api/interfaceFieldSchema.js \
        frontend/src/views/interface/InterfaceList.vue
git commit -m "feat(FN-07): 前端接口列表新增「导出字段」按钮 + 通用 downloadBlob"
```

---

## Task 8: 各列表页 exportList Service 方法 · FN-10 后端

**Files:**
- Modify: `backend/.../service/InterfaceConfigService.java`（`exportList(String name) : byte[]`）
- Modify: `backend/.../service/TemplateService.java`
- Modify: `backend/.../service/ChannelConfigService.java`
- Modify: `backend/.../service/PortRouteService.java`
- Modify: `backend/.../service/PerfStatService.java`
- Modify: `backend/.../service/DbConnectionService.java`（**导出不含明文密码**，password 列固定输出 `***`）
- Modify: `backend/.../service/SqlAuditLogService.java`
- Modify: 缓存管理 service（`CacheService` 或 `QueryCacheManager` 侧）
- Test: `backend/src/test/java/com/powergateway/FN10InterfaceListExportTest.java`

**Interfaces:**
- Produces: 每个 service 一个 `exportList(<过滤参数>) : byte[]`
- Consumes: `ExcelExportUtil.export(sheetName, columns, rows)`

- [ ] **Step 1**：写失败测试（以 InterfaceConfig 为代表）

```java
@Test
void 按name过滤_导出行数与列表一致() throws Exception {
    Long id = createInterface("测试导出A");
    createInterface("测试导出B");
    createInterface("别名");
    byte[] bytes = interfaceService.exportList("测试导出");
    try (Workbook wb = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
        Sheet s = wb.getSheetAt(0);
        assertEquals(2, s.getLastRowNum(),
            "过滤 name='测试导出' 应导出 2 行数据 + 1 行 header");
    }
}
```

- [ ] **Step 2**：运行失败

- [ ] **Step 3**：`InterfaceConfigService` 新增

```java
public byte[] exportList(String name) {
    List<InterfaceConfig> rows = list(name, 1, 10000);
    List<ExcelExportUtil.Column<InterfaceConfig>> cols = Arrays.asList(
        new ExcelExportUtil.Column<>("接口名称",   InterfaceConfig::getName),
        new ExcelExportUtil.Column<>("类型",       InterfaceConfig::getType),
        new ExcelExportUtil.Column<>("状态",       r -> statusLabel(r.getStatus())),
        new ExcelExportUtil.Column<>("访问路径",   InterfaceConfig::getPath),
        new ExcelExportUtil.Column<>("响应格式",   InterfaceConfig::getResponseFormat),
        new ExcelExportUtil.Column<>("创建时间",   r -> fmtDt(r.getCreateTime()))
    );
    return ExcelExportUtil.export("接口列表", cols, rows);
}

private String statusLabel(String s) {
    if (s == null) return "";
    switch (s) {
        case "published": return "已发布";
        case "draft":     return "草稿";
        case "disabled":  return "已禁用";
        default: return s;
    }
}
private String fmtDt(java.util.Date d) {
    return d == null ? "" : new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(d);
}
```

- [ ] **Step 4**：`TemplateService.exportList(String name, String srcFormat, String targetFormat)`

```java
public byte[] exportList(String name, String srcFormat, String targetFormat) {
    List<ConvertTemplate> rows = search(name, srcFormat, targetFormat, 1, 10000);
    return ExcelExportUtil.export("转换模板", Arrays.asList(
        new ExcelExportUtil.Column<>("模板名称", ConvertTemplate::getName),
        new ExcelExportUtil.Column<>("源格式",   ConvertTemplate::getSrcFormat),
        new ExcelExportUtil.Column<>("目标格式", ConvertTemplate::getTargetFormat),
        new ExcelExportUtil.Column<>("版本",     ConvertTemplate::getVersion),
        new ExcelExportUtil.Column<>("是否最新", r -> r.getIsLatest() != null && r.getIsLatest() ? "Y" : "N")
    ), rows);
}
```

- [ ] **Step 5**：`ChannelConfigService.exportList(String keyword)`

```java
public byte[] exportList(String keyword) {
    List<ChannelConfig> rows = list(keyword);
    return ExcelExportUtil.export("渠道模板", Arrays.asList(
        new ExcelExportUtil.Column<>("渠道编码",   ChannelConfig::getChannelCode),
        new ExcelExportUtil.Column<>("渠道名称",   ChannelConfig::getChannelName),
        new ExcelExportUtil.Column<>("识别字段",   ChannelConfig::getIdentifierField),
        new ExcelExportUtil.Column<>("识别值",     ChannelConfig::getIdentifierValue)
    ), rows);
}
```

- [ ] **Step 6**：`PortRouteService.exportList(String channelCode)`

```java
public byte[] exportList(String channelCode) {
    List<PortRoute> rows = list(channelCode);
    return ExcelExportUtil.export("端口路由", Arrays.asList(
        new ExcelExportUtil.Column<>("渠道编码",     PortRoute::getChannelCode),
        new ExcelExportUtil.Column<>("下游 URL",     PortRoute::getTargetUrl),
        new ExcelExportUtil.Column<>("请求方法",     PortRoute::getMethod),
        new ExcelExportUtil.Column<>("超时(ms)",     PortRoute::getTimeoutMs)
    ), rows);
}
```

- [ ] **Step 7**：`PerfStatService.exportList(...)`（沿用现有 `list` 方法过滤参数）

```java
public byte[] exportList(String interfaceName, String opType,
                         String startTime, String endTime) {
    List<PerfStat> rows = list(interfaceName, opType, startTime, endTime, 1, 10000);
    return ExcelExportUtil.export("性能统计", Arrays.asList(
        new ExcelExportUtil.Column<>("接口名",   PerfStat::getInterfaceName),
        new ExcelExportUtil.Column<>("操作类型", PerfStat::getOpType),
        new ExcelExportUtil.Column<>("耗时(ms)", PerfStat::getCostMs),
        new ExcelExportUtil.Column<>("结果",     PerfStat::getResult),
        new ExcelExportUtil.Column<>("时间",     r -> fmtDt(r.getCreateTime()))
    ), rows);
}
```

- [ ] **Step 8**：`DbConnectionService.exportList(String keyword)`

```java
public byte[] exportList(String keyword) {
    List<DbConnection> rows = list(keyword);
    return ExcelExportUtil.export("数据源列表", Arrays.asList(
        new ExcelExportUtil.Column<>("名称",  DbConnection::getName),
        new ExcelExportUtil.Column<>("类型",  DbConnection::getDbType),
        new ExcelExportUtil.Column<>("URL",   DbConnection::getUrl),
        new ExcelExportUtil.Column<>("账号",  DbConnection::getUsername),
        new ExcelExportUtil.Column<>("密码",  r -> "***"),   // 安全：不导出明文
        new ExcelExportUtil.Column<>("环境",  DbConnection::getEnv)
    ), rows);
}
```

- [ ] **Step 9**：`SqlAuditLogService.exportList(...)` 与缓存 `exportList()` 类似照抄字段。缓存导出至少含：接口名 / 缓存 Key / TTL / 命中次数 / 未命中次数 / 最近命中时间。

- [ ] **Step 10**：运行 `FN10InterfaceListExportTest` 通过 + 全量回归

- [ ] **Step 11**：提交

```bash
git add backend/src/main/java/com/powergateway/service/*.java \
        backend/src/test/java/com/powergateway/FN10InterfaceListExportTest.java
git commit -m "feat(FN-10): 9 个列表 Service 新增 exportList，复用 ExcelExportUtil"
```

---

## Task 9: 各列表 Controller 暴露 export 端点 · FN-10 后端

**Files:**
- Modify: `backend/.../controller/InterfaceConfigController.java`
- Modify: `backend/.../controller/TemplateController.java`
- Modify: `backend/.../controller/ChannelConfigController.java`
- Modify: `backend/.../controller/PortRouteController.java`
- Modify: `backend/.../controller/StatsController.java`（或 `PerfStatController`）
- Modify: `backend/.../controller/DbConnectionController.java`
- Modify: `backend/.../controller/CacheController.java`
- Modify: `backend/.../controller/SqlAuditLogController.java`

**Interfaces:**
- Produces: 每 Controller 一个 `GET .../list/export` 返回 xlsx byte[]

- [ ] **Step 1**：以 `InterfaceConfigController` 为模板

```java
@GetMapping("/list/export")
@Operation(summary = "FN-10 导出接口列表 Excel")
public ResponseEntity<byte[]> exportList(@RequestParam(required = false) String name) throws Exception {
    byte[] data = service.exportList(name);
    return excelResponse(data, "接口列表_" + tsSuffix() + ".xlsx");
}
```

抽公共方法（放 Controller 内 private 或工具类均可）：

```java
static ResponseEntity<byte[]> excelResponse(byte[] data, String rawName) throws Exception {
    String encoded = URLEncoder.encode(rawName, StandardCharsets.UTF_8.name());
    HttpHeaders h = new HttpHeaders();
    h.setContentType(MediaType.parseMediaType(
        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"));
    h.set(HttpHeaders.CONTENT_DISPOSITION,
        "attachment; filename=\"" + encoded + "\"; filename*=UTF-8''" + encoded);
    return ResponseEntity.ok().headers(h).body(data);
}
static String tsSuffix() {
    return new SimpleDateFormat("yyyyMMddHHmm").format(new Date());
}
```

- [ ] **Step 2**：其余 7 个 Controller 照抄，`@RequestParam` 传对应过滤参数。

- [ ] **Step 3**：`SysLogController` 已存在 `exportExcel` 端点（SYS-1），**不改**；SqlAuditLog 与其在同一前端 Tab，本 Task 只新增 SqlAuditLog 侧的 `list/export` 端点。

- [ ] **Step 4**：全量回归

```bash
cd backend && mvn test -q 2>&1 | tail -15
```

- [ ] **Step 5**：提交

```bash
git add backend/src/main/java/com/powergateway/controller/*.java
git commit -m "feat(FN-10): 8 个 Controller 暴露 GET /list/export xlsx 下载端点"
```

---

## Task 10: 前端各列表页「导出报表」按钮 · FN-10 前端

**Files:**
- Modify: `frontend/src/views/interface/InterfaceList.vue`
- Modify: `frontend/src/views/convert/Template.vue`
- Modify: `frontend/src/views/convert/ChannelConfig.vue`
- Modify: `frontend/src/views/convert/PortRoute.vue`
- Modify: `frontend/src/views/system/LogList.vue`（SQL 审计 Tab；操作日志 Tab 已有导出按钮，不改）
- Modify: `frontend/src/views/system/Stats.vue`
- Modify: `frontend/src/views/interface/CacheList.vue`
- Modify: `frontend/src/views/db/Datasource.vue`
- Modify: `frontend/src/api/interface.js` / `frontend/src/api/template.js` / `frontend/src/api/channel.js` / 等各 api 文件新增 `exportXxxList`

**Interfaces:**
- Consumes: `GET /api/{module}/list/export`，`responseType: 'blob'`
- Produces: 页面顶部工具栏「导出报表」按钮

- [ ] **Step 1**：在 `frontend/src/api/interface.js` 追加

```js
export function exportInterfaceList(name) {
  return request.get('/interface/list/export', {
    params: { name },
    responseType: 'blob'
  })
}
```

其他模块的 api 文件同样补对应导出方法（`exportTemplateList` / `exportChannelList` / `exportPortRouteList` / `exportPerfStatList` / `exportDbList` / `exportCacheList` / `exportSqlAuditList`）。

- [ ] **Step 2**：每个列表页搜索工具栏末尾插入

```vue
<el-button type="default" @click="handleExportReport">
  <el-icon><Download /></el-icon>导出报表
</el-button>
```

```js
import { downloadBlob } from '@/utils/download'
import { exportInterfaceList } from '@/api/interface'

async function handleExportReport() {
  const blob = await exportInterfaceList(searchName.value || undefined)
  downloadBlob(blob, `接口列表_${Date.now()}.xlsx`)
}
```

- [ ] **Step 3**：手工验证：每个页面点「导出报表」应下载 xlsx，打开列头与页面表格一致。

- [ ] **Step 4**：提交

```bash
git add frontend/src/views frontend/src/api
git commit -m "feat(FN-10): 8 个列表页新增「导出报表」按钮"
```

---

## Task 11: 前端 CodeEditor 组件 + MessageDebug 集成 · FN-08

**Files:**
- Modify: `frontend/package.json`（新增依赖）
- Create: `frontend/src/components/tools/CodeEditor.vue`
- Modify: `frontend/src/views/tools/MessageDebug.vue`

**Interfaces:**
- Produces: `<CodeEditor v-model="text" language="json|xml|csv" />`
- Consumes: `codemirror@5.65.16` + `vue-codemirror@6.1.1`

- [ ] **Step 1**：新增前端依赖

```bash
cd frontend && npm install codemirror@5.65.16 vue-codemirror@6.1.1
```

确认 `package.json` 已写入。

- [ ] **Step 2**：新建 `frontend/src/components/tools/CodeEditor.vue`

```vue
<template>
  <div ref="host" class="code-editor" />
</template>

<script setup>
import { ref, watch, onMounted, onBeforeUnmount } from 'vue'
import CodeMirror from 'codemirror'
import 'codemirror/mode/javascript/javascript'
import 'codemirror/mode/xml/xml'
import 'codemirror/lib/codemirror.css'
import 'codemirror/theme/idea.css'

const props = defineProps({
  modelValue: { type: String, default: '' },
  language:   { type: String, default: 'json' } // json / xml / csv
})
const emit = defineEmits(['update:modelValue'])

const host = ref(null)
let cm = null

function modeOf(lang) {
  if (lang === 'xml') return 'xml'
  if (lang === 'json') return { name: 'javascript', json: true }
  return null // csv 走纯文本
}

onMounted(() => {
  cm = CodeMirror(host.value, {
    value: props.modelValue || '',
    mode: modeOf(props.language),
    theme: 'idea',
    lineNumbers: true,
    lineWrapping: true,
    tabSize: 2
  })
  cm.on('change', () => emit('update:modelValue', cm.getValue()))
})

onBeforeUnmount(() => { cm = null })

watch(() => props.modelValue, v => {
  if (cm && cm.getValue() !== v) cm.setValue(v || '')
})

watch(() => props.language, lang => {
  if (cm) cm.setOption('mode', modeOf(lang))
})
</script>

<style scoped>
.code-editor { border: 1px solid var(--el-border-color, #dcdfe6); border-radius: 4px; }
.code-editor :deep(.CodeMirror) { min-height: 220px; font-family: Menlo, Consolas, monospace; }
</style>
```

- [ ] **Step 3**：`MessageDebug.vue` 输入区替换。定位当前 `<el-input type="textarea" v-model="inputText" />`，替换为：

```vue
<el-radio-group v-model="inputLang" style="margin-bottom:8px">
  <el-radio-button value="json">JSON</el-radio-button>
  <el-radio-button value="xml">XML</el-radio-button>
  <el-radio-button value="csv">CSV</el-radio-button>
</el-radio-group>
<CodeEditor v-model="inputText" :language="inputLang" />
```

`data`/`setup` 里新增 `const inputLang = ref('json')`。切换时**不清空** `inputText`（只切 mode，Task 已由组件 watch 处理）。

- [ ] **Step 4**：调用 `POST /api/convert` 时透传 `srcFormat`

```js
await convertMessage({
  channelCode: form.channelCode,
  srcFormat: inputLang.value.toUpperCase() === 'CSV' ? 'CSV' :
             inputLang.value.toUpperCase() === 'XML' ? 'XML' : 'JSON',
  payload: inputText.value
})
```

调用 `POST /api/exec/{id}` 时在 `Accept` 头带 `application/xml` 等（依 `inputLang` 决定）以联动 FN-06。

- [ ] **Step 5**：手工验证：选 XML → 输入 `<root><id>1</id></root>` 有语法高亮；切到 JSON 内容仍在。

- [ ] **Step 6**：提交

```bash
git add frontend/package.json frontend/package-lock.json \
        frontend/src/components/tools/CodeEditor.vue \
        frontend/src/views/tools/MessageDebug.vue
git commit -m "feat(FN-08): MessageDebug 引入 CodeMirror 语法高亮，支持 JSON/XML/CSV 切换"
```

---

## Task 12: 接口文档 Service + Controller · FN-09 后端

**Files:**
- Create: `backend/src/main/java/com/powergateway/model/dto/DocumentModel.java`
- Create: `backend/src/main/java/com/powergateway/service/InterfaceDocumentService.java`
- Create: `backend/src/main/java/com/powergateway/controller/InterfaceDocumentController.java`
- Test: `backend/src/test/java/com/powergateway/FN09DocumentServiceTest.java`

**Interfaces:**
- Produces:
  - `GET /api/doc/transform/list` → `Result<List<DocumentSummary>>`
  - `GET /api/doc/transform/{templateId}?format=md|html` → 文档字符串
  - `GET /api/doc/transform/export` → zip
  - `GET /api/doc/visual/list`
  - `GET /api/doc/visual/{interfaceId}?format=md|html`
  - `GET /api/doc/visual/export` → zip
- Consumes: `TemplateService.getById(...)`、`InterfaceConfigService.getById(...)`、`ChannelConfigService`、`PortRouteService`、`TableMetaService.getTables(dbId)`

- [ ] **Step 1**：定义 `DocumentModel.java`

```java
package com.powergateway.model.dto;

import lombok.Data;
import java.util.*;

@Data
public class DocumentModel {
    private String type;              // TRANSFORM / VISUAL
    private String title;             // 顶级标题
    private String summary;           // 一句话概览
    private Map<String, Object> meta; // 基本信息 KV
    private List<Section> sections;   // 章节列表

    @Data
    public static class Section {
        private String heading;
        private String description;
        private List<List<String>> table;  // 首行为表头
        private String codeBlock;          // 代码块内容
        private String codeLang;           // json / xml / sql
    }
}
```

- [ ] **Step 2**：写失败测试 `FN09DocumentServiceTest.java`

```java
@Test
void 转换接口Markdown_含渠道模板字段映射() {
    Long tplId = createTemplate("测试模板", "JSON", "XML");
    String md = docService.buildMarkdownForTemplate(tplId);
    assertTrue(md.contains("# "), "Markdown 必须包含一级标题");
    assertTrue(md.contains("测试模板"));
    assertTrue(md.contains("JSON"));
    assertTrue(md.contains("XML"));
    assertTrue(md.contains("|"), "字段映射应以 Markdown 表格呈现");
}

@Test
void 可视化接口HTML_含请求响应字段表和示例() {
    Long id = createSelectInterface();
    String html = docService.buildHtmlForVisual(id);
    assertTrue(html.startsWith("<!DOCTYPE html>") || html.startsWith("<html"));
    assertTrue(html.contains("请求字段"));
    assertTrue(html.contains("响应字段"));
    assertTrue(html.contains("<table"));
}

@Test
void 全量打包zip_manifest条目数与遍历一致() throws Exception {
    createTemplate("A", "JSON", "XML");
    createTemplate("B", "JSON", "CSV");
    byte[] zip = docService.exportAllTransformZip();
    Map<String, byte[]> entries = unzip(zip);
    assertTrue(entries.containsKey("manifest.json"));
    long fileCount = entries.keySet().stream()
        .filter(k -> !k.equals("manifest.json"))
        .count();
    assertEquals(2 * 2, fileCount, "每模板两份（md + html），共 4 个");
}
```

- [ ] **Step 3**：运行失败

- [ ] **Step 4**：实现 `InterfaceDocumentService.java`

```java
@Service
@RequiredArgsConstructor
public class InterfaceDocumentService {

    private final TemplateService templateService;
    private final ChannelConfigService channelService;
    private final PortRouteService portRouteService;
    private final InterfaceConfigService interfaceService;
    private final TableMetaService tableMetaService;
    private final ObjectMapper objectMapper;

    public String buildMarkdownForTemplate(Long id) {
        DocumentModel m = buildTransformModel(id);
        return renderMarkdown(m);
    }

    public String buildHtmlForTemplate(Long id) {
        return renderHtml(buildTransformModel(id));
    }

    public String buildMarkdownForVisual(Long id) {
        return renderMarkdown(buildVisualModel(id));
    }

    public String buildHtmlForVisual(Long id) {
        return renderHtml(buildVisualModel(id));
    }

    public byte[] exportAllTransformZip() { ... }
    public byte[] exportAllVisualZip()    { ... }

    // ---- model 构造 ----
    private DocumentModel buildTransformModel(Long tplId) {
        // 组装 meta：名称、srcFormat、targetFormat、渠道、端口路由
        // sections：字段映射表、字段加工规则表、端口路由 URL/timeout、样例入参/出参
        ...
    }

    private DocumentModel buildVisualModel(Long id) {
        // meta：接口名、类型、db、response_format、path
        // sections：请求字段表、响应字段表、SQL 示例、样例 JSON / XML
        ...
    }

    // ---- 渲染 ----
    private String renderMarkdown(DocumentModel m) {
        StringBuilder sb = new StringBuilder();
        sb.append("# ").append(m.getTitle()).append("\n\n");
        sb.append("> ").append(m.getSummary()).append("\n\n");
        m.getMeta().forEach((k, v) -> sb.append("- **").append(k).append("**: ").append(v).append("\n"));
        sb.append("\n");
        for (DocumentModel.Section s : m.getSections()) {
            sb.append("## ").append(s.getHeading()).append("\n\n");
            if (s.getDescription() != null) sb.append(s.getDescription()).append("\n\n");
            if (s.getTable() != null && !s.getTable().isEmpty()) {
                List<List<String>> t = s.getTable();
                sb.append("| ").append(String.join(" | ", t.get(0))).append(" |\n");
                sb.append("|").append(repeat("---|", t.get(0).size())).append("\n");
                for (int i = 1; i < t.size(); i++) {
                    sb.append("| ").append(String.join(" | ", t.get(i))).append(" |\n");
                }
                sb.append("\n");
            }
            if (s.getCodeBlock() != null) {
                sb.append("```").append(s.getCodeLang() == null ? "" : s.getCodeLang()).append("\n");
                sb.append(s.getCodeBlock()).append("\n```\n\n");
            }
        }
        return sb.toString();
    }

    private String renderHtml(DocumentModel m) {
        StringBuilder sb = new StringBuilder();
        sb.append("<!DOCTYPE html>\n<html lang=\"zh-CN\"><head>\n")
          .append("<meta charset=\"UTF-8\">\n")
          .append("<title>").append(esc(m.getTitle())).append("</title>\n")
          .append("<style>")
          .append("body{font-family:-apple-system,'Microsoft YaHei',sans-serif;max-width:920px;margin:24px auto;padding:0 16px;color:#222}")
          .append("table{border-collapse:collapse;width:100%;margin:12px 0}")
          .append("th,td{border:1px solid #d0d7de;padding:6px 10px;text-align:left}")
          .append("th{background:#f5f7fa}")
          .append("pre{background:#f5f7fa;padding:10px;border-radius:4px;overflow:auto}")
          .append("</style>\n</head><body>\n");
        sb.append("<h1>").append(esc(m.getTitle())).append("</h1>\n");
        sb.append("<p>").append(esc(m.getSummary())).append("</p>\n");
        sb.append("<ul>");
        m.getMeta().forEach((k, v) -> sb.append("<li><b>").append(esc(k))
            .append("</b>: ").append(esc(String.valueOf(v))).append("</li>"));
        sb.append("</ul>\n");
        for (DocumentModel.Section s : m.getSections()) {
            sb.append("<h2>").append(esc(s.getHeading())).append("</h2>\n");
            if (s.getDescription() != null)
                sb.append("<p>").append(esc(s.getDescription())).append("</p>\n");
            if (s.getTable() != null && !s.getTable().isEmpty()) {
                sb.append("<table>\n<tr>");
                for (String h : s.getTable().get(0))
                    sb.append("<th>").append(esc(h)).append("</th>");
                sb.append("</tr>\n");
                for (int i = 1; i < s.getTable().size(); i++) {
                    sb.append("<tr>");
                    for (String c : s.getTable().get(i))
                        sb.append("<td>").append(esc(c)).append("</td>");
                    sb.append("</tr>\n");
                }
                sb.append("</table>\n");
            }
            if (s.getCodeBlock() != null) {
                sb.append("<pre><code>").append(esc(s.getCodeBlock())).append("</code></pre>\n");
            }
        }
        sb.append("</body></html>\n");
        return sb.toString();
    }

    private String esc(String s) {
        if (s == null) return "";
        return s.replace("&","&amp;").replace("<","&lt;").replace(">","&gt;");
    }
    private String repeat(String s, int n) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < n; i++) sb.append(s);
        return sb.toString();
    }

    // ---- zip 打包 ----
    private byte[] pack(Map<String, byte[]> entries) {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream();
             ZipOutputStream zos = new ZipOutputStream(out)) {
            for (Map.Entry<String, byte[]> e : entries.entrySet()) {
                zos.putNextEntry(new ZipEntry(e.getKey()));
                zos.write(e.getValue());
                zos.closeEntry();
            }
            zos.finish();
            return out.toByteArray();
        } catch (Exception e) {
            throw new BusinessException(500, "zip 打包失败: " + e.getMessage());
        }
    }
}
```

- [ ] **Step 5**：`InterfaceDocumentController.java`

```java
@RestController
@RequestMapping("/api/doc")
@RequiredArgsConstructor
@Tag(name = "接口文档")
public class InterfaceDocumentController {

    private final InterfaceDocumentService docService;
    private final TemplateService templateService;
    private final InterfaceConfigService interfaceService;

    @GetMapping("/transform/list")
    public Result<List<Map<String,Object>>> transformList() {
        return Result.success(templateService.listAllSummary());
    }
    @GetMapping("/visual/list")
    public Result<List<Map<String,Object>>> visualList() {
        return Result.success(interfaceService.listAllSummary());
    }

    @GetMapping("/transform/{id}")
    public ResponseEntity<byte[]> transformDoc(@PathVariable Long id,
                                               @RequestParam(defaultValue = "md") String format) {
        return docResponse(
            "html".equalsIgnoreCase(format) ? docService.buildHtmlForTemplate(id) : docService.buildMarkdownForTemplate(id),
            format,
            templateService.getById(id).getName() + "_文档");
    }
    @GetMapping("/visual/{id}")
    public ResponseEntity<byte[]> visualDoc(@PathVariable Long id,
                                            @RequestParam(defaultValue = "md") String format) {
        return docResponse(
            "html".equalsIgnoreCase(format) ? docService.buildHtmlForVisual(id) : docService.buildMarkdownForVisual(id),
            format,
            interfaceService.getById(id).getName() + "_文档");
    }

    @GetMapping("/transform/export")
    public ResponseEntity<byte[]> exportTransform() {
        return zipResponse(docService.exportAllTransformZip(), "转换接口文档_" + ts() + ".zip");
    }
    @GetMapping("/visual/export")
    public ResponseEntity<byte[]> exportVisual() {
        return zipResponse(docService.exportAllVisualZip(), "可视化接口文档_" + ts() + ".zip");
    }
    private ResponseEntity<byte[]> docResponse(byte[] body, String format, String baseName) {
        String ext = "html".equalsIgnoreCase(format) ? "html" : "md";
        MediaType type = "html".equalsIgnoreCase(format)
                ? MediaType.TEXT_HTML : MediaType.parseMediaType("text/markdown");
        String filename = URLEncoder.encode(baseName + "." + ext, StandardCharsets.UTF_8);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename*=UTF-8''" + filename)
                .contentType(type)
                .body(body);
    }

    private ResponseEntity<byte[]> zipResponse(byte[] body, String baseName) {
        String filename = URLEncoder.encode(baseName, StandardCharsets.UTF_8);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename*=UTF-8''" + filename)
                .contentType(MediaType.parseMediaType("application/zip"))
                .body(body);
    }

    private String ts() {
        return LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
    }
}
```

（`listAllSummary` 是 TemplateService / InterfaceConfigService 新增的辅助方法：返回 `[{id, name, type/srcFormat, ...}]`，无需分页。）

- [ ] **Step 6**：运行 `FN09DocumentServiceTest` 通过 + 全量回归

- [ ] **Step 7**：提交

```bash
git add backend/src/main/java/com/powergateway/model/dto/DocumentModel.java \
        backend/src/main/java/com/powergateway/service/InterfaceDocumentService.java \
        backend/src/main/java/com/powergateway/controller/InterfaceDocumentController.java \
        backend/src/main/java/com/powergateway/service/TemplateService.java \
        backend/src/main/java/com/powergateway/service/InterfaceConfigService.java \
        backend/src/test/java/com/powergateway/FN09DocumentServiceTest.java
git commit -m "feat(FN-09): 接口文档 Service + Controller（Markdown/HTML + 全量 zip）"
```

---

## Task 13: 接口文档前端页面 · FN-09 前端

**Files:**
- Create: `frontend/src/api/interfaceDoc.js`
- Create: `frontend/src/views/interface/InterfaceDocument.vue`
- Modify: `frontend/src/router/index.js`（append-only 新增 `/interface/document`）

**Interfaces:**
- Consumes: `/api/doc/transform/list` / `/api/doc/visual/list` / `/api/doc/{type}/{id}?format=md|html` / `/api/doc/{type}/export`

- [ ] **Step 1**：`frontend/src/api/interfaceDoc.js`

```js
import request from './request'

export const listTransformDoc = () => request.get('/doc/transform/list')
export const listVisualDoc    = () => request.get('/doc/visual/list')
export const previewDoc = (kind, id, format = 'html') =>
  request.get(`/doc/${kind}/${id}`, { params: { format }, responseType: 'blob' })
export const exportAllDoc = (kind) =>
  request.get(`/doc/${kind}/export`, { responseType: 'blob' })
```

- [ ] **Step 2**：`InterfaceDocument.vue`

```vue
<template>
  <div class="doc-page">
    <el-tabs v-model="tab">
      <el-tab-pane label="转换接口" name="transform">
        <div class="toolbar">
          <el-input v-model="tfKw" placeholder="搜索模板名称" style="width:220px" />
          <el-button type="primary" @click="exportAll('transform')">全量导出 zip</el-button>
        </div>
        <el-table :data="filteredTf" border stripe>
          <el-table-column prop="name" label="模板名称" />
          <el-table-column prop="srcFormat" label="源格式" width="100" />
          <el-table-column prop="targetFormat" label="目标格式" width="100" />
          <el-table-column label="操作" width="260">
            <template #default="{ row }">
              <el-button size="small" @click="preview('transform', row)">预览</el-button>
              <el-dropdown @command="c => download('transform', row, c)">
                <el-button size="small">下载 <el-icon><ArrowDown /></el-icon></el-button>
                <template #dropdown>
                  <el-dropdown-menu>
                    <el-dropdown-item command="md">Markdown</el-dropdown-item>
                    <el-dropdown-item command="html">HTML</el-dropdown-item>
                  </el-dropdown-menu>
                </template>
              </el-dropdown>
            </template>
          </el-table-column>
        </el-table>
      </el-tab-pane>
      <el-tab-pane label="可视化接口" name="visual">
        <!-- 同上结构，字段: name / type / status -->
      </el-tab-pane>
    </el-tabs>

    <el-drawer v-model="previewOpen" size="60%" :title="previewTitle">
      <iframe v-if="previewUrl" :src="previewUrl" style="width:100%;height:100%;border:0"></iframe>
    </el-drawer>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { listTransformDoc, listVisualDoc, previewDoc, exportAllDoc } from '@/api/interfaceDoc'
import { downloadBlob } from '@/utils/download'

const tab = ref('transform')
const tfList = ref([]); const vlList = ref([])
const tfKw = ref('')
const filteredTf = computed(() =>
  tfList.value.filter(r => !tfKw.value || r.name.includes(tfKw.value)))

async function load() {
  tfList.value = (await listTransformDoc()) || []
  vlList.value = (await listVisualDoc()) || []
}

const previewOpen = ref(false); const previewUrl = ref(''); const previewTitle = ref('')
async function preview(kind, row) {
  const blob = await previewDoc(kind, row.id, 'html')
  previewUrl.value = URL.createObjectURL(blob)
  previewTitle.value = row.name
  previewOpen.value = true
}

async function download(kind, row, format) {
  const blob = await previewDoc(kind, row.id, format)
  downloadBlob(blob, `${row.name}_文档.${format === 'md' ? 'md' : 'html'}`)
}

async function exportAll(kind) {
  const blob = await exportAllDoc(kind)
  downloadBlob(blob, `${kind === 'transform' ? '转换' : '可视化'}接口文档_${Date.now()}.zip`)
}

onMounted(load)
</script>
```

- [ ] **Step 3**：`router/index.js` **append-only** 追加

```js
{
  path: 'interface/document',
  name: 'InterfaceDocument',
  component: () => import('@/views/interface/InterfaceDocument.vue'),
  meta: { title: '接口文档' }
},
```

菜单挂点按 UX-B 决定，本单元不改 `SideMenu.vue`（除非本单元先合入且 UX-B 未定，则临时挂在「工具」下）。

- [ ] **Step 4**：手工验证：访问 `/interface/document`，两个 Tab 列表能列出、预览抽屉能渲染 HTML、下载 md/html/zip 三选正常。

- [ ] **Step 5**：提交

```bash
git add frontend/src/api/interfaceDoc.js \
        frontend/src/views/interface/InterfaceDocument.vue \
        frontend/src/router/index.js
git commit -m "feat(FN-09): 接口文档前端页面（预览抽屉 + 单份下载 + 全量 zip）"
```

---

## Task 14: 配置导入导出后端 · FN-11

**Files:**
- Create: `backend/src/main/java/com/powergateway/model/dto/ImportManifest.java`
- Create: `backend/src/main/java/com/powergateway/model/dto/ImportResult.java`
- Create: `backend/src/main/java/com/powergateway/service/ConfigExportService.java`
- Create: `backend/src/main/java/com/powergateway/service/ConfigImportService.java`
- Create: `backend/src/main/java/com/powergateway/controller/ConfigImportExportController.java`
- Test: `backend/src/test/java/com/powergateway/FN11ConfigExportServiceTest.java`
- Test: `backend/src/test/java/com/powergateway/FN11ConfigImportServiceTest.java`

**Interfaces:**
- Produces:
  - `GET /api/config/export/transform?ids=...&all=true` → zip
  - `GET /api/config/export/visual?ids=...&all=true` → zip
  - `POST /api/config/import/transform` multipart：`file`, `strategy=OVERWRITE|SKIP|ASK` → `ImportResult`
  - `POST /api/config/import/visual` 同上
  - `POST /api/config/import/{kind}/confirm`（ASK 二次确认，body 为条目决议列表）
- Consumes: `TemplateService` / `ChannelConfigService` / `PortRouteService` / `InterfaceConfigService`

- [ ] **Step 1**：DTO

```java
// ImportManifest.java
@Data
public class ImportManifest {
    private String kind;              // TRANSFORM / VISUAL
    private String exportedAt;
    private String version;           // CHG-020
    private List<Entry> entries;
    @Data
    public static class Entry {
        private String path;          // templates/xxx.json
        private String primaryKey;    // "name|JSON|XML" 之类
    }
}
```

```java
// ImportResult.java
@Data
public class ImportResult {
    private int imported;
    private int updated;
    private int skipped;
    private int failed;
    private List<Item> items;
    @Data
    public static class Item {
        private String name;
        private String status;        // NEW / OVERWRITE / SKIP / CONFLICT / FAIL
        private Long existsId;        // 冲突时旧记录 id
        private String message;
    }
}
```

- [ ] **Step 2**：`FN11ConfigExportServiceTest`

```java
@Test
void 全量导出_zip结构含manifest与条目() throws Exception {
    Long tid = createTemplate("模板A", "JSON", "XML");
    byte[] zip = exportService.exportAllTransform();
    Map<String, byte[]> entries = unzip(zip);
    assertTrue(entries.containsKey("manifest.json"));
    assertTrue(entries.keySet().stream().anyMatch(k -> k.startsWith("templates/")));
    ImportManifest manifest = objectMapper.readValue(entries.get("manifest.json"), ImportManifest.class);
    assertEquals("TRANSFORM", manifest.getKind());
    assertFalse(manifest.getEntries().isEmpty());
}

@Test
void 导出内容不含id与时间戳() throws Exception {
    Long tid = createTemplate("模板B", "JSON", "XML");
    byte[] zip = exportService.exportAllTransform();
    Map<String, byte[]> entries = unzip(zip);
    String json = new String(entries.get("templates/模板B.json"), StandardCharsets.UTF_8);
    assertFalse(json.contains("\"id\":"), "导出的模板 JSON 不应含 id 字段");
    assertFalse(json.contains("createTime") && json.contains(String.valueOf(tid)));
}

@Test
void 选中3条导出_zip内3个模板文件() throws Exception {
    Long a = createTemplate("A", "JSON", "XML");
    Long b = createTemplate("B", "JSON", "XML");
    Long c = createTemplate("C", "JSON", "XML");
    createTemplate("D", "JSON", "XML");  // 不选
    byte[] zip = exportService.exportSelectedTransform(Arrays.asList(a, b, c));
    Map<String, byte[]> entries = unzip(zip);
    long tplCount = entries.keySet().stream()
        .filter(k -> k.startsWith("templates/")).count();
    assertEquals(3, tplCount);
}
```

- [ ] **Step 3**：`FN11ConfigImportServiceTest`

```java
@Test
void OVERWRITE_已存在则更新() throws Exception {
    Long id = createTemplate("重复", "JSON", "XML");
    byte[] zip = buildZipWithTemplate("重复", "JSON", "XML", "新映射");
    ImportResult r = importService.importTransform(zip, "OVERWRITE");
    assertEquals(1, r.getUpdated());
    assertEquals(0, r.getImported());
    assertEquals("新映射", templateService.getById(id).getMappingRule());
}

@Test
void SKIP_已存在则跳过() throws Exception {
    createTemplate("重复", "JSON", "XML");
    byte[] zip = buildZipWithTemplate("重复", "JSON", "XML", "新映射");
    ImportResult r = importService.importTransform(zip, "SKIP");
    assertEquals(0, r.getUpdated());
    assertEquals(1, r.getSkipped());
}

@Test
void ASK_不落库_返回preview() throws Exception {
    createTemplate("重复", "JSON", "XML");
    byte[] zip = buildZipWithTemplate("重复", "JSON", "XML", "新映射");
    ImportResult r = importService.importTransform(zip, "ASK");
    assertEquals(0, r.getUpdated());
    assertEquals(0, r.getImported());
    assertEquals(1, r.getItems().size());
    assertEquals("CONFLICT", r.getItems().get(0).getStatus());
}

@Test
void 事务性_条目损坏时全量回滚() throws Exception {
    long before = templateService.count();
    byte[] zip = buildCorruptZip();  // 含 1 个好条目 + 1 个损坏 JSON
    assertThrows(BusinessException.class,
        () -> importService.importTransform(zip, "OVERWRITE"));
    assertEquals(before, templateService.count(), "损坏时应回滚，不留半成品");
}

@Test
void 转换模板主键_按name加srcFormat加targetFormat三元组() throws Exception {
    createTemplate("同名", "JSON", "XML");
    byte[] zipDiff = buildZipWithTemplate("同名", "XML", "JSON", "另一个");
    ImportResult r = importService.importTransform(zipDiff, "OVERWRITE");
    assertEquals(1, r.getImported(), "srcFormat/targetFormat 不同视为新条目");
    assertEquals(0, r.getUpdated());
}
```

- [ ] **Step 4**：运行失败

- [ ] **Step 5**：实现 `ConfigExportService.java`

```java
@Service
@RequiredArgsConstructor
public class ConfigExportService {

    private final TemplateService templateService;
    private final ChannelConfigService channelService;
    private final PortRouteService portRouteService;
    private final InterfaceConfigService interfaceService;
    private final ObjectMapper objectMapper;

    public byte[] exportAllTransform() { ... }
    public byte[] exportSelectedTransform(List<Long> ids) { ... }
    public byte[] exportAllVisual() { ... }
    public byte[] exportSelectedVisual(List<Long> ids) { ... }

    private byte[] zipEntries(String kind, Map<String, Object> objects,
                              Function<Map.Entry<String,Object>, String> keyFn) {
        LinkedHashMap<String, byte[]> files = new LinkedHashMap<>();
        ImportManifest manifest = new ImportManifest();
        manifest.setKind(kind);
        manifest.setExportedAt(Instant.now().toString());
        manifest.setVersion("CHG-020");
        List<ImportManifest.Entry> entries = new ArrayList<>();
        try {
            for (Map.Entry<String, Object> e : objects.entrySet()) {
                stripIdAndTimestamps(e.getValue());
                byte[] json = objectMapper.writerWithDefaultPrettyPrinter()
                    .writeValueAsBytes(e.getValue());
                files.put(e.getKey(), json);
                ImportManifest.Entry m = new ImportManifest.Entry();
                m.setPath(e.getKey());
                m.setPrimaryKey(keyFn.apply(e));
                entries.add(m);
            }
            manifest.setEntries(entries);
            files.put("manifest.json",
                objectMapper.writerWithDefaultPrettyPrinter().writeValueAsBytes(manifest));
            return pack(files);
        } catch (Exception ex) {
            throw new BusinessException(500, "配置导出失败: " + ex.getMessage());
        }
    }

    private void stripIdAndTimestamps(Object obj) {
        // 反射清空 id / createTime / updateTime 字段（若存在）
        for (String f : Arrays.asList("id","createTime","updateTime")) {
            try {
                Field field = obj.getClass().getDeclaredField(f);
                field.setAccessible(true);
                field.set(obj, null);
            } catch (NoSuchFieldException | IllegalAccessException ignore) {}
        }
    }
    // pack(files) 同 InterfaceDocumentService（可抽公共工具 ZipUtil，见 Task 15）
}
```

- [ ] **Step 6**：实现 `ConfigImportService.java`

```java
@Service
@RequiredArgsConstructor
public class ConfigImportService {

    private final TemplateService templateService;
    private final ChannelConfigService channelService;
    private final PortRouteService portRouteService;
    private final InterfaceConfigService interfaceService;
    private final ObjectMapper objectMapper;

    @Transactional(rollbackFor = Exception.class)
    @SysLogRecord(action = "导入转换配置")
    public ImportResult importTransform(byte[] zipBytes, String strategy) {
        Strategy s = Strategy.of(strategy);
        Map<String, byte[]> entries = unzip(zipBytes);
        // 先解析 manifest，若不存在则宽容按目录约定读
        ImportResult result = new ImportResult();
        result.setItems(new ArrayList<>());
        for (Map.Entry<String, byte[]> e : entries.entrySet()) {
            if (e.getKey().startsWith("templates/")) {
                handleTemplate(e.getValue(), s, result);
            } else if (e.getKey().startsWith("channels/")) {
                handleChannel(e.getValue(), s, result);
            } else if (e.getKey().startsWith("port-routes/")) {
                handlePortRoute(e.getValue(), s, result);
            }
        }
        return result;
    }

    @Transactional(rollbackFor = Exception.class)
    @SysLogRecord(action = "导入可视化配置")
    public ImportResult importVisual(byte[] zipBytes, String strategy) { ... }

    private void handleTemplate(byte[] json, Strategy s, ImportResult r) {
        try {
            ConvertTemplate incoming = objectMapper.readValue(json, ConvertTemplate.class);
            ConvertTemplate existing = templateService.findByPrimary(
                incoming.getName(), incoming.getSrcFormat(), incoming.getTargetFormat());
            ImportResult.Item item = new ImportResult.Item();
            item.setName(incoming.getName());
            if (existing == null) {
                if (s == Strategy.ASK) {
                    item.setStatus("NEW");
                } else {
                    templateService.save(incoming);
                    r.setImported(r.getImported() + 1);
                    item.setStatus("NEW");
                }
            } else {
                item.setExistsId(existing.getId());
                switch (s) {
                    case OVERWRITE:
                        incoming.setId(existing.getId());
                        templateService.update(incoming);
                        r.setUpdated(r.getUpdated() + 1);
                        item.setStatus("OVERWRITE");
                        break;
                    case SKIP:
                        r.setSkipped(r.getSkipped() + 1);
                        item.setStatus("SKIP");
                        break;
                    case ASK:
                        item.setStatus("CONFLICT");
                        break;
                }
            }
            r.getItems().add(item);
        } catch (Exception ex) {
            throw new BusinessException(400, "模板条目导入失败: " + ex.getMessage());
        }
    }

    private enum Strategy {
        OVERWRITE, SKIP, ASK;
        static Strategy of(String s) {
            try { return valueOf(s.toUpperCase()); }
            catch (Exception e) { throw new BusinessException(400, "非法策略: " + s); }
        }
    }
}
```

`TemplateService.findByPrimary(name, src, target)` 是新增的辅助方法，走 `LambdaQueryWrapper` 三元组查询。`ChannelConfigService.findByCode(code)`、`PortRouteService.findByChannelCode(code)`、`InterfaceConfigService.findByName(name)` 同理。

- [ ] **Step 7**：Controller

```java
@RestController
@RequestMapping("/api/config")
@RequiredArgsConstructor
public class ConfigImportExportController {

    private final ConfigExportService exportService;
    private final ConfigImportService importService;

    @GetMapping("/export/transform")
    public ResponseEntity<byte[]> exportTransform(@RequestParam(required = false) List<Long> ids,
                                                  @RequestParam(defaultValue = "false") boolean all) {
        byte[] zip = all || ids == null || ids.isEmpty()
            ? exportService.exportAllTransform() : exportService.exportSelectedTransform(ids);
        return zipResponse(zip, "transform-config-" + ts() + ".zip");
    }
    @GetMapping("/export/visual")
    public ResponseEntity<byte[]> exportVisual(@RequestParam(required = false) List<Long> ids,
                                               @RequestParam(defaultValue = "false") boolean all) { ... }

    @PostMapping("/import/transform")
    public Result<ImportResult> importTransform(@RequestParam("file") MultipartFile file,
                                                @RequestParam("strategy") String strategy) throws IOException {
        return Result.success(importService.importTransform(file.getBytes(), strategy));
    }
    @PostMapping("/import/visual")
    public Result<ImportResult> importVisual(@RequestParam("file") MultipartFile file,
                                             @RequestParam("strategy") String strategy) throws IOException {
        return Result.success(importService.importVisual(file.getBytes(), strategy));
    }
}
```

- [ ] **Step 8**：运行两个测试类通过 + 全量回归

- [ ] **Step 9**：提交

```bash
git add backend/src/main/java/com/powergateway/model/dto/ImportManifest.java \
        backend/src/main/java/com/powergateway/model/dto/ImportResult.java \
        backend/src/main/java/com/powergateway/service/ConfigExportService.java \
        backend/src/main/java/com/powergateway/service/ConfigImportService.java \
        backend/src/main/java/com/powergateway/controller/ConfigImportExportController.java \
        backend/src/test/java/com/powergateway/FN11ConfigExportServiceTest.java \
        backend/src/test/java/com/powergateway/FN11ConfigImportServiceTest.java
git commit -m "feat(FN-11): 配置导入导出（zip 打包/解压 + 主键判定 + 三策略 + 事务）"
```

---

## Task 15: 导入导出前端页面 · FN-11 前端

**Files:**
- Create: `frontend/src/api/interfaceImportExport.js`
- Create: `frontend/src/views/interface/InterfaceImportExport.vue`
- Modify: `frontend/src/router/index.js`（append-only `/interface/import-export`）

**Interfaces:**
- Consumes: 后端 5 个端点（Task 14）

- [ ] **Step 1**：`frontend/src/api/interfaceImportExport.js`

```js
import request from './request'

export const exportConfig = (kind, params) =>
  request.get(`/config/export/${kind}`, { params, responseType: 'blob' })

export const importConfig = (kind, file, strategy) => {
  const fd = new FormData()
  fd.append('file', file)
  fd.append('strategy', strategy)
  return request.post(`/config/import/${kind}`, fd, {
    headers: { 'Content-Type': 'multipart/form-data' }
  })
}
```

- [ ] **Step 2**：`InterfaceImportExport.vue`

```vue
<template>
  <div class="ie-page">
    <el-tabs v-model="kind">
      <el-tab-pane label="转换接口" name="transform" />
      <el-tab-pane label="可视化接口" name="visual" />
    </el-tabs>

    <el-row :gutter="16">
      <el-col :span="12">
        <el-card header="导出">
          <el-button type="primary" @click="exportAll">全量导出 zip</el-button>
          <el-button @click="exportSelected" :disabled="!selectedIds.length">
            导出选中 ({{ selectedIds.length }})
          </el-button>
          <el-table :data="candidates" @selection-change="onSelChange" style="margin-top:12px">
            <el-table-column type="selection" width="42" />
            <el-table-column prop="name" label="名称" />
          </el-table>
        </el-card>
      </el-col>

      <el-col :span="12">
        <el-card header="导入">
          <el-radio-group v-model="strategy">
            <el-radio-button value="OVERWRITE">覆盖</el-radio-button>
            <el-radio-button value="SKIP">跳过</el-radio-button>
            <el-radio-button value="ASK">询问</el-radio-button>
          </el-radio-group>
          <el-upload :auto-upload="false" :on-change="onFileChange" :show-file-list="true" style="margin-top:8px">
            <el-button>选择 zip</el-button>
          </el-upload>
          <el-button type="primary" :disabled="!file" @click="doImport" style="margin-top:8px">开始导入</el-button>
          <el-table v-if="result" :data="result.items" border style="margin-top:12px">
            <el-table-column prop="name" label="条目" />
            <el-table-column prop="status" label="状态" width="120" />
            <el-table-column prop="message" label="说明" />
          </el-table>
          <div v-if="result" style="margin-top:8px">
            成功新增 {{ result.imported }} · 覆盖 {{ result.updated }} · 跳过 {{ result.skipped }} · 失败 {{ result.failed }}
          </div>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script setup>
import { ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { exportConfig, importConfig } from '@/api/interfaceImportExport'
import { listTransformDoc, listVisualDoc } from '@/api/interfaceDoc'
import { downloadBlob } from '@/utils/download'

const kind = ref('transform')
const strategy = ref('OVERWRITE')
const file = ref(null)
const result = ref(null)
const candidates = ref([])
const selectedIds = ref([])

async function loadCandidates() {
  candidates.value = kind.value === 'transform'
    ? (await listTransformDoc()) || []
    : (await listVisualDoc()) || []
  selectedIds.value = []
}
watch(kind, loadCandidates, { immediate: true })

function onSelChange(rows) { selectedIds.value = rows.map(r => r.id) }

async function exportAll() {
  const blob = await exportConfig(kind.value, { all: true })
  downloadBlob(blob, `${kind.value}-config-${Date.now()}.zip`)
}
async function exportSelected() {
  const blob = await exportConfig(kind.value, { ids: selectedIds.value.join(',') })
  downloadBlob(blob, `${kind.value}-config-${Date.now()}.zip`)
}

function onFileChange(f) { file.value = f.raw }
async function doImport() {
  result.value = await importConfig(kind.value, file.value, strategy.value)
  ElMessage.success('导入完成')
}
</script>
```

- [ ] **Step 3**：`router/index.js` **append-only** 追加

```js
{
  path: 'interface/import-export',
  name: 'InterfaceImportExport',
  component: () => import('@/views/interface/InterfaceImportExport.vue'),
  meta: { title: '配置导入导出' }
},
```

- [ ] **Step 4**：手工验证 5 个链路：全量导出 zip、选中 2 条导出、SKIP 重复导入、OVERWRITE 修改后重复导入、ASK 返回 preview 表。

- [ ] **Step 5**：提交

```bash
git add frontend/src/api/interfaceImportExport.js \
        frontend/src/views/interface/InterfaceImportExport.vue \
        frontend/src/router/index.js
git commit -m "feat(FN-11): 配置导入导出前端页面（选中/全量导出 + 三策略导入）"
```

---

## Task 16: pg-testkit 端到端冒烟脚本

**Files:**
- Modify: `pg-testkit/src/main/resources/scenarios/`（新增 `ux-e-smoke.json` 或对应场景类）
- Or: `pg-testkit/README.md` 补充手工冒烟步骤

**Interfaces:**
- Consumes: 已启动的 backend :8080 + frontend :5173

- [ ] **Step 1**：定义冒烟清单（按 spec §14 逐项）

1. 发布一个 SELECT 接口，`curl -H "Accept: application/xml" -d '{"params":{}}' http://localhost:8080/api/exec/{id}` → 返回 XML
2. `curl "http://localhost:8080/api/exec/{id}?format=csv"` → 返回 CSV 首行含列头
3. 前端「导出字段」下载 xlsx，双 Sheet 打开正常
4. 前端 MessageDebug 切 XML mode 有语法高亮
5. `GET /api/doc/visual/{id}?format=html` 浏览器可读，含请求/响应字段表
6. 每个列表页「导出报表」下载 xlsx
7. `POST /api/config/export/transform?all=true` 下载 zip，unzip 后含 `manifest.json`
8. 修改 zip 内某模板 `mappingRule` 后 `POST /api/config/import/transform` (OVERWRITE)，DB 中对应模板已更新

- [ ] **Step 2**：把冒烟结果记录在 Task 16 commit message 中；若 pg-testkit 支持脚本化，则新增场景文件。若不支持，在本任务计划的最后追加一段「手工冒烟结果」小节。

- [ ] **Step 3**：提交（若有代码变更）

```bash
git add pg-testkit/
git commit -m "test(UX-E): 端到端冒烟脚本 / 结果记录"
```

---

## Task 17: 变更记录 CHG-020 + 问题清单归档

**Files:**
- Modify: `docs/03-开发/变更记录.md`
- Modify: `docs/03-开发/问题清单.md`

- [ ] **Step 1**：在 `变更记录.md` 追加

```markdown
## CHG-020 · UX-E 可视化接口扩展（FN-06 ~ FN-11）

- **日期**：2026-07-19
- **影响单元**：M2-7（ExecController 扩展 Accept 协商）、M2-2（POI 复用，未回填旧导出）、M1-1（FormatConverter 复用）、M1-5（TemplateService 新增 findByPrimary/listAllSummary）、SYS-1（LogList 增导出按钮，SysLogService 未回填）
- **变更前**：可视化接口仅支持 JSON 响应；字段配置无 Excel 输出；报文调试无语法高亮；无接口文档下载；列表页除操作日志外无导出报表；无配置导入导出能力
- **变更后**：
  - FN-06 `ExecController` 支持 `Accept/?format=` 协商 JSON/XML/CSV/FORM_DATA 四格式响应；`interface_config` 新增 `response_format` / `response_headers`
  - FN-07 新增 `GET /api/interface/{id}/field-schema/export` 输出双 Sheet Excel
  - FN-08 `MessageDebug.vue` 引入 CodeMirror 5.x 语法高亮
  - FN-09 新增 `InterfaceDocumentService` 输出 Markdown/HTML，含全量 zip 导出
  - FN-10 新增 `ExcelExportUtil` 通用工具，8 个列表页新增「导出报表」按钮
  - FN-11 新增 `ConfigExportService` / `ConfigImportService`，zip 打包 + 主键判定 + OVERWRITE/SKIP/ASK 三策略
- **原因**：产品需求「面向业务的字段解释文档、可迁移配置、对外多格式响应」交付
- **迁移操作**：旧库需执行 `backend/src/main/resources/db/migration-response-format.sql`（幂等）
- **依赖变更**：前端新增 `codemirror@5.65.16` + `vue-codemirror@6.1.1`；后端**无**新增依赖
```

- [ ] **Step 2**：`问题清单.md` 把 FN-06 ～ FN-11 六条从「待解决」搬到「已解决」，每条附上「CHG-020」与完成日期。

- [ ] **Step 3**：提交

```bash
git add docs/03-开发/变更记录.md docs/03-开发/问题清单.md
git commit -m "docs(UX-E): CHG-020 归档 + FN-06~FN-11 问题清单归档"
```

---

## 自审清单（执行前 review 用）

- [ ] 6 子项覆盖率：FN-06 (Task 1~4) / FN-07 (Task 5~7) / FN-08 (Task 11) / FN-09 (Task 12~13) / FN-10 (Task 5, 8~10) / FN-11 (Task 14~15) 全部覆盖
- [ ] Accept 协商测试覆盖 JSON / XML / CSV / FORM_DATA 四种：见 Task 3 Step 1 用例 1/2/3/4
- [ ] `FormatType.FORM_DATA` 用词与既有枚举一致（不是 `FORM`）
- [ ] `interface_config` 迁移脚本幂等（`information_schema.COLUMNS` 判断）
- [ ] `init.sql` 同步更新（Task 1 Step 4）
- [ ] `ExecController` 默认 Accept=JSON 兼容 M2-7 语义（Task 3 Step 3 分支：`target == FormatType.JSON → return Result.success(...)`）
- [ ] `SysLogService.exportExcel` / `TableMetaService.exportExcel` 不回填（Task 5 / Task 9 Step 3 明确不动）
- [ ] zip 打包只用 JDK `ZipOutputStream`（Task 12 Step 4 / Task 14 Step 5 均自实现）
- [ ] 不引入 PDF/Word/monaco/zip4j（Task 11 只用 codemirror；Task 12/14 zip 自建）
- [ ] 每 Task Steps 有代码块（除 Task 16 冒烟脚本外）
- [ ] 每 Task 独立可 review，前 Task 完成即可开始下 Task
- [ ] 全部测试类均带 `@ActiveProfiles("test")`
- [ ] CHG-020 与问题清单归档在 Task 17
- [x] 无 TBD / TODO / 待定 / "略" 未展开的关键决策（Task 12 中 `docResponse / zipResponse / ts` 三个辅助方法已在计划里给出完整实现）
