# UX-E · 可视化接口扩展 详细设计

- **日期**：2026-07-19
- **状态**：待评审
- **来源**：`docs/03-开发/问题清单.md` 「2026-07-19 批次 → E 组」，原始条目见仓库根 `111.txt` #15a / #15b / #18b / #19 / #20 / #21
- **需求参考**：
  - `docs/01-需求/可视化接口程序产品需求说明书.md` § 3.2.9 拓展性（多报文格式）、§ 3.2.7 缓存查询管理（下载报表）、二级菜单 5.1 接口文档
  - `docs/01-需求/需求拆分与最小实现方案.md` 相关章节（FN-06 复用 M1-6；FN-07/10/11 复用 M2-2 Excel 导出模板；FN-09 复用 SpringDoc + `OpenApiDynamicCustomizer`）
- **总览设计**：`docs/02-设计/详细设计/2026-07-19-visual-refresh-and-fixes-overview.md`
- **前置依赖**：M1-1（FormatConverter）、M1-6（ConvertService）、M2-2（Excel/POI 5.2.3 已引入）、M2-3~M2-7（可视化接口 CRUD + 发布 + 执行入口）、M2-9 审计、SYS-1 操作日志
- **变更编号（占位）**：CHG-020（本组交付时统一登记，覆盖 6 个子项）
- **并行边界**：与 UX-A、UX-B、UX-C、UX-D、UX-F 无 Java 文件冲突；仅 `router/index.js` 采用 append-only 策略（详见 overview）

---

## 1. 目标

在不引入第二个 Excel 库、不新增数据库的前提下，为**已发布的可视化接口 / 已配置的转换模板**补齐 6 项"最后一公里"能力：

1. **对外输出格式自由化**（FN-06）
2. **配置产物可交付**（FN-07 字段清单 Excel）
3. **调试能力齐备**（FN-08 报文调试 XML）
4. **接口文档下载即用**（FN-09）
5. **管理页可导出报表**（FN-10）
6. **配置可迁移**（FN-11 导入导出）

统一原则：**复用已交付组件，不引入第二个 Excel/JSON/XML 库；所有导出走 POI 5.2.3 + Jackson；所有导入解析既有 DTO**。

---

## 2. 需求来源与范围

### 2.1 6 个子项

| 编号 | 111.txt | 简述 | 关键决策 |
|------|---------|------|---------|
| FN-06 | #15a | 已发布接口执行结果支持 JSON/XML/CSV/FormData 四种响应；请求也允许非 JSON 提交 | 复用 `FormatConverter` + Accept 头协商；`interface_config` 加 `response_format` 兜底 |
| FN-07 | #15b | 接口配置完成后，一键导出请求/响应字段清单为 Excel（英文名/中文/类型/长度/必填/备注） | 新增 `GET /api/interface/{id}/field-schema/export`，用 POI 生成单文件双 Sheet |
| FN-08 | #18b | 报文调试页 `MessageDebug.vue` 支持 XML 输入（当前实为纯 textarea，视觉上无 XML 语法提示） | 输入区改 `<CodeEditor>`，语言 = json/xml/csv 三选；无需额外后端 |
| FN-09 | #19 | 接口文档功能落地：转换接口 / 可视化接口两类分别生成 | 新增 `views/interface/InterfaceDocument.vue`；后端 `DocumentService` 生成 Markdown+HTML，浏览器下载；不引入 PDF 库 |
| FN-10 | #20 | 常用查询菜单查询结果统一支持下载报表（Excel） | 抽 `ExcelExportUtil` 后端工具类；前端各列表页头部新增「导出当前查询结果」按钮 |
| FN-11 | #21 | 转换模板 / 可视化接口 配置的导入导出，导入按"主键"判定新增或更新 | 新增 `views/interface/InterfaceImportExport.vue`；后端 `ConfigExportService` / `ConfigImportService`；主键：转换模板按 `name`，可视化接口按 `name` |

### 2.2 不含 / 明确排除

- **不引入 PDF/Word 生成库**（iText、Apache PDFBox、docx4j 等）。FN-09 输出 Markdown + HTML，浏览器打印即得 PDF，需求「下载即用」达标。
- **不重构 `ExecController`**，仅扩展 Accept 协商与响应包装。原 `Result<T>` 返回体在 `Accept: application/json`（默认）时**保持不变**，保证兼容。
- **不做双向多格式请求解析的自动映射**——请求 Body 仍以 JSON 为默认，XML/CSV/Form 通过转换到 `Map<String,Object>` 后走原有 `params` 通道。
- **不引入 monaco-editor**（体积过大，Element Plus 生态首选 CodeMirror 5.x），且 UX-C 已用 `ConditionBuilder`，本单元保持一致轻量。
- 不做「导入前 diff 预览」的复杂 UI，冲突以「覆盖 / 跳过 / 询问」三选，「询问」通过前端表格逐条勾选实现。

---

## 3. FN-06 多报文格式支持

### 3.1 `ExecController` 改造设计

改造点定位：`backend/src/main/java/com/powergateway/controller/ExecController.java`。

#### 3.1.1 Accept 头协商

新增枚举 `com.powergateway.utils.ResponseFormat`（值：`JSON` / `XML` / `CSV` / `FORM`，复用 `FormatType`，避免二次维护）。

协商顺序（前面覆盖后面）：

1. Query 参数 `?format=xml`（调试/浏览器场景，最直观）
2. HTTP 头 `Accept`（`application/xml`→XML、`text/csv`→CSV、`application/x-www-form-urlencoded`→FORM、`application/json`→JSON）
3. `interface_config.response_format`（用户在配置页选的默认值）
4. 兜底 JSON

新增工具类 `com.powergateway.utils.AcceptNegotiator`：

