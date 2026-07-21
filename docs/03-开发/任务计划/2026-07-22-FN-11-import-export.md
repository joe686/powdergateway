# FN-11 · 配置导入导出扩展 · 实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 把「配置导入/导出」升级为可以生成 Excel / Markdown 的双向管道，Excel 支持"每接口一个 xlsx"的反导入，并把「接口文档」菜单合并进来，全部收拢在「辅助工具 → 文档与配置」一个页面里。

**Architecture:** 后端沿用现有 `ConfigImportService` / `ConfigExportService` / `InterfaceDocumentService` 三个服务，加入 `ExcelConfigCodec`（接口配置 ↔ .xlsx 双向）、`ExcelTemplateCodec`（转换模板 ↔ .xlsx 双向）、`MarkdownConfigCodec`（配置 → .md 只读）；`ConfigImportExportController` 新增 4 个端点（`/export/excel` / `/export/markdown` / `/import/excel` / `/import/preview`），复用 `ConflictStrategy` 与 `ImportResult`；前端把 `InterfaceImportExport.vue` 与 `InterfaceDocument.vue` 合并为 `DocsAndImportExport.vue` 三 Tab（文档预览 / 导出 / 导入），旧路由 `301` 重定向到新页面。

**Tech Stack:** Spring Boot 2.7.18, MyBatis-Plus 3.5.7, `poi-ooxml 5.2.3`（已引入）, Jackson, Vue 3 `<script setup>`, Element Plus (`el-tabs`/`el-upload`/`el-radio-group`/`el-checkbox-group`/`el-descriptions`/`ElMessage`/`ElMessageBox`), axios（走 `frontend/src/api/request.js` 且 responseType=blob 自动绕过 Result 解包 · commit 9506731）

## Global Constraints

- 所有文档、注释、提交信息、UI 文案一律中文
- 前端 HTTP 一律走 `frontend/src/api/request.js`；下载走 `responseType: 'blob'`
- Excel 文件命名规范：`{接口类型}_{接口名}_{yyyyMMdd_HHmmss}.xlsx`、`TEMPLATE_{模板名}_{src}to{tgt}_{yyyyMMdd_HHmmss}.xlsx`，禁用非法文件名字符（\/:*?"<>|），转成下划线
- Excel 每个文件带隐藏 sheet `_meta`，含 `schemaVersion=1`、`exportedAt`、`sourceInterfaceId`（仅用于展示，非导入依据）；schemaVersion 不匹配时导入阶段直接拒绝并提示"请重新从新版本导出"
- Excel 数组字段路径统一 `items[*].a` 语法：`[*]` 代表"数组下的元素"，`.` 分隔嵌套 object（与 CHG-004 `getNestedValue` 保持一致的点号路径习惯）
- 反导入策略：仅支持"全量覆盖"（无 patch 模式）；SKIP/OVERWRITE/ASK 三种冲突策略沿用现状
- 一次上传的多个 excel 必须同类型（全接口 or 全模板），前端按文件名前缀（`QUERY_/INSERT_/UPDATE_/DELETE_/TEMPLATE_`）校验，混选阻断并提示
- Markdown 只导出、不导入（决策点 1）；用户改了 md 反导回要走 excel 路径
- 每个接口一个独立 xlsx（决策点 2）；多接口下载时打成 zip
- 菜单合并（决策点 3）：`/interface/import-export` 与 `/interface/doc` 合并为 `/tools/docs`（放在「辅助工具」大类下），旧路径保留并重定向到新路径避免收藏栏死链
- 所有 Java 测试类必须加 `@ActiveProfiles("test")`
- TDD Red-Green-Refactor，禁止跳步：先失败测试 → 最小实现 → 通过 → 重构 → 再通过 → commit
- 每个 Task 结束时 `git commit`，提交信息前缀 `feat(FN-11):` / `test(FN-11):` / `refactor(FN-11):` / `chore(FN-11):`
- 完成后追加 `CHG-022 FN-11 导入导出扩展（Excel/Markdown/菜单合并）` 到 `docs/03-开发/变更记录.md`；`docs/01-需求/需求拆分与最小实现方案.md` FN-11 单元描述定稿；`docs/03-开发/开发计划.md` 对应行填交付时间

---

## 文件清单速览

