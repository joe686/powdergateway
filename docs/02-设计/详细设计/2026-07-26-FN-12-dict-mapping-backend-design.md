# FN-12 字典映射管理 · 后端设计（v0.2.0 ①）

> **单元**：FN-12（新增）· **版本**：v0.2.0 ① · **CR 依据**：[CR-001](../../06-项目管理/待办与缺陷池.md#cr-001-字典转换配置字段字典映射)
> **反馈来源**：[FB-039](../../06-项目管理/反馈簿.md)（2026-07-22 · 用户拍板 B 方案）· [FB-040](../../06-项目管理/反馈簿.md)（2026-07-26 · 联动 FN-09）
> **设计日期**：2026-07-26 · **状态**：✅ 已 brainstorm · 待 writing-plans
> **前置**：v0.1.1 已发布（本地 tag，master 已推）

---

## 零、上下游与本 spec 边界

FN-12 完整交付分 4 步（[路线图 § v0.2.0](../../06-项目管理/路线图.md)）：

```
① FN-12 后端  ← 本 spec
   ↓
② DictMappingProcessor 集成 M1-3
   ↓
③ 前端 DictMappingList.vue + 三处向导嵌入
   ↓
④ FN-12 × FN-09 联动（Excel 多 sheet · md/html 加字典 key 列）
```

**本 spec 只覆盖 ①**：`dict_mapping` 表 + Service + Controller + Redis 缓存 + Excel 导入导出。②③④ 各自另有 spec。

---

## 一、范围与边界

### 1.1 做

- `dict_mapping` 表 DDL + MyBatis-Plus 实体 + Mapper
- `DictMappingService`：CRUD · 双向拆条 · Redis 缓存加载/失效 · Excel 导入导出
- `DictMappingController`：REST + Sa-Token 认证
- Redis 缓存 `dict:{system}:{dictKey}:{direction}` → HashMap<source,target>，TTL 3600s
- 单元测试 ≥ 15 用例（TDD Red-Green-Refactor）

### 1.2 不做（划出 v0.2.0 ①）

| 项 | 归属 |
|---|---|
| `DictMappingProcessor` 集成 M1-3 `FieldProcessor` | v0.2.0 ② |
| 前端 `DictMappingList.vue` + 三处向导嵌入 | v0.2.0 ③ |
| FN-09 × FN-12 Excel 多 sheet 联动 | v0.2.0 ④ |
| 按 system_code 批量清空 · orphan 字典视图 · pipeline 优化 | v0.2.1（YAGNI） |

---

## 二、数据模型

### 2.1 DDL（追加到 `backend/src/main/resources/db/init.sql`）

```sql
-- 12. 字典映射表（CR-001 · FN-12）
CREATE TABLE IF NOT EXISTS dict_mapping (
  id            BIGINT       PRIMARY KEY AUTO_INCREMENT,
  system_code   VARCHAR(64)  NOT NULL COMMENT '对端系统标识（业务代号，如 CIF/CORE/CRM）· 自由文本 · 前端下拉去重',
  dict_key      VARCHAR(128) NOT NULL COMMENT '字典标识，如 GENDER / ACCT_STATUS',
  direction     TINYINT      NOT NULL COMMENT '1=出向(PG→对端)  2=入向(对端→PG)',
  source_value  VARCHAR(255) NOT NULL COMMENT '源值',
  target_value  VARCHAR(255) NOT NULL COMMENT '目标值（多对一允许 target 重复）',
  cn_label      VARCHAR(255)          COMMENT '中文含义',
  status        TINYINT      DEFAULT 1 COMMENT '1=启用 0=停用',
  deleted       TINYINT      DEFAULT 0 COMMENT '软删除',
  create_time   DATETIME     DEFAULT CURRENT_TIMESTAMP,
  update_time   DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_src (system_code, dict_key, direction, source_value),
  KEY idx_lookup (system_code, dict_key, direction, source_value)
);
```

- **软删除**：MyBatis-Plus `deleted=1` 自动过滤（对齐项目全局约定）
- **多对一**：`uk_src` 只锁 `source_value`，允许多个 source 映射到同一 target
- **不锁 target_value**：允许 `M→1, F→0, U→0`（多个源 → 同 target=0）
- **`system_code` 自由文本**：无 FK 约束，前端下拉从 `SELECT DISTINCT system_code FROM dict_mapping WHERE deleted=0` 拉历史值去重

### 2.2 Redis 缓存

| 项 | 值 |
|---|---|
| **Key** | `dict:{system_code}:{dict_key}:{direction}` |
| **Value** | `HashMap<source_value, target_value>` |
| **TTL** | 3600 秒（1h） |
| **加载时机** | 首次 `lookup` 时读 DB → 全量装载该 (system,key,direction) 三维度的映射 → 写 Redis |
| **失效时机** | CRUD 任何一条 → 精准 `DEL dict:{系统}:{key}:{方向}` 一个 key |
| **降级** | Redis 挂了 → 走 DB fallback（Mapper.selectByLookup）+ WARN 日志一次 |

**为什么用 HashMap 而不是 String key-per-entry**：单次接口调用可能触发多次同 (system,key,direction) 的 `source → target` 查询，一次 HGET 比 N 次 GET 快；且失效时一次 DEL 即可，不用扫描前缀。

---

## 三、组件与 API

### 3.1 后端类清单

| 类 | 位置 | 职责 |
|---|---|---|
| `DictMapping` | `backend/src/main/java/com/powergateway/model/DictMapping.java` | 实体 · `@TableName("dict_mapping")` · `@TableLogic` |
| `DictMappingMapper` | `backend/src/main/java/com/powergateway/dao/DictMappingMapper.java` | MP Mapper · `selectByLookup(system,key,direction)` 返回全量 |
| `DictMappingService` | `backend/src/main/java/com/powergateway/service/DictMappingService.java` | CRUD + 双向拆条 + Redis + Excel |
| `DictMappingController` | `backend/src/main/java/com/powergateway/controller/DictMappingController.java` | REST + Sa-Token + Swagger |
| `DictMappingExcelRow` | `backend/src/main/java/com/powergateway/model/dto/DictMappingExcelRow.java` | Excel 行 DTO · POI 手工解析（无注解） |
| `DictMappingImportResult` | `backend/src/main/java/com/powergateway/model/dto/DictMappingImportResult.java` | `{successCount, failedRows:[{rowIndex,errorMsg}]}` |
| `DictMappingExcelHelper` | `backend/src/main/java/com/powergateway/utils/DictMappingExcelHelper.java` | POI 读写工具（`parse(InputStream)` + `build(List<DictMapping>)`） |

### 3.2 REST API

| 方法 | 路径 | 角色 | 用途 |
|:-:|---|:-:|---|
| GET | `/api/dict-mapping/list` | 三角色 | 分页 + 筛选（system/dictKey/direction/status） |
| GET | `/api/dict-mapping/systems` | 三角色 | 返回 `DISTINCT system_code` 列表（前端下拉） |
| GET | `/api/dict-mapping/{id}` | 三角色 | 单条详情 |
| POST | `/api/dict-mapping` | admin/user | 新增 · body `bidirectional=true` 时后端拆 2 条 |
| PUT | `/api/dict-mapping/{id}` | admin/user | 编辑 · **不允许改 direction**（要改 direction → 删了重建） |
| DELETE | `/api/dict-mapping/{id}` | admin/user | 软删 + 精准失效 Redis |
| POST | `/api/dict-mapping/import` | admin/user | Excel 上传（`MultipartFile`） |
| GET | `/api/dict-mapping/export` | 三角色 | Excel 下载（支持按筛选） |
| POST | `/api/dict-mapping/lookup` | 内部（Processor 用） | `{system,dictKey,direction,source}` → `{target,cnLabel}` 或 404 |

### 3.3 Excel 导入格式

**列头**（中英双语，`direction` 支持数字或中文）：

| 系统代号 system_code | 字典标识 dict_key | 方向 direction | 源值 source_value | 目标值 target_value | 中文含义 cn_label | 状态 status |
|---|---|---|---|---|---|---|
| CIF | GENDER | 1 (或"出向") | M | 1 | 男 | 1 |
| CIF | GENDER | 1 | F | 0 | 女 | 1 |
| CIF | GENDER | 2 (或"入向") | 1 | M | 男 | 1 |

**策略**：

| 场景 | 处理 |
|---|---|
| **一条错整体回滚** | 事务包裹整个 import；任何一行校验失败 → 全部回滚 |
| **报错行号** | 返回 `DictMappingImportResult { successCount=0, failedRows:[{rowIndex:5, errorMsg:"direction 必须为 1 或 2"}] }` |
| **重复行覆盖** | `(system,key,direction,source)` 命中已有条目 → 覆盖 `target/cnLabel/status`（幂等，方便批量刷） |
| **direction 兼容** | 数字 `1/2` OR 中文 `出向/入向` OR 英文 `OUT/IN` 都接受 |
| **表头容错** | 只校验必填列（前 5 列），额外列忽略 |

**依赖**：**复用项目已有的 Apache POI 5.2.3**（M2-2 表结构导出已用）。CR-001 § 扩展 A 原写"实施时选 EasyExcel"但项目已有 POI 时应复用，避免新增依赖（YAGNI）。POI 手写 Workbook 读写虽多几行代码，但依赖树更干净。

---

## 四、权限与审计

### 4.1 认证

- Sa-Token cookie，未登录 → 401
- 走既有 `SaTokenConfig`，无需新增拦截器

### 4.2 授权（Q1 拍板：三角色均可访问，admin/user 可写 · readonly 只读）

**项目惯例（重要）**：既有代码**不使用 `@SaCheckRole`**，角色权限拦截统一由**前端菜单路由白名单**（`MenuPermission`）承担；后端 Controller 只做 Sa-Token 登录验证。

- **v0.2.0 ① 后端阶段**：Controller 层无角色注解，`SaTokenConfig` 认证即可
- **v0.2.0 ③ 前端阶段**：在 `MenuPermission.java` 添加路由白名单
  - `ADMIN_MENUS` 和 `USER_MENUS` 追加 `/tools/dict`（可写）
  - `READONLY_MENUS` 追加 `/tools/dict/view`（如做只读页）或干脆不加
- **风险与缓解**：仅前端拦截存在越权风险（懂 API 的人可绕过前端直调 REST）；但对齐项目现状，且字典 CRUD 属于配置类操作不是敏感数据，等 v0.5.0 SYS-3 大升级引入统一后端 RBAC 后一并加强

### 4.3 审计

每个 CRUD 方法加 `@SysLogRecord(module = "字典管理", action = "保存字典|删除字典|导入字典|导出字典")`，走既有 `SysLogAspect` 异步写 `sys_log`。

**注意 `@SysLogRecord` 签名**：只有 `module` 和 `action` 两个 String 参数（不是 `opType` 枚举）。

---

## 五、错误处理与未命中

### 5.1 表单校验

- Controller 层 `@Valid` + `BindingResult`，字段级返回错误
- `system_code`、`dict_key`、`source_value`、`target_value` 均 `@NotBlank`
- `direction` `@Min(1) @Max(2)`

### 5.2 业务校验（Service 层）

| 场景 | 异常 |
|---|---|
| 同 (system,key,direction,source) 已存在（新增） | `BusinessException(409, "已存在同源值映射：{system} / {dictKey} / {source}")` |
| direction 非 1/2 | `BusinessException(400, "direction 必须为 1(出向) 或 2(入向)")` |
| 编辑时改 direction | `BusinessException(400, "不允许修改方向，请删除后重建")` |
| 删除不存在 id | `BusinessException(404, "字典条目不存在或已删除")` |

### 5.3 未命中（Q5 拍板：严格模式）

`lookup` API：

```java
public DictMappingLookupResult lookup(String system, String dictKey, int direction, String source) {
    // 1. Redis HGET
    // 2. miss → DB Mapper.selectByLookup 全量装载 + 写 Redis
    // 3. HashMap.get(source)
    // 4. 找不到 → 返回 404（Controller 层）；Processor 侧包装成 BusinessException(400, "字典 GENDER 值 M 在系统 CIF 未定义映射")
}
```

- **不提供宽松/透传模式**（YAGNI，若后续用户反馈再加）

### 5.4 Redis 降级

- Redis 挂了（`RedisConnectionFailureException`）→ 走 DB fallback
- 日志级别 `WARN`（不 ERROR，避免刷屏）
- 不阻塞主链路

### 5.5 Excel 导入错

```java
public DictMappingImportResult importExcel(MultipartFile file) {
    // 事务包裹整个方法
    // 逐行解析 → 校验 → INSERT/UPDATE
    // 任何一行抛异常 → 事务回滚 → 返回 failedRows
}
```

---

## 六、测试策略与验收

### 6.1 测试用例目标 ≥ 15 条（TDD Red-Green-Refactor 逐 Task 加）

**Service 层（H2 + `@Transactional`）**：
- `save_正常_单向` · `save_双向_拆条产生2条`
- `save_唯一约束冲突_抛BusinessException`
- `save_多对一允许_源不同目标同`
- `update_修改direction_拒绝`
- `delete_软删_精准失效Redis`
- `lookup_命中Redis` · `lookup_miss走DB并装载Redis` · `lookup_全miss返回404`
- `importExcel_正常` · `importExcel_一行错整体回滚`
- `importExcel_重复行覆盖`

**Controller 层（MockMvc + Sa-Token）**：
- `list_readonly_可访问` · `save_readonly_403`
- `save_未登录_401`
- `export_三角色_可访问`

**Mapper 层（H2）**：
- `selectByLookup_返回全量` · `selectByLookup_status=0过滤`

### 6.2 验收门槛

- 后端 `mvn test` 全绿（新增 ≥ 15 用例，v0.1.1 基线 555 用例继续绿）
- Swagger UI 能看到 9 个新 API 分组 `dict-mapping`
- 手工冒烟：Postman 调 POST 新增双向条目 → 观察 DB 2 条 · 调 lookup 命中 · 删除后再 lookup 404
- 手工测试指南在 v0.2.0 ③ 前端完成后补 `MT-19-* 字典管理` 一组（本 ① 阶段先跑 Swagger）

### 6.3 手工测试暂缓

v0.2.0 ① 只交付后端，无 UI。手工验证走 Swagger + DB 校验 SQL 即可，正式 P1 手工测试等 v0.2.0 ③ 前端交付后一并跑。

---

## 七、升级路径预留（Q2 承诺）

若未来（v0.4.0 或更后）需硬约束 `system_code`：

```sql
-- 步骤 1：新建系统清单表
CREATE TABLE sys_system (
  code        VARCHAR(64)  PRIMARY KEY,
  name        VARCHAR(128),
  description VARCHAR(255),
  status      TINYINT      DEFAULT 1,
  create_time DATETIME     DEFAULT CURRENT_TIMESTAMP
);

-- 步骤 2：从既有字典去重回填
INSERT INTO sys_system (code, name)
  SELECT DISTINCT system_code, system_code
  FROM dict_mapping WHERE deleted=0;

-- 步骤 3：加 FK
ALTER TABLE dict_mapping
  ADD CONSTRAINT fk_dict_system
  FOREIGN KEY (system_code) REFERENCES sys_system(code)
  ON DELETE RESTRICT;
```

**数据零丢失** · **v0.2.0 ① 无需为此预留任何代码**（自由文本已经能兼容）。此段仅作实施 CR 到来时的技术卡片。

---

## 八、依赖与影响面

| 项 | 影响 |
|---|---|
| **新增 Maven 依赖** | 无（复用既有 `poi-ooxml:5.2.3`） |
| **配置库 DDL** | +1 表 `dict_mapping` |
| **审计库** | 无变更 |
| **Redis** | 新增 key 前缀 `dict:*`，占用小 |
| **既有代码修改** | 无（v0.2.0 ② 才会修改 `FieldProcessor` / `ProcessRuleType`） |
| **既有测试影响** | 无（新增用例，不改既有） |

---

## 九、CHG 归档（实施完成后）

`DictMapping` 是新单元，非"对已交付单元的行为增删"，**技术上不构成范围变更**，但为完整可追溯性建议：

- 完成后新增 `CHG-028` 归档到 [变更记录.md](../../03-开发/变更记录.md)：`新增 FN-12 字典映射管理（后端 · v0.2.0 ①）`
- 更新 `docs/01-需求/需求拆分与最小实现方案.md` 追加 FN-12 单元描述
- 更新 `docs/03-开发/开发计划.md` 表格

---

## 十、相关文档

- [CR-001 待落地条目](../../06-项目管理/待办与缺陷池.md#cr-001-字典转换配置字段字典映射)
- [FB-039 反馈原文](../../06-项目管理/反馈簿.md)（2026-07-22）
- [FB-040 联动反馈](../../06-项目管理/反馈簿.md)（2026-07-26 · CR-001 § 扩展 A）
- [路线图 § v0.2.0](../../06-项目管理/路线图.md)
- [手工测试指南](../../04-测试/v0.1.0-手工测试指南.md)（v0.2.0 ③ 落地后追加 MT-19-* 字典管理组）
- 实施 TDD 分解将保存至 `docs/03-开发/任务计划/2026-07-26-FN-12-backend.md`（writing-plans 阶段产出）