```java
public static FormatType negotiate(HttpServletRequest req,
                                   String queryParamFormat,
                                   String configDefault) {
    if (queryParamFormat != null) return FormatType.parse(queryParamFormat);
    String accept = req.getHeader(HttpHeaders.ACCEPT);
    if (accept != null) {
        if (accept.contains("application/xml") || accept.contains("text/xml")) return FormatType.XML;
        if (accept.contains("text/csv")) return FormatType.CSV;
        if (accept.contains("application/x-www-form-urlencoded")) return FormatType.FORM;
        if (accept.contains("application/json")) return FormatType.JSON;
    }
    if (configDefault != null && !configDefault.isEmpty()) return FormatType.parse(configDefault);
    return FormatType.JSON;
}
```

#### 3.1.2 响应体序列化

改造后的方法签名保持不变（`Result<?>`），但在 Controller 层拦截 `Accept != JSON` 的情况，改由 `ResponseEntity<String>` 返回：

```java
@PostMapping(value = "/{interfaceId}",
             produces = {MediaType.APPLICATION_JSON_VALUE,
                         MediaType.APPLICATION_XML_VALUE,
                         "text/csv",
                         MediaType.APPLICATION_FORM_URLENCODED_VALUE})
public ResponseEntity<?> execute(HttpServletRequest req,
                                 @PathVariable Long interfaceId,
                                 @RequestParam(value = "format", required = false) String format,
                                 @RequestBody(required = false) ExecRequest body) { ... }
```

处理流程：

1. 走原有 `service.executeXxx()`，拿到业务结果 `Object` （行列表 / 影响行数）
2. `FormatType target = AcceptNegotiator.negotiate(req, format, config.getResponseFormat())`
3. 若 `target == JSON`：**保持 `Result.success(data)` 返回原语义**（兼容既有调用方）
4. 否则：
   - `SELECT` 结果 `List<Map<String,Object>>` → 用 `FormatConverter.serializeMap()` 序列化 → 用 `wrapAsRoot("data", ...)` 包一层，避免 XML 无根标签
   - `INSERT/UPDATE/DELETE` 结果 `Integer` → 包为 `{"code":200,"data":<n>}` 再序列化
   - 响应 `Content-Type` 匹配 `target`

XML 特殊处理：`FormatConverter.serialize` 在 XML 情况下会自动包裹 `<root>` 元素（已实现），对多行 List 需要手工包裹 `<rows><row>...</row></rows>`。计划在 `FormatConverter` 增补 `serializeRows(List<Map<String,Object>>, FormatType)` 便捷方法，或在 `ExecController` 内先转 `{"rows": <list>}` 单层 Map 后调用 `serializeMap`。

#### 3.1.3 请求体的非 JSON 支持（可选，默认关闭）

M2-7 现状 `@RequestBody ExecRequest`：Spring 用 Jackson 自动解 JSON。要支持 XML/CSV 请求需注册 `HttpMessageConverter`。**本次不做**，仅在 `ExecController` 前置一个"若 `Content-Type` 非 JSON 则读原始 Body，用 `FormatConverter.parseToMap` 转成 `Map` 装进 `ExecRequest.params`"的旁路：

```java
if (contentType != null && !contentType.contains("json")) {
    String raw = IOUtils.toString(req.getInputStream(), StandardCharsets.UTF_8);
    FormatType from = detectFromContentType(contentType);
    Map<String, Object> parsed = formatConverter.parseToMap(raw, from);
    body = new ExecRequest();
    body.setParams(parsed);
}
```

`@RequestBody(required = false)` 保证 Spring 不因 mismatch 报错。

### 3.2 与 M1-6 ConvertService 的关系

- **不引入依赖**：`ExecController` 不注入 `ConvertService`（那是渠道模板转换的入口），而是直接注入 `FormatConverter`（M1-1 工具，无副作用、无缓存）。
- **序列化路径**：只用 `FormatConverter.parseToMap` 与 `serializeMap`；渠道匹配、mapping/process 规则全部不参与。
- **不与 M1-7 `PortRouteService.dispatch()` 冲突**：那是"上游转发到下游"两级 header 合并，本单元只是"下游到调用方"的响应包装。

### 3.3 `interface_config.response_format` 字段

新增列（幂等 migration 脚本）：

```sql
-- 位置：backend/src/main/resources/db/migration-response-format.sql（新建）
-- 幂等：通过存储过程判定字段是否存在，参考 migration-sys-config.sql 模式
ALTER TABLE interface_config
  ADD COLUMN response_format VARCHAR(16) DEFAULT 'JSON'
  COMMENT 'FN-06 用户默认响应格式：JSON/XML/CSV/FORM';

ALTER TABLE interface_config
  ADD COLUMN response_headers TEXT DEFAULT NULL
  COMMENT 'FN-06 自定义响应头 JSON，格式 {"X-Foo":"bar"}';
```

同时更新 `init.sql` 中 `interface_config` 建表 DDL（新库直接生效），并按 CLAUDE.md 规约在 `docs/03-开发/变更记录.md` 追加 CHG-020。

`InterfaceConfig` 实体新增两个字段：

```java
private String responseFormat;   // 默认 "JSON"
private String responseHeaders;  // JSON 字符串，Jackson 反序列化到 Map<String,String>
```

`InterfaceSaveRequest` 同步新增二字段透传。

### 3.4 前端 `InterfaceConfig` 表单增强

各接口配置页（`QueryConfig.vue` / `InsertConfig.vue` / `UpdateConfig.vue` / `DeleteConfig.vue`）在「基础信息」区新增两个字段：