| 操作 | 路径 | 用途 |
|------|------|------|
| 新增 | `backend/src/main/java/com/powergateway/service/codec/ExcelConfigCodec.java` | 接口配置 ↔ .xlsx 双向 |
| 新增 | `backend/src/main/java/com/powergateway/service/codec/ExcelTemplateCodec.java` | 转换模板 ↔ .xlsx 双向 |
| 新增 | `backend/src/main/java/com/powergateway/service/codec/MarkdownConfigCodec.java` | 配置 → .md（仅导出） |
| 新增 | `backend/src/main/java/com/powergateway/service/codec/PathExpression.java` | `items[*].a` 路径解析/构造工具（**独立单元**方便复用） |
| 修改 | `backend/src/main/java/com/powergateway/service/ConfigExportService.java` | 追加 `exportExcel(ids, type): byte[]` / `exportMarkdown(ids, type): byte[]` |
| 修改 | `backend/src/main/java/com/powergateway/service/ConfigImportService.java` | 追加 `importExcel(MultipartFile[], ConflictStrategy): ImportResult` 与前置 `previewExcel()` |
| 修改 | `backend/src/main/java/com/powergateway/controller/ConfigImportExportController.java` | 追加 `POST /api/config/export/excel`、`POST /api/config/export/markdown`、`POST /api/config/import/excel`、`POST /api/config/import/preview` |
| 修改 | `backend/src/main/java/com/powergateway/model/dto/ImportResult.java` | 补 `fileErrors: List<FileErrorItem>`（记录文件级失败原因 + 行号），不破坏现有字段 |
| 新增 | `backend/src/main/java/com/powergateway/config/MenuPermission.java` | 追加 `/tools/docs`，ADMIN + USER 有权限；`/interface/doc` / `/interface/import-export` 保留在白名单以支撑重定向 |
| 新增 | `backend/src/test/java/com/powergateway/FN11PathExpressionTest.java` | `items[*].a` 解析/构造/回读的正反双向测试 |
| 新增 | `backend/src/test/java/com/powergateway/FN11ExcelConfigCodecTest.java` | encode + decode 双向；覆盖 QUERY/INSERT/UPDATE/DELETE 四种接口类型 |
| 新增 | `backend/src/test/java/com/powergateway/FN11ExcelTemplateCodecTest.java` | 转换模板 encode + decode；覆盖嵌套 JSON 路径 |
| 新增 | `backend/src/test/java/com/powergateway/FN11MarkdownCodecTest.java` | 仅 encode；断言表头/字段列/条件列输出正确 |
| 新增 | `backend/src/test/java/com/powergateway/FN11ImportExportControllerExcelTest.java` | 端到端 controller：上传 → preview → import → 校验 DB；下载 → 解析 xlsx |
| 新增 | `frontend/src/views/tools/DocsAndImportExport.vue` | 合并页面，三 Tab（文档预览 / 导出 / 导入） |
| 修改 | `frontend/src/router/index.js` | 追加 `/tools/docs`；`/interface/doc` 与 `/interface/import-export` 改为 `redirect: '/tools/docs'` |
| 修改 | `frontend/src/components/layout/SideMenu.vue` | 「辅助工具」下把「接口文档」和「配置导入/导出」两项合成一项「文档与配置」；`TOOLS_PATHS` 增补 `/tools/docs` |
| 修改 | `frontend/src/api/interfaceImportExport.js` | 新增 `exportExcel(ids, type)` / `exportMarkdown(ids, type)` / `importExcel(files, strategy)` / `previewExcel(files)` |
| 新增 | `frontend/tests/views/DocsAndImportExport.spec.js` | Vitest 组件测试：Tab 切换、同类型文件校验、导出格式选择 |
| 修改 | `docs/03-开发/变更记录.md` | 追加 `CHG-022` |
| 修改 | `docs/01-需求/需求拆分与最小实现方案.md` | FN-11 单元范围/实现方案定稿 |
| 修改 | `docs/03-开发/开发计划.md` | FN-11 行填交付时间 |

---

## Task 1: `PathExpression` 工具（`items[*].a` 路径解析）

**Files:**
- Create: `backend/src/main/java/com/powergateway/service/codec/PathExpression.java`
- Create: `backend/src/test/java/com/powergateway/FN11PathExpressionTest.java`

