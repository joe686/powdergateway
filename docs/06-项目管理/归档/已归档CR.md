# 已归档 CR（已交付落地）

已交付版本落地的 CR 归档区。**只增不改**。

## 归档规则

CR 落地对应 CHG · 版本 tag 打完后 · 从 [../待办与缺陷池.md § "已接受待落地 CR"](../待办与缺陷池.md) 迁入本文件 · 状态改 ✅ 已交付 · 补落地 CHG 编号。

## 索引

| CR | 需求 | 交付版本 | 落地 CHG |
|:-:|---|:-:|:-:|
| **CR-001** | 字典转换配置 · FN-12 字典映射全链路（+ 扩展 A 接口文档联动）| v0.2.0 | CHG-028/029/031/032 |

---

## CR-001 字典转换配置（字段字典映射）

- **提出日期**：2026-07-22
- **提出人**：用户
- **反馈来源**：[FB-039](../反馈簿.md) · [FB-040](../反馈簿.md)（扩展 A）
- **需求描述**：
  转换接口（模块一：接口转换 M1-x）与可视化接口（模块二：数据库 CRUD M2-x）都需要**跨系统字典值映射**能力。表设计要素：
  - 字典对接的系统号（对端系统标识）
  - 字典标识（如 `GENDER` / `ACCT_STATUS`）
  - A 系统字典值 / B 系统字典值（多对一：多个源值可映射到同一目标值）
  - 中文含义
  - **转换方向**：出向（PG → 对端）/ 入向（对端 → PG）/ 双向（"配置一次生成两条"）
- **动机**：现有 `FieldProcessor`（M1-3）只有 Trim/Pad/Substring/Case/TypeCast 5 种通用加工策略,跨系统集成时**普遍**需要字典码值转换（性别 M/F ↔ 1/0,账户状态 A/F ↔ ACTIVE/FROZEN 等）。当前用户只能通过硬编码脚本或多条字段加工规则拼凑,成本高、易错。补齐后可显著减少集成配置工时（对齐产品"减少接口开发工时 50%"目标）。
- **影响单元**：
  - **新增** FN-12 字典映射管理（表 + Controller + Service + 前端管理页）
  - **扩展** M1-3 `FieldProcessor` 新增 `DictMappingProcessor` 策略
  - **集成点** M1-6 报文转换（字段映射时可选加工规则 = 字典转换）、M2-3/4/5/6 可视化接口 4 步向导的字段加工步骤
  - **前端** 字段加工规则选择器新增"字典转换"类型 + 系统/字典/方向下拉

- **设计方案（B 方案已采纳）**：

  **表结构 `dict_mapping`**（B 方案 · 规范化单向存储 + 双向由前端拆条）：
  ```sql
  CREATE TABLE dict_mapping (
    id            BIGINT       PRIMARY KEY AUTO_INCREMENT,
    system_code   VARCHAR(64)  NOT NULL COMMENT '对端系统标识（关联 registry_config.service_name 或 db_connection.name）',
    dict_key      VARCHAR(128) NOT NULL COMMENT '字典标识,如 GENDER',
    direction     TINYINT      NOT NULL COMMENT '1=出向(PG→对端) 2=入向(对端→PG)',
    source_value  VARCHAR(255) NOT NULL COMMENT '源值',
    target_value  VARCHAR(255) NOT NULL COMMENT '目标值（多对一允许重复）',
    cn_label      VARCHAR(255)          COMMENT '中文含义',
    status        TINYINT      DEFAULT 1,
    deleted       TINYINT      DEFAULT 0,
    create_time   DATETIME     DEFAULT CURRENT_TIMESTAMP,
    update_time   DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_src (system_code, dict_key, direction, source_value),
    KEY idx_lookup (system_code, dict_key, direction, source_value)
  );
  ```
  - **前端"双向"复选框**：勾选后一次保存生成 2 条（direction=1 A→B, direction=2 B→A）,后端 Service 处理
  - **多对一**：`uk_src` 只对 `source_value` 唯一,允许多个源指向同一 `target_value`
  - **反查缓存**：Redis KEY `dict:{system}:{dict_key}:{direction}` → HashMap<source,target>,TTL 1h（对齐 M2-2 元数据缓存策略）

  **FieldProcessor 集成**：
  ```java
  new ProcessRule(ProcessRuleType.DICT_MAP, Map.of(
      "system", "CIF",
      "dictKey", "GENDER",
      "direction", "1"   // 1=出向 2=入向
  ))
  ```
  策略实现读 `Redis dict:CIF:GENDER:1` 查 target;未命中报 `BusinessException(400, "字典 GENDER 值 M 在系统 CIF 未定义映射")`。

- **前端 UI**：
  - 新增菜单 `辅助工具 → 字典管理`（`/tools/dict`,`DictMappingList.vue`）
  - 字段加工规则选择器（`FieldProcess.vue` / `InterfaceWizard.vue` 步骤 6）追加"字典转换"选项,三级联动下拉（系统 → 字典标识 → 方向）
  - 支持 Excel 导入导出（对齐 FN-11）

- **归入版本**：**v0.2.0**（用户 2026-07-22 确认 B 方案）
- **状态**：✅ 已交付 v0.2.0（2026-07-27）
- **落地 CHG**：
  - CHG-028 · 后端 dict_mapping 表 + Service + Controller + Redis 缓存 + POI Excel 导入导出（v0.2.0 ①）
  - CHG-029 · M1-3 集成 DictMappingProcessor + ProcessRuleType.DICT_MAP（v0.2.0 ②）
  - CHG-031 · 前端 DictMappingList.vue + 三处向导集成 + MenuPermission 三角色（v0.2.0 ③）
  - CHG-032 · FN-09 联动 · Excel xlsx 多 sheet + 字典 key 列（v0.2.0 ④ · 扩展 A 落地）

### 扩展 A · 字典 × 接口文档联动（来自 [FB-040](../反馈簿.md)，2026-07-26 追加 · 与 CR-001 主体同批 v0.2.0 交付）

字典功能落地后,`FN-09 接口文档` 输出同步升级：

1. **字段规格表新增"字典 key"列**
   - 影响：`InterfaceDocumentService.buildVisualModel()` 请求/响应字段表、`buildTransformModel()` 字段映射表
   - 列名：`字典标识`（对应 `dict_mapping.dict_key`）,空则渲染 `—`
   - 数据来源：接口配置中字段加工规则若为 `DICT_MAP` 类型,从 `params.dictKey` 提取

2. **新增 Excel(.xlsx) 多 sheet 下载**（用户 2026-07-26 确认方案 · CHG-032 落地）
   - 新增接口 `GET /api/interface-doc/{id}/xlsx` · FN-09 扩为**三格式**（md + html + xlsx）
   - Excel 结构：
     - `Sheet 1 · 基本信息`：接口名称 / 类型 / 状态 / 访问路径 / 默认响应格式
     - `Sheet 2 · 请求字段`：字段名 / 类型 / 必填 / 说明 / **字典 key**
     - `Sheet 3 · 响应字段`：字段名 / 类型 / 说明 / **字典 key**
     - `Sheet 4 · 字典对照`：按接口涉及的字典去重汇总
   - 转换模板文档：Sheet 2 改"字段映射"
   - zip 打包升级：每个接口出 md + html + xlsx 三份 · manifest.json 新增 `xlsx` 键
   - 依赖：EasyExcel（实际实施选用）

3. **落地实际工时**：+2 人日 · 8~12 新增用例（含字典关联提取、多 sheet 生成、空字典兼容、批量导出 manifest 兼容 · CHG-032 归档确认）