```vue
<el-form-item label="默认响应格式">
  <el-select v-model="form.responseFormat" style="width:200px">
    <el-option label="JSON" value="JSON" />
    <el-option label="XML"  value="XML" />
    <el-option label="CSV"  value="CSV" />
    <el-option label="FormData" value="FORM" />
  </el-select>
  <el-tooltip content="调用方可通过 Accept 头或 ?format= 覆盖此默认值">
    <el-icon><QuestionFilled /></el-icon>
  </el-tooltip>
</el-form-item>
<el-form-item label="自定义响应头">
  <ResponseHeadersEditor v-model="form.responseHeaders" />
</el-form-item>
```

新增子组件 `frontend/src/components/interface/ResponseHeadersEditor.vue`：一个 key-value 表格，`v-model` 双向绑定为 JSON 字符串。

`InterfaceList.vue` 新增一列「响应格式」显示 `row.responseFormat` 标签。

---

## 4. FN-07 请求/响应字段清单导出 Excel

### 4.1 新增端点

```
GET /api/interface/{id}/field-schema/export
Response: application/vnd.openxmlformats-officedocument.spreadsheetml.sheet
Filename: {interfaceName}_字段清单_{yyyyMMddHHmm}.xlsx
```

后端新增：

- `backend/src/main/java/com/powergateway/controller/InterfaceFieldSchemaController.java`
- `backend/src/main/java/com/powergateway/service/InterfaceFieldSchemaService.java`

Controller 极薄，只处理响应头：

```java
@GetMapping("/{id}/field-schema/export")
public ResponseEntity<byte[]> export(@PathVariable Long id) {
    InterfaceConfig config = service.getById(id);
    byte[] data = schemaService.exportExcel(config);
    HttpHeaders h = new HttpHeaders();
    h.setContentType(MediaType.parseMediaType(
        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"));
    h.setContentDispositionFormData("attachment",
        URLEncoder.encode(config.getName() + "_字段清单.xlsx", "UTF-8"));
    return ResponseEntity.ok().headers(h).body(data);
}
```

### 4.2 Excel 结构

**两个 Sheet**：

| Sheet 名 | 场景 |
|---------|------|
| 请求字段 | SELECT 的条件字段；INSERT/UPDATE 的写入字段；DELETE 的条件字段 |
| 响应字段 | SELECT 的返回字段；写入类接口固定输出「影响行数」一列 |

**统一列定义**（Header 灰色底、加粗、居中，复用 `TableMetaService.createHeaderStyle`）：

| 列 | 说明 |
|----|------|
| 序号 | 从 1 开始 |
| 英文字段名 | 对应 `column` / `paramKey` / `alias` |
| 中文含义 | 取自 `ColumnMeta.remarks`（表结构注释，M2-2 已缓存）；无注释时留空 |
| 数据类型 | `ColumnMeta.type`（如 `VARCHAR`、`INT`、`DATETIME`） |
| 长度 | 从表结构元数据 `TableMeta` 提取（新增 `size` 字段，若已缺则默认空） |
| 必填 | `!nullable && !primaryAuto`，Y/N |
| 备注 | 拼接来源：`REQUEST` / `CONST=xxx` / `CALC=表达式` / 主键 / 唯一索引 |

`InterfaceFieldSchemaService` 内部按 `config.type` 分派：

```java
public byte[] exportExcel(InterfaceConfig config) {
    List<FieldRow> requestRows;
    List<FieldRow> responseRows;
    switch (config.getType()) {
        case "SELECT": {
            QueryConfigJson q = objectMapper.readValue(config.getConfigJson(), QueryConfigJson.class);
            requestRows  = buildRequestRowsFromQuery(q, config.getDbConnectionId());
            responseRows = buildResponseRowsFromQuery(q, config.getDbConnectionId());
            break;
        }
        case "INSERT": {
            InsertConfigJson i = objectMapper.readValue(config.getConfigJson(), InsertConfigJson.class);
            requestRows  = buildRequestRowsFromInsert(i, config.getDbConnectionId());
            responseRows = affectedRowsSchema();
            break;
        }
        // UPDATE / DELETE 同上
    }
    return writeExcel(requestRows, responseRows, config.getName());
}
```

字段类型/中文注释统一走 `tableMetaService.getTables(dbId)` 缓存，避免每次导出击穿目标库。

### 4.3 前端 `InterfaceList.vue` 新增「导出字段」按钮

在"操作"列现有按钮组末尾追加：

```vue
<el-button size="small" @click="handleExportFields(row)">导出字段</el-button>
```

调用：

```js
async function handleExportFields(row) {
  const blob = await request.get(`/interface/${row.id}/field-schema/export`, {
    responseType: 'blob'
  })
  downloadBlob(blob, `${row.name}_字段清单.xlsx`)
}
```

新增前端工具函数 `frontend/src/utils/download.js`：`downloadBlob(blob, filename)`（URL.createObjectURL + `<a>` 触发下载），后续 FN-09/FN-10/FN-11 全部复用。

---

## 5. FN-08 报文调试 XML 支持

### 5.1 `MessageDebug.vue` 输入区改造

**现状**：`el-input type="textarea"`，纯文本，无语法着色。
**方案**：引入 CodeMirror 5.x（`vue-codemirror6` 或直接嵌入 `codemirror` npm 包）。若 `frontend/package.json` 已有 `codemirror`，直接复用；否则新增依赖 `codemirror@5.65.x` + `vue-codemirror@6.x`（体积 < 200KB gzip）。

新增子组件 `frontend/src/components/tools/CodeEditor.vue`：