**Interfaces:**
- Produces: `PathExpression`（无状态工具类）
  - `static Object read(Object root, String path)` — 从 `Map`/`List` 混合树里按 `a.b[*].c` 取值（`[*]` 位置返回 `List`）
  - `static void write(Map<String,Object> root, String path, Object value)` — 按路径写入，遇 `[*]` 自动创建 `List`；写入 `List` 元素时 value 应为 `List`
  - `static List<String> tokenize(String path)` — 拆分成段（用于 Excel 表头展示）
  - `static String join(List<String> tokens)` — 反向拼接

- [ ] **Step 1 (Red)**：写测试，全部失败
  - `read` 场景：`{"a":{"b":[{"c":1},{"c":2}]}}` + `"a.b[*].c"` → `[1,2]`
  - `write` 场景：空 map + `"a.b[*].c"` + `[1,2]` → `{"a":{"b":[{"c":1},{"c":2}]}}`
  - `tokenize("a.b[*].c")` → `["a","b","[*]","c"]`
  - `join(["a","b","[*]","c"])` → `"a.b[*].c"`
  - 边界：空路径、null root、`[*]` 出现在叶子（`items[*]` 直接取整个数组）
- [ ] **Step 2 (Green)**：最小实现，通过所有测试
- [ ] **Step 3 (Refactor)**：抽出 `Token`（KEY/ARRAY_MARKER）内部类，重跑测试全绿
- [ ] **Step 4**：`git commit -m "feat(FN-11): PathExpression 支持 items[*].a 路径读写"`

---

## Task 2: `ExcelConfigCodec` · 接口配置 ↔ .xlsx

**Files:**
- Create: `backend/src/main/java/com/powergateway/service/codec/ExcelConfigCodec.java`
- Create: `backend/src/test/java/com/powergateway/FN11ExcelConfigCodecTest.java`

**Interfaces:**
- Consumes: `PathExpression`, `InterfaceConfig`, `poi-ooxml`
- Produces:
  - `byte[] encode(InterfaceConfig config)` — 输出单个 xlsx 字节
  - `InterfaceConfig decode(InputStream xlsx)` — 反向解析；schemaVersion 不符抛 `IncompatibleSchemaException`；表头列名不符抛 `InvalidExcelStructureException(sheet, row, reason)`
  - Sheet 布局按方案文档 §3.1：`元数据` / `表配置` / `字段列表` / `条件配置` / `数据来源` / `加工规则` / `缓存配置` / `_meta`（隐藏）

- [ ] **Step 1 (Red)**：写 QUERY 接口的往返测试
  - 构造 `InterfaceConfig` with 主表+关联表+2 条件+3 字段+缓存开启
  - `encode → decode`：断言 configJson 语义等价（可用 `JsonNode.equals` 忽略字段顺序）
  - 断言 `_meta.schemaVersion=1`
  - 断言表头列名：`元数据` sheet 第 1 行必含 `["接口ID","接口名","类型","数据源","状态","描述"]`
- [ ] **Step 2 (Green)**：实现 QUERY 类型的 encode+decode 直到测试绿
- [ ] **Step 3 (Red)**：加 INSERT/UPDATE/DELETE 三个测试用例
- [ ] **Step 4 (Green)**：扩展 codec 覆盖四种类型
- [ ] **Step 5 (Red)**：兼容性异常测试
  - 手工构造 schemaVersion=2 的 xlsx → `decode` 抛 `IncompatibleSchemaException`
  - 手工构造缺失"元数据"sheet 的 xlsx → 抛 `InvalidExcelStructureException`
- [ ] **Step 6 (Green)**：实现兼容检查
- [ ] **Step 7 (Refactor)**：把每个 sheet 的读写抽成 `SheetHandler<T>` 内部策略，清理重复
- [ ] **Step 8**：`git commit -m "feat(FN-11): ExcelConfigCodec 接口配置 ↔ xlsx 双向编解码"`

---

## Task 3: `ExcelTemplateCodec` · 转换模板 ↔ .xlsx

**Files:**
- Create: `backend/src/main/java/com/powergateway/service/codec/ExcelTemplateCodec.java`
- Create: `backend/src/test/java/com/powergateway/FN11ExcelTemplateCodecTest.java`

**Interfaces:**
- Consumes: `PathExpression`, `ConvertTemplate`
- Produces:
  - `byte[] encode(ConvertTemplate t)` — Sheet：`元数据` / `字段映射` / `字段加工` / `_meta`
  - `ConvertTemplate decode(InputStream xlsx)`
  - `字段映射` 的路径列示例：`head.FunctionId`（普通嵌套）/ `body.items[*].amount`（循环报文体）