```vue
<template>
  <div ref="host" class="code-editor" />
</template>

<script setup>
import { ref, watch, onMounted, onBeforeUnmount } from 'vue'
import CodeMirror from 'codemirror'
import 'codemirror/mode/javascript/javascript'
import 'codemirror/mode/xml/xml'
import 'codemirror/mode/htmlmixed/htmlmixed'
import 'codemirror/lib/codemirror.css'
import 'codemirror/theme/idea.css'

const props = defineProps({
  modelValue: { type: String, default: '' },
  language:   { type: String, default: 'json' } // json / xml / csv
})
const emit = defineEmits(['update:modelValue'])
// ... 挂载 / 销毁 / language 切换 / value 双向绑定
</script>
```

CSV 无原生模式，退化为纯文本+等宽字体。

### 5.2 输入格式切换

`MessageDebug.vue` 顶部工具栏新增 `<el-radio-group>`：

```vue
<el-radio-group v-model="inputLang">
  <el-radio-button value="json">JSON</el-radio-button>
  <el-radio-button value="xml">XML</el-radio-button>
  <el-radio-button value="csv">CSV</el-radio-button>
</el-radio-group>
```

切换时不清空内容（避免误删），仅重设 CodeMirror mode。

### 5.3 后端 debug 端点 Accept application/xml

当前 `MessageDebug.vue` 的"接口调用调试"模式走的是 `POST /api/exec/{id}`（详见 `frontend/src/api/interface.js.execInterface`）。FN-06 完成后，此处天然获得 XML/CSV 请求响应能力，本子项无需再改后端。

「格式转换调试」模式走 `POST /api/convert`（`convertMessage`），M1-6 已支持四种 srcFormat，此处也无需改后端。

**新增点**：`convertMessage` 请求增加 `srcFormat` 显式字段（复用现有 `ConvertRequest.srcFormat`），前端根据 `inputLang` 自动填入。

---

## 6. FN-09 接口文档功能

### 6.1 两类接口的文档差异

| 维度 | 转换接口（M1 系列） | 可视化接口（M2 系列） |
|------|-------------------|---------------------|
| 触发面 | `POST /api/convert` / `POST /api/dispatch` | `POST /api/exec/{id}` |
| 文档核心 | 「渠道识别 → 模板匹配 → 字段映射 → 字段加工 → 端口转发」全链路 | 「请求参数 → SQL 构造 → 响应字段」端到端 |
| 输出章节 | 渠道列表、每渠道下的模板 + 字段映射表 + 加工规则表 + 端口路由 | 每接口的：基本信息、请求字段表、响应字段表、SQL 示例、响应格式（联动 FN-06）、样例（JSON + XML） |

### 6.2 后端设计

新增：

- `backend/src/main/java/com/powergateway/service/InterfaceDocumentService.java`
- `backend/src/main/java/com/powergateway/controller/InterfaceDocumentController.java`

端点：

```
GET  /api/doc/transform/list             # 转换接口清单
GET  /api/doc/transform/{templateId}     # 单个转换模板 Markdown/HTML
GET  /api/doc/transform/export           # 全部转换接口打包（zip 含 md + html）
GET  /api/doc/visual/list                # 可视化接口清单
GET  /api/doc/visual/{interfaceId}       # 单个可视化接口 Markdown/HTML
GET  /api/doc/visual/export              # 全部可视化接口打包
GET  /api/doc/visual/{id}?format=html    # 单份下载
```

统一响应：`Content-Type: text/markdown; charset=utf-8` 或 `text/html; charset=utf-8`，`Content-Disposition: attachment`。

生成实现：`InterfaceDocumentService` 内部先构造 `DocumentModel`（POJO），再用 **Java StringBuilder** 拼 Markdown / HTML 模板（不引入 Freemarker / Velocity）。样板控制在 200 行内。

HTML 模板：内嵌简单 CSS（表格边框、代码块灰底），保证浏览器打开即打印为 PDF（用户需求「下载即用」达标）。

打包 zip：Java 内置 `ZipOutputStream`，不引入额外依赖。

### 6.3 生成格式选择：Markdown + HTML

**决策**：只出 Markdown + HTML，**不出 PDF/Word**。理由：

1. 不引入 iText/POI-word 二次依赖，避免体积膨胀
2. 用户在浏览器打开 HTML 后 Ctrl+P 打印为 PDF 已满足「下载即用」
3. Markdown 天然适合技术团队 review + Git 版本管理

### 6.4 前端「接口文档」菜单页

- **路由改造**：`frontend/src/router/index.js` 把 `tools/swagger` 的 `PlaceholderView` 替换为新组件 `views/interface/InterfaceDocument.vue`。菜单文案改为「接口文档」，位置由 UX-B 统一决定。
- **页面结构**：顶部两个 Tab「转换接口」/「可视化接口」，各自一个搜索框 + 分页表格。每行两个操作：`预览`（右侧抽屉展示 HTML）/`下载`（下拉：Markdown / HTML）。页面底部一个「批量导出全部」按钮（触发 `/api/doc/xxx/export` 打包 zip）。

- **对 Swagger UI 的关系**：Swagger UI（`/swagger-ui.html`）仍然由 M2-7 `OpenApiDynamicCustomizer` 维护，作为「原生技术文档」。本 FN-09 提供的是「面向业务的字段解释文档」，两者互补，菜单上单独入口保留 `swagger-ui.html` 的外链（如需要）。

---

## 7. FN-10 常用查询报表下载

### 7.1 涉及的查询菜单