- [ ] **Step 1 (Red)**：构造一个 mappingRule 包含 `items[*].amount → dst.list[*].amt` 的模板，encode+decode 往返测试
- [ ] **Step 2 (Green)**：实现
- [ ] **Step 3 (Red)**：processRule（字段加工）测试：`{"字段":"amount","策略":"multiply","参数":{"factor":100}}` 往返
- [ ] **Step 4 (Green)**：实现
- [ ] **Step 5**：`git commit -m "feat(FN-11): ExcelTemplateCodec 转换模板 ↔ xlsx 双向"`

---

## Task 4: `MarkdownConfigCodec` · 配置 → .md（只导出）

**Files:**
- Create: `backend/src/main/java/com/powergateway/service/codec/MarkdownConfigCodec.java`
- Create: `backend/src/test/java/com/powergateway/FN11MarkdownCodecTest.java`

**Interfaces:**
- Produces:
  - `String encodeInterface(InterfaceConfig c)` — markdown 字符串
  - `String encodeTemplate(ConvertTemplate t)`

- [ ] **Step 1 (Red)**：断言 QUERY 输出含"## 主表"、"## 输出字段"、"## WHERE 条件"三个二级标题；断言 transform template 输出含"## 字段映射"表格
- [ ] **Step 2 (Green)**：实现（可复用 `InterfaceDocumentService` 已有 buildMarkdown 逻辑，避免重复；如已足够则本 codec 只做薄封装）
- [ ] **Step 3 (Refactor)**：若与 `InterfaceDocumentService.buildMarkdownForVisual/Transform` 重复，把公共逻辑抽到 `MarkdownConfigCodec`，`InterfaceDocumentService` 改为委托调用（保持既有 controller 行为零退化，由 Task 5 controller 层测试兜底）
- [ ] **Step 4**：`git commit -m "feat(FN-11): MarkdownConfigCodec 配置只读文档生成"`

---

## Task 5: `ConfigImportExportController` 新端点

**Files:**
- Modify: `backend/src/main/java/com/powergateway/service/ConfigExportService.java`
- Modify: `backend/src/main/java/com/powergateway/service/ConfigImportService.java`
- Modify: `backend/src/main/java/com/powergateway/controller/ConfigImportExportController.java`
- Modify: `backend/src/main/java/com/powergateway/model/dto/ImportResult.java`
- Create: `backend/src/test/java/com/powergateway/FN11ImportExportControllerExcelTest.java`

**Interfaces:**
- Produces:
  - `POST /api/config/export/excel` body: `{ ids: number[], type: "interface"|"template" }` → 单 ID 返回 xlsx，多 ID 返回 zip；Content-Disposition 附带文件名
  - `POST /api/config/export/markdown` body 同上；单 ID 返回 md 文件，多 ID 返回 zip
  - `POST /api/config/import/excel` multipart: `files[]` + `strategy` → `Result<ImportResult>`
  - `POST /api/config/import/preview` multipart: `files[]` → `Result<PreviewResult>` （不落库，只解析并返回将影响的接口/模板名+冲突详情，给前端做展示）
  - `ImportResult.fileErrors: List<FileErrorItem>` — 每项含 `fileName`, `sheet`, `rowIndex`, `reason`

- [ ] **Step 1 (Red)**：写 controller 测试
  - 单接口导出 excel：断言 Content-Type = `application/vnd.openxmlformats-officedocument.spreadsheetml.sheet`，Content-Disposition 含 `.xlsx`
  - 多接口导出 excel：断言 Content-Type = `application/zip`
  - 单接口导入 excel（新增）：断言 ImportResult.imported 含该接口名，DB 已插入
  - 冲突接口导入 excel（OVERWRITE 策略）：断言 configJson 已被替换
  - 混合类型上传（1 个 QUERY_ + 1 个 TEMPLATE_）：Controller 层应返回 400 + 明确错误 msg
  - schemaVersion 不匹配：`fileErrors` 应含该文件名 + 版本原因
  - preview 端点：断言不落库（DB 计数不变）+ 返回预期条数
- [ ] **Step 2 (Green)**：实现服务层 + controller，全部通过
- [ ] **Step 3**：确认下载路径已经命中 `frontend/src/api/request.js` Blob 分支（无需前端改动，controller 只要 `ResponseEntity<byte[]>` + 正确 Content-Type 就行）
- [ ] **Step 4**：`git commit -m "feat(FN-11): 新增 excel/markdown 导入导出 4 个端点"`