| 菜单 | 前端文件 | 后端 list 端点 | 导出端点（新增） |
|------|---------|--------------|----------------|
| 接口列表 | `views/interface/InterfaceList.vue` | `/api/interface/list` | `/api/interface/list/export` |
| 转换模板列表 | `views/convert/Template.vue` | `/api/template/list` | `/api/template/list/export` |
| 渠道模板 | `views/convert/ChannelConfig.vue` | `/api/channel/list` | `/api/channel/list/export` |
| 端口路由 | `views/convert/PortRoute.vue` | `/api/port-route/list` | `/api/port-route/list/export` |
| 操作日志 | `views/system/LogList.vue` | `/api/sys-log/list` | ✅ 已有 `/api/log/export` |
| SQL 审计日志 | 同上 Tab | `/api/sql-audit-log/list` | `/api/sql-audit-log/export` |
| 性能统计 | `views/system/Stats.vue` | `/api/perf-stat/list` | `/api/perf-stat/list/export` |
| 缓存管理 | `views/interface/CacheList.vue` | `/api/interface/cache/list` | `/api/interface/cache/list/export` |
| 数据源列表 | `views/db/Datasource.vue` | `/api/db/list` | `/api/db/list/export` |

### 7.2 统一 Excel 导出组件

新增 `backend/src/main/java/com/powergateway/utils/ExcelExportUtil.java`：

```java
public class ExcelExportUtil {

    /** 通用列描述 */
    public static class Column<T> {
        public String header;        // 表头中文
        public Function<T, Object> valueGetter;
        public Column(String h, Function<T,Object> g) { this.header=h; this.valueGetter=g; }
    }

    /**
     * 通用导出：一个 sheet，任意实体列表 + 列定义。
     */
    public static <T> byte[] export(String sheetName, List<Column<T>> columns, List<T> rows) {
        try (XSSFWorkbook wb = new XSSFWorkbook()) {
            XSSFSheet sheet = wb.createSheet(safeSheetName(sheetName));
            // header
            CellStyle headerStyle = createHeaderStyle(wb);
            XSSFRow header = sheet.createRow(0);
            for (int i = 0; i < columns.size(); i++) {
                XSSFCell cell = header.createCell(i);
                cell.setCellValue(columns.get(i).header);
                cell.setCellStyle(headerStyle);
            }
            // body
            for (int r = 0; r < rows.size(); r++) {
                XSSFRow row = sheet.createRow(r + 1);
                T item = rows.get(r);
                for (int c = 0; c < columns.size(); c++) {
                    Object val = columns.get(c).valueGetter.apply(item);
                    writeCell(row.createCell(c), val);
                }
            }
            // 自适应宽度
            for (int i = 0; i < columns.size(); i++) sheet.autoSizeColumn(i);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            wb.write(out);
            return out.toByteArray();
        } catch (Exception e) {
            throw new BusinessException(500, "Excel 导出失败: " + e.getMessage());
        }
    }
    // ...
}
```

各 Service 只需传入列定义 + 查询结果，即可获得 byte[]：

```java
// InterfaceConfigService 新增
public byte[] exportList(String name) {
    List<InterfaceConfig> rows = list(name, 1, 10000);
    return ExcelExportUtil.export("接口列表", Arrays.asList(
        new Column<>("接口名称", InterfaceConfig::getName),
        new Column<>("类型",     InterfaceConfig::getType),
        new Column<>("状态",     r -> statusLabel(r.getStatus())),
        new Column<>("访问路径", InterfaceConfig::getPath),
        new Column<>("响应格式", InterfaceConfig::getResponseFormat),
        new Column<>("创建时间", r -> formatDt(r.getCreateTime()))
    ), rows);
}
```

**已有 `SysLogService.exportExcel` / `TableMetaService.exportExcel` 保持不动**（老代码不硬性回填，避免测试回归），仅新增的导出复用 `ExcelExportUtil`。

### 7.3 前端每个列表页头部新增「导出」按钮

统一在搜索栏末尾添加：

```vue
<el-button type="default" @click="handleExport">
  <el-icon><Download /></el-icon>导出报表
</el-button>
```

调用统一 `downloadBlob` 工具（FN-07 已建）。前端不新增 Vuex/Pinia 状态。

**权限**：`@SaCheckPermission` 与列表接口保持一致（导出即"批量读"，权限继承 list 端点即可，不新增权限项）。

---

## 8. FN-11 配置导入导出

### 8.1 转换接口的导出（转换模板 + 关联 channel_config + port_route）

新增：

- `backend/src/main/java/com/powergateway/service/ConfigExportService.java`
- `backend/src/main/java/com/powergateway/controller/ConfigImportExportController.java`

**输出格式**：单个 zip，内部结构：

```
transform-config-YYYYMMDDHHmm.zip
├── manifest.json                 # 版本、导出时间、条目索引
├── templates/
│   ├── {name}.json               # 每模板一文件
│   └── ...
├── channels/
│   ├── {channelCode}.json
│   └── ...
└── port-routes/
    ├── {channelCode}.json
    └── ...
```

单文件 JSON 结构 = 直接 Jackson 序列化 `ConvertTemplate` / `ChannelConfig` / `PortRoute` 实体，**去掉自增 id 和时间戳**（防止导入侧主键冲突）。

端点：

```
GET  /api/config/export/transform?ids=1,2,3        # 选中导出
GET  /api/config/export/transform/all              # 全量
POST /api/config/import/transform                  # multipart/form-data，参数 file + strategy
```

`strategy` 枚举：`OVERWRITE`（存在则更新）/`SKIP`（存在则跳过）/`ASK`（不落库，返回条目预览让前端逐条确认）。

### 8.2 可视化接口的导出

结构类似：

```
visual-config-YYYYMMDDHHmm.zip
├── manifest.json
└── interfaces/
    ├── {interfaceName}.json      # 完整 InterfaceConfig + configJson 内联
    └── ...
```

**不导出 `db_connection`**（含加密密码，跨环境不适用），改为把 `dbConnectionName` 写入 manifest，导入侧按名字查找目标库；找不到时前端弹窗让用户手工选。

### 8.3 主键判定策略

| 类型 | 主键 | 冲突判定 |
|------|------|---------|
| 转换模板 | `name` + `srcFormat` + `targetFormat` | 三元组匹配则视为同一模板 |
| 渠道配置 | `channelCode` | 唯一索引已存在 |
| 端口路由 | `channelCode`（一渠道一路由） | 匹配 channelCode |
| 可视化接口 | `name`（同时校验 `type` 一致，防止 SELECT 覆盖 INSERT） | 前端有一个「按功能号匹配」开关（用户 111.txt #21 原话），若接口配置 JSON 里存在 `functionId` 字段，优先按 functionId 匹配；否则按 name |

「功能号」在当前 `InterfaceConfig` 表结构中**没有专门字段**。方案：从 `config_json` 反序列化后读顶层扩展字段 `functionId`。若 UX-B 或 UX-D 单元后续为转换接口加入 `functionId` 主属性，本导入服务向下兼容读取即可（不阻塞本组交付）。

### 8.4 冲突时的合并策略

- **OVERWRITE**：调用 `save(existingId, incoming)`，全字段覆盖；关联审计日志 `@SysLogRecord(action="导入覆盖")`
- **SKIP**：日志记 skip，返回 `{"imported": n, "skipped": m, "details": [...]}`
- **ASK**：不落库；返回条目列表（每条附 `existsId` / `diff` 概览），前端逐条勾选后回调 `/api/config/import/transform/confirm` 提交最终结果

失败原子性：整个导入用 `@Transactional`（配置库单库事务），任一条目校验失败则整体回滚，避免半成品配置污染。

### 8.5 前端「导入/导出」菜单页

新增 `frontend/src/views/interface/InterfaceImportExport.vue`：

- 顶部两个 Tab：「转换接口」/「可视化接口」
- 每 Tab 内左右两块：
  - **导出区**：搜索 + 复选框列表 + 「导出选中」/ 「全量导出」按钮
  - **导入区**：`<el-upload>` 上传 zip；策略下拉；上传后展示解析结果表格（条目名 / 状态：新增/覆盖/跳过/冲突）；「确认导入」按钮
- ASK 模式下：每行前置 checkbox + 单选按钮（新建 / 覆盖 / 跳过）

路由：新增 `/interface/import-export`（append-only）。菜单位置由 UX-B 决定，本单元只保证组件与 API 就绪。

---

## 9. 前端文件变更清单

### 9.1 新增

| 文件 | 用途 | 对应子项 |
|------|------|---------|
| `frontend/src/utils/download.js` | 通用 blob 下载封装 | FN-07/09/10/11 共用 |
| `frontend/src/components/interface/ResponseHeadersEditor.vue` | 响应头 KV 编辑器 | FN-06 |
| `frontend/src/components/tools/CodeEditor.vue` | CodeMirror 5.x 封装（json/xml/csv） | FN-08 |
| `frontend/src/views/interface/InterfaceDocument.vue` | 接口文档预览/下载页 | FN-09 |
| `frontend/src/views/interface/InterfaceImportExport.vue` | 配置导入导出页 | FN-11 |
| `frontend/src/api/interfaceDoc.js` | 接口文档 API 封装 | FN-09 |
| `frontend/src/api/interfaceImportExport.js` | 导入导出 API 封装 | FN-11 |
| `frontend/src/api/interfaceFieldSchema.js` | 字段清单导出 API | FN-07 |

### 9.2 修改

| 文件 | 变更点 | 对应子项 |
|------|--------|---------|
| `frontend/src/views/interface/InterfaceList.vue` | 增「响应格式」列、「导出字段」「导出报表」按钮 | FN-06/07/10 |
| `frontend/src/views/interface/QueryConfig.vue` 等 4 类接口配置页 | 新增「默认响应格式」+「响应头」表单项 | FN-06 |
| `frontend/src/views/tools/MessageDebug.vue` | 输入区换 `CodeEditor`；新增语言切换；execute 传 `srcFormat` | FN-08 |
| `frontend/src/views/convert/Template.vue` 等 6 个列表页 | 头部加「导出报表」按钮 | FN-10 |
| `frontend/src/router/index.js` | 追加 `tools/swagger`→`InterfaceDocument.vue`；新增 `/interface/import-export` route（append-only） | FN-09/11 |
| `frontend/src/api/interface.js` | 补 `exportList`、`exportFieldSchema` 方法 | FN-07/10 |

### 9.3 依赖新增（前端）

若 `frontend/package.json` 尚未含 CodeMirror：

```
"codemirror": "5.65.16",
"vue-codemirror": "^6.1.1"
```

（约 200KB gzip，用于 FN-08）。

---

## 10. 后端文件变更清单

### 10.1 新增

| 文件 | 用途 | 对应子项 |
|------|------|---------|
| `utils/AcceptNegotiator.java` | Accept 头协商 | FN-06 |
| `utils/ExcelExportUtil.java` | 通用 Excel 导出工具 | FN-07/10 |
| `service/InterfaceFieldSchemaService.java` | 字段清单构造 | FN-07 |
| `controller/InterfaceFieldSchemaController.java` | 字段清单端点 | FN-07 |
| `service/InterfaceDocumentService.java` | 文档生成（Markdown/HTML） | FN-09 |
| `controller/InterfaceDocumentController.java` | 文档端点 | FN-09 |
| `service/ConfigExportService.java` | 配置导出 zip 打包 | FN-11 |
| `service/ConfigImportService.java` | 配置导入解析 + 冲突处理 | FN-11 |
| `controller/ConfigImportExportController.java` | 导入导出端点 | FN-11 |
| `model/dto/FieldSchemaRow.java` | Excel 行 DTO | FN-07 |
| `model/dto/DocumentModel.java` | 文档中间模型 | FN-09 |
| `model/dto/ImportManifest.java` | zip manifest | FN-11 |
| `model/dto/ImportResult.java` | 导入结果 | FN-11 |