---

## Task 6: 菜单合并 · 前端 `DocsAndImportExport.vue`

**Files:**
- Create: `frontend/src/views/tools/DocsAndImportExport.vue`
- Modify: `frontend/src/router/index.js`
- Modify: `frontend/src/components/layout/SideMenu.vue`
- Modify: `frontend/src/api/interfaceImportExport.js`
- Modify: `backend/src/main/java/com/powergateway/config/MenuPermission.java`
- Create: `frontend/tests/views/DocsAndImportExport.spec.js`

**Interfaces:**
- Produces:
  - 页面三 Tab：
    - `文档预览`（继承 `InterfaceDocument.vue` 现有 2 tab 内嵌显示 + 下载按钮）
    - `导出`（左勾选接口/模板，右选择格式：md / excel / zip，下方"下载"按钮）
    - `导入`（Element `el-upload` 多选 + 文件名前缀校验 + 冲突策略 `el-radio-group` + 上传后展示 `ImportResult`）
  - `SideMenu.vue` 辅助工具下菜单项：
    - 「文档与配置」→ `/tools/docs`
    - 保留「报文调试」`/tools/debug`、「Swagger 文档」`/tools/swagger`
    - 删除「接口文档」和「配置导入/导出」两个入口（旧路径 router 层重定向到 `/tools/docs`）
  - `MenuPermission.ADMIN_MENUS` / `USER_MENUS` 追加 `/tools/docs`；`/interface/doc` / `/interface/import-export` 保留（有权限才能被重定向命中）

- [ ] **Step 1 (Red · 组件测试)**：Vitest 用例
  - 渲染三个 Tab；默认激活 `文档预览`
  - 上传混类型文件（QUERY_xxx.xlsx + TEMPLATE_xxx.xlsx）→ `ElMessage.error` 被调用 1 次
  - 上传纯 QUERY_ 文件 → 允许，进入策略选择
  - 导出 tab 选中 3 个接口 + 格式=md → 调 API `exportMarkdown([1,2,3], 'interface')`
- [ ] **Step 2 (Green)**：实现三 Tab，测试通过
- [ ] **Step 3**：手工端到端联调（本地起 backend + frontend）
  - 导出一个 QUERY 接口的 excel，用 Excel 打开检查结构；把某列改一下值，重新导入 → 应生效
  - 导出一个转换模板的 md，人工阅读语义正确
  - 全量导出 zip 按钮保留（backend 老端点 `/api/config/export`）
- [ ] **Step 4**：`git commit -m "feat(FN-11): 合并文档与导入导出为 /tools/docs 三 Tab 页面"`

---

## Task 7: 文档定稿 + 变更记录

**Files:**
- Modify: `docs/03-开发/变更记录.md`
- Modify: `docs/01-需求/需求拆分与最小实现方案.md`
- Modify: `docs/03-开发/开发计划.md`
- Modify: `docs/03-开发/任务计划/README.md`
- Modify: `docs/02-设计/详细设计/README.md`

- [ ] **Step 1**：追加 `CHG-022 FN-11 导入导出扩展`，含变更前/后描述 + 影响文件清单
- [ ] **Step 2**：FN-11 在需求拆分文档里的"范围/不含/实现方案/验收标准"改成 excel/md/合并菜单三点最终态
- [ ] **Step 3**：开发计划表格 FN-11 行填交付日期与状态"已完成"
- [ ] **Step 4**：`docs/03-开发/任务计划/README.md` 追加 `2026-07-22 FN-11 导入导出扩展` 一行；`docs/02-设计/详细设计/README.md` 保留 FN-11 方案链接
- [ ] **Step 5**：`git commit -m "docs(FN-11): 变更记录 + 需求拆分 + 开发计划 定稿"`

---

## 完成门槛

- 全部单元测试 + 集成测试绿（本单元预估新增 ~40 个 case）
- 手工端到端流程：导出 excel → 改内容 → 反导入 → DB 生效
- 全量 `mvn test` 无退化（现有 326 个测试 + 本单元新增全绿）
- 前端 `npm run build` 无报错
- 4 个 commit（Task 2/3/4/5 各一，Task 6 一，Task 7 一）