### 10.2 修改

| 文件 | 变更点 | 对应子项 |
|------|--------|---------|
| `controller/ExecController.java` | Accept 协商 + 响应序列化分支 | FN-06 |
| `service/InterfaceConfigService.java` | 新增 `exportList`；`save` 处理 `responseFormat`/`responseHeaders` | FN-06/10 |
| `service/TemplateService.java` / `ChannelConfigService.java` / `PortRouteService.java` / `PerfStatService.java` / `QueryCacheManager` / `DbConnectionService` | 各自新增 `exportXxx` 方法，复用 `ExcelExportUtil` | FN-10 |
| `controller/InterfaceConfigController.java` 等 | 新增 `GET .../list/export` 端点 | FN-10 |
| `model/InterfaceConfig.java` | 新增 `responseFormat`、`responseHeaders` 字段 | FN-06 |
| `model/dto/InterfaceSaveRequest.java` | 同步新增两字段 | FN-06 |
| `config/SaTokenConfig.java` | 无需改（`/api/exec/**` 仍开放；`/api/doc/**`、`/api/config/import/**` 走登录鉴权，无需白名单） | — |

### 10.3 依赖新增（后端）

**无**。POI 5.2.3、Jackson、Jackson-dataformat-XML、Dom4j、OpenCSV 均已引入；`ZipOutputStream` 为 JDK 自带。**满足约束：后端不引入第二个 Excel 库**。

---

## 11. 数据库变更

### 11.1 幂等 migration 脚本

新增：`backend/src/main/resources/db/migration-response-format.sql`

```sql
-- CHG-020 UX-E FN-06：为 interface_config 补齐响应格式相关字段（幂等）
DELIMITER $$

DROP PROCEDURE IF EXISTS pg_add_response_format $$
CREATE PROCEDURE pg_add_response_format()
BEGIN
  IF NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS
                 WHERE TABLE_SCHEMA = DATABASE()
                   AND TABLE_NAME = 'interface_config'
                   AND COLUMN_NAME = 'response_format') THEN
    ALTER TABLE interface_config
      ADD COLUMN response_format VARCHAR(16) DEFAULT 'JSON'
      COMMENT 'FN-06 用户默认响应格式';
  END IF;

  IF NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS
                 WHERE TABLE_SCHEMA = DATABASE()
                   AND TABLE_NAME = 'interface_config'
                   AND COLUMN_NAME = 'response_headers') THEN
    ALTER TABLE interface_config
      ADD COLUMN response_headers TEXT DEFAULT NULL
      COMMENT 'FN-06 自定义响应头 JSON';
  END IF;
END $$
DELIMITER ;

CALL pg_add_response_format();
DROP PROCEDURE pg_add_response_format;
```

### 11.2 `init.sql` 更新

`interface_config` DDL 追加两列（供新库直接生效），并在文件头注释追加：

```sql
-- 兼容旧库（CHG-020 UX-E FN-06 新增字段）：请执行 migration-response-format.sql
```

### 11.3 其它子项：无表结构变更

- FN-07/09/10 均只读现有配置；不新增表
- FN-08 纯前端
- FN-11 只 CRUD 现有表，不新增

---

## 12. 依赖变更总表

| 类别 | 变更 |
|------|------|
| 后端 Maven 依赖 | **无新增**（POI/Jackson/Dom4j/OpenCSV 已就绪） |
| 前端 npm 依赖 | 若原本没有：新增 `codemirror@5.65.x` + `vue-codemirror@6.x`（仅 FN-08 用） |
| 数据库 | 幂等 migration 1 份（FN-06 加 2 列） |

---

## 13. 测试用例

严格遵循 CLAUDE.md TDD 规约，测试类命名 `{FNxx}xxxTest.java`，**全部**加 `@ActiveProfiles("test")`。

### 13.1 FN-06 多报文格式

`FN06ExecFormatTest`（`@WebMvcTest(ExecController.class)`）

- `SELECT_Accept_JSON_返回JSON`：默认路径不受影响
- `SELECT_Accept_XML_返回XML且Content-Type正确`
- `SELECT_query_format_csv_覆盖Accept`
- `SELECT_未指定Accept_读取config默认响应格式`
- `INSERT_Accept_XML_返回XML包装的影响行数`
- `未知format_400`

`FN06AcceptNegotiatorTest`（工具类）

- 5 种优先级组合下的返回

### 13.2 FN-07 字段清单

`FN07FieldSchemaTest`（Service 层）

- SELECT 接口导出：请求 Sheet 含所有 condition 字段；响应 Sheet 含所有返回字段；中文注释来自表结构
- INSERT 接口导出：REQUEST 字段列有 "REQUEST"；CONST 字段列有 "CONST=xxx"；响应 Sheet 只有「影响行数」
- 不存在的接口：抛 404

### 13.3 FN-08 XML 调试

- 纯前端能力，无后端测试
- 手工用例（记录在任务计划）：调试页选 XML 语言，输入 `<root><id>1</id></root>`，能高亮着色；execute 后端返回正确

### 13.4 FN-09 接口文档

`FN09DocumentServiceTest`

- 转换接口 Markdown 生成：包含渠道名 + 模板名 + 字段映射表
- 可视化接口 HTML 生成：请求字段表 & 响应字段表 & 样例（JSON+XML）
- 全量打包 zip：manifest.json 条目数与遍历结果一致

### 13.5 FN-10 报表下载

`FN10ExcelExportUtilTest`（工具类）

- 空列表：仅生成 header 行
- 100 条数据：sheet 名安全化（含特殊字符替换）
- 自适应列宽（断言 `sheet.getColumnWidth(0) > 0`）

`FN10InterfaceListExportTest`（Service）

- 按 name 过滤后导出，行数等于过滤后数量

### 13.6 FN-11 导入导出

`FN11ConfigExportServiceTest`

- 全量导出：zip 结构完整，manifest.json 字段完备
- 选中 3 条导出：zip 内 3 个 .json 文件
- 导出内容不含 id / createTime

`FN11ConfigImportServiceTest`

- OVERWRITE：已有模板 name 匹配则更新
- SKIP：已有则跳过，`imported` 计数正确
- ASK：不落库，返回 preview 列表
- 事务性：中间条目 JSON 损坏 → 全量回滚
- 主键冲突：转换模板 `(name, src, target)` 三元组匹配

---

## 14. 验收标准

### FN-06

1. 已发布接口 `curl -H "Accept: application/xml" -d '{"params":{}}' /api/exec/{id}` 返回合法 XML
2. `curl "/api/exec/{id}?format=csv"` 返回合法 CSV，首行为列头
3. 未带 Accept 时，行为等同于 M2-7（`Result<T>` JSON）
4. 前端 `InterfaceList` 显示每个接口的响应格式；编辑页可修改并保存

### FN-07

1. 「导出字段」按钮下载的 xlsx 打开可读，两个 sheet 完整
2. 中文注释列在有注释的字段上非空
3. SELECT 接口的响应字段列数 = 配置的返回字段数

### FN-08

1. `MessageDebug.vue` 输入区在 XML 模式下有语法高亮
2. 语言切换不清空已输入内容
3. XML 输入通过 execute 能正常调用已发布接口（依赖 FN-06 完成）

### FN-09

1. 「接口文档」菜单页可以列出所有转换模板 + 所有已发布可视化接口
2. 每条可单独下载 Markdown / HTML；HTML 用浏览器打开即可阅读，表格样式正确
3. 「全量导出」下载 zip，解压后目录结构与 manifest 一致

### FN-10

1. 9 个列表页全部有「导出报表」按钮，点击下载 xlsx
2. 导出的 xlsx 内容与当前页面查询过滤后的列表一致
3. 大列表（1w+ 行）导出无 OOM（POI 使用 `SXSSFWorkbook` streaming，如内存有压力可后续切换，本次先用 `XSSFWorkbook`）

### FN-11

1. 全量导出转换接口 zip，删掉库中所有相关表再导入，恢复完全一致（对比字段值）
2. SKIP 模式重复导入 = 无变化
3. OVERWRITE 模式重复导入 = 更新字段（可以修改导出 zip 中某模板 name 一致下的 mapping_rule 后二次导入验证）
4. ASK 模式返回 preview 后，勾选部分覆盖部分跳过，最终结果符合勾选
5. zip 结构损坏 → 400 明确错误信息，无脏数据入库（事务回滚）

---

## 15. 实施顺序建议

按依赖顺序（能并行的用 `//` 标记）：

```
Step 1  FN-06 后端：AcceptNegotiator + ExecController 改造 + migration 脚本
        │
        └── 阻塞 FN-07 前端「响应格式」按钮的默认值展示
        └── 阻塞 FN-08「XML 提交能触达后端」的端到端验证

Step 2  FN-10 后端：ExcelExportUtil + 各 Service 的 exportXxx 方法
        │                             // 与 FN-07 并行
        └── 复用工具立刻能被 FN-07 用（FieldSchemaService 内部结构相似）

Step 3  FN-07 后端 + 前端 // FN-08 前端（CodeEditor 组件 + MessageDebug 改造）

Step 4  FN-06 前端：4 个配置页 + InterfaceList 表单增强 + ResponseHeadersEditor
        │
        └── 依赖 Step 1 后端字段就位

Step 5  FN-09 后端 DocumentService（不依赖任何前置） // FN-11 后端 Export/Import Service

Step 6  FN-09 前端 InterfaceDocument.vue // FN-11 前端 InterfaceImportExport.vue

Step 7  端到端联调：pg-testkit 加冒烟脚本
        - 发布一接口 → 切响应格式 → curl 各 Accept
        - 导出字段清单 → 用户下载确认
        - 导出全量配置 → drop 后重新导入
```

**关键路径**（若资源紧张要砍范围）：FN-06 > FN-10 > FN-11 > FN-07 > FN-09 > FN-08

理由：FN-06 影响对外接口契约（放开的越晚，改动成本越高）；FN-10 覆盖 9 个页面，是"用户感知最强的"；FN-11 决定了跨环境迁移能力；FN-07/09 属于「有更好，晚一版也行」；FN-08 纯 UX 提升，最低优先级。

---

## 附录：与其它 UX 组的接口契约

| 关联组 | 契约点 | 说明 |
|-------|--------|------|
| UX-A | tokens.css 已定义的 `--panel-bg` / `--table-header-bg` 等变量 | 本组新页面必须使用 tokens，不硬编码颜色 |
| UX-B | 菜单分组决定 `InterfaceDocument` 和 `InterfaceImportExport` 挂在哪一节 | 本组只提供组件与路由，菜单挂点由 UX-B 拍板 |
| UX-C | 字段映射的中文含义（M1-2 mapping_rule） | FN-07 / FN-09 复用；不要求 UX-C 前置完成 |
| UX-D | 转换接口向导 → 保存链路 | FN-11 导入的转换模板应能被 UX-D 向导编辑；仅测试兼容，不改代码 |
| UX-F | 已修复的中文乱码 | FN-07/09/10 导出中文默认按 UTF-8，若客户端 MySQL 字符集有问题，UX-F 已给出修复方案 |
