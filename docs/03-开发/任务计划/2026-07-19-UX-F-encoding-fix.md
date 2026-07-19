# UX-F · 中文注释乱码修复 · 实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 收尾 CHG-011 OBS-1 / CHG-012 遗留的 `sys_config` 中文注释乱码问题（问题清单 OPS-01）：数据层重编码 + 环境指南 + 前端渲染兜底三管齐下。

**Architecture:** 数据层出幂等 SQL 迁移脚本反向解码已污染 `remark`；环境层出 `MySQL字符集配置指南.md` 指导客户改 `my.ini`；表现层前端加 `textSanitizer.js` 检测明显乱码后用占位符兜底。三者互不依赖，可并行；实施时按 Task 顺序单人执行更简洁。

**Tech Stack:** MySQL 8, Vue 3, JavaScript, Bash（无新增框架/依赖）

## Global Constraints

- 所有对话/注释/文档中文
- 前端严格用 `src/api/request.js`（本单元不新增请求，但保留约束）
- vue-draggable-next 只用 default slot + v-for（本单元不动 draggable）
- SQL 迁移脚本**幂等**：可反复执行、老库/新库均安全
- SQL 迁移前**必须**在指南里提示 `mysqldump` 备份
- 前端兜底工具**纯函数**，无副作用
- 完成后追加 `CHG-021` 到 `docs/03-开发/变更记录.md`，问题清单 OPS-01 从"待解决"搬到"已解决"

## 参考

- **对应 spec**：`docs/02-设计/详细设计/2026-07-19-UX-F-encoding-fix-design.md`
- **前置分析**：`docs/03-开发/变更记录.md` 中 CHG-011 OBS-1 与 CHG-012（已给根因、`TableMetaService.normalizeUrlForMetaQuery` 已部分缓解）
- **总览**：`docs/02-设计/详细设计/2026-07-19-visual-refresh-and-fixes-overview.md`

---

### Task 1: 前端 —— `textSanitizer.js` 兜底工具 + 手工核验

**Files:**
- Create: `frontend/src/utils/textSanitizer.js`

**Interfaces:**
- Consumes: 无
- Produces:
  - `isGarbled(s: string) : boolean`
  - `sanitize(s: string, placeholder?: string) : string`

- [ ] **Step 1: 写文件**

创建 `frontend/src/utils/textSanitizer.js`：

```js
// UX-F · 中文乱码检测与占位兜底
// 场景：某些历史数据被 MySQL 服务端字符集配置错误双重编码，
//       无法在前端修复，只能显示占位符避免视觉污染

// 触发规则：UTF-8 replacement char (U+FFFD) 或连续 3+ 个问号
const GARBLED_PATTERN = /[�]|\?{3,}|锟斤拷/

/**
 * 检测字符串是否为乱码
 * @param {string} s
 * @returns {boolean}
 */
export function isGarbled(s) {
  if (!s || typeof s !== 'string') return false
  return GARBLED_PATTERN.test(s)
}

/**
 * 若为乱码返回占位符，否则原样返回
 * @param {string} s
 * @param {string} placeholder
 * @returns {string}
 */
export function sanitize(s, placeholder = '（编码异常）') {
  return isGarbled(s) ? placeholder : s
}
```

- [ ] **Step 2: 浏览器控制台手工核验**

Run: `npm run dev`，浏览器打开任意页面 F12 Console 执行：

```js
const { isGarbled, sanitize } = await import('/src/utils/textSanitizer.js')

console.assert(isGarbled('锟斤拷') === true, '锟斤拷 应为乱码')
console.assert(isGarbled('���') === true, 'FFFD 应为乱码')
console.assert(isGarbled('?????') === true, '5 个问号 应为乱码')
console.assert(isGarbled('中文注释') === false, '正常中文 不应误判')
console.assert(isGarbled('') === false, '空串 不应误判')
console.assert(isGarbled(null) === false, 'null 不应误判')

console.assert(sanitize('锟斤拷', '坏了') === '坏了', 'sanitize 替换')
console.assert(sanitize('中文注释') === '中文注释', 'sanitize 保留正常文本')
console.log('textSanitizer 全部断言通过')
```

Expected: 所有 assert 无报错，最后 log "textSanitizer 全部断言通过"

- [ ] **Step 3: 提交**

```bash
git add frontend/src/utils/textSanitizer.js
git commit -m "feat(UX-F): 新增 textSanitizer.js 前端乱码兜底工具"
```

---

### Task 2: 前端 —— `SysConfig.vue` remark 列挂 sanitize

**Files:**
- Modify: `frontend/src/views/system/SysConfig.vue`

**Interfaces:**
- Consumes: `sanitize / isGarbled`（Task 1）
- Produces: SysConfig 页面 remark 列，遇乱码显示"（编码异常，请联系管理员）"，无 `?????` 视觉污染

- [ ] **Step 1: 加 import**

在 `<script setup>` 顶部加：

```js
import { sanitize, isGarbled } from '@/utils/textSanitizer'
```

- [ ] **Step 2: 改 remark 列**

找到现有 `<el-table-column label="备注" prop="remark">`（若结构不同，找 remark 列相关的 `<el-table-column>` 或 formatter），替换为：

```vue
<el-table-column label="备注" prop="remark">
  <template #default="{ row }">
    <span :class="{ 'pg-garbled': isGarbled(row.remark) }">
      {{ sanitize(row.remark, '（编码异常，请联系管理员）') }}
    </span>
  </template>
</el-table-column>
```

在同文件 `<style>` 中加：

```css
.pg-garbled {
  color: var(--pg-warning, #F59E0B);
  font-style: italic;
}
```

- [ ] **Step 3: 手工验证**

Run: 启动前后端，访问 http://localhost:5173/system/config

**若配置库中恰有乱码的 sys_config.remark**：应显示黄色斜体"（编码异常，请联系管理员）"
**若全部正常**：显示原始中文备注

若测试环境没有乱码数据，人为造一条：

```sql
INSERT INTO sys_config (config_key, config_value, remark)
VALUES ('test.garbled', 'x', '锟斤拷锟斤拷');
```

刷新页面 → 该行 remark 显示占位符 → 测试完毕删除：

```sql
DELETE FROM sys_config WHERE config_key = 'test.garbled';
```

- [ ] **Step 4: 检查其它 remark 展示位（可选，若发现则同步）**

Run: 用 Grep 搜前端：

```
Grep pattern: prop="remark"|:formatter=.*remark
```

若发现其它列表页也展示 remark 且未走 `sanitize`（如 `frontend/src/views/system/DbConnection.vue`），按上一步同样方式添加兜底。若无则跳过。

- [ ] **Step 5: 提交**

```bash
git add frontend/src/views/system/SysConfig.vue
# 若第 4 步动过其它文件则一并 add
git commit -m "feat(UX-F): SysConfig 与相关列表页 remark 列挂 sanitize 兜底"
```

---

### Task 3: 数据层 —— 幂等重编码 SQL 迁移脚本

**Files:**
- Create: `backend/src/main/resources/db/migration-encoding-fix.sql`

**Interfaces:**
- Consumes: 用户已按 Task 4 指南调整过 my.ini 并重启 MySQL
- Produces: 已污染的 `sys_config` / `db_connection` remark 列还原中文

- [ ] **Step 1: 写脚本**

创建 `backend/src/main/resources/db/migration-encoding-fix.sql`：

```sql
-- ================================================================
-- UX-F · 中文乱码数据修复脚本（幂等）
-- 场景：MySQL 服务端字符集配置错导致中文被双重编码为乱码（latin1 存 utf8 字节）
-- 原理：CONVERT(CAST(CONVERT(col USING latin1) AS BINARY) USING utf8mb4) 反向解码
-- 前置：确保 my.ini 已设 [mysqld] character-set-server=utf8mb4 且已重启
--       并已执行 mysqldump powergateway_config > backup.sql 备份
-- 幂等：正常 utf8 字节的行 REGEXP '[[:cntrl:]]' 为 false，UPDATE 会跳过
-- ================================================================

-- 1. 会话字符集
SET NAMES utf8mb4;
SET CHARACTER_SET_CLIENT     = utf8mb4;
SET CHARACTER_SET_CONNECTION = utf8mb4;
SET CHARACTER_SET_RESULTS    = utf8mb4;

-- 2. sys_config.remark 反向解码
UPDATE sys_config
SET remark = CONVERT(CAST(CONVERT(remark USING latin1) AS BINARY) USING utf8mb4)
WHERE remark IS NOT NULL
  AND remark <> ''
  AND remark REGEXP '[[:cntrl:]]'
  AND CONVERT(CAST(CONVERT(remark USING latin1) AS BINARY) USING utf8mb4) IS NOT NULL;

-- 3. sys_config 表字符集升级到 utf8mb4
ALTER TABLE sys_config
    CONVERT TO CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

-- 4. db_connection.remark 反向解码（若字段存在）
UPDATE db_connection
SET remark = CONVERT(CAST(CONVERT(remark USING latin1) AS BINARY) USING utf8mb4)
WHERE remark IS NOT NULL
  AND remark <> ''
  AND remark REGEXP '[[:cntrl:]]';

ALTER TABLE db_connection
    CONVERT TO CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

-- 5. 输出汇总（供人工核对）
SELECT COUNT(*) AS sys_config_total
  FROM sys_config WHERE remark IS NOT NULL AND remark <> '';
SELECT COUNT(*) AS db_connection_total
  FROM db_connection WHERE remark IS NOT NULL AND remark <> '';
```

- [ ] **Step 2: 手工造污染数据验证**

在 MySQL 客户端手动插入一条已知乱码：

```sql
USE powergateway_config;
-- 用 latin1 存 utf8 字节：模拟被污染的中文"缓存配置"
INSERT INTO sys_config (config_key, config_value, remark, value_type, group_name)
VALUES ('test.encoding', 'x',
        CONVERT(CAST('缓存配置' AS BINARY) USING latin1),
        'string', 'test');
SELECT remark FROM sys_config WHERE config_key = 'test.encoding';
-- 预期看到乱码
```

- [ ] **Step 3: 执行迁移脚本**

Run: `SOURCE backend/src/main/resources/db/migration-encoding-fix.sql;`
Expected:
- 无报错
- `SELECT remark FROM sys_config WHERE config_key = 'test.encoding';` 应显示正确的 "缓存配置"

- [ ] **Step 4: 二次执行验证幂等**

Run: 再次 `SOURCE backend/src/main/resources/db/migration-encoding-fix.sql;`
Expected: 无报错、无数据变化（remark 仍为 "缓存配置"，因不再匹配 `REGEXP '[[:cntrl:]]'`）

清理测试数据：

```sql
DELETE FROM sys_config WHERE config_key = 'test.encoding';
```

- [ ] **Step 5: 提交**

```bash
git add backend/src/main/resources/db/migration-encoding-fix.sql
git commit -m "feat(UX-F): 幂等 SQL 迁移脚本反向解码乱码 remark 列"
```

---

### Task 4: 文档 —— `MySQL字符集配置指南.md`

**Files:**
- Create: `docs/03-开发/MySQL字符集配置指南.md`

**Interfaces:**
- Consumes: 无
- Produces: 用户可按步骤修复本地 MySQL 服务端字符集，从根源杜绝新数据乱码

- [ ] **Step 1: 写指南**

创建 `docs/03-开发/MySQL字符集配置指南.md`：

````markdown
# MySQL 字符集配置指南（Windows）

## 症状识别

在 PG "系统管理 → 系统配置"页面中，若"参数注释"列显示 `????` 或"锟斤拷"，或前端已挂 `sanitize` 后显示黄色斜体"（编码异常...）"，说明服务端 MySQL 字符集配置错误。

## 修复步骤

### 1. 备份配置库

**必须先备份**：

```powershell
cd "C:\Program Files\MySQL\MySQL Server 8.0\bin"
.\mysqldump.exe -u root -p powergateway_config > D:\backup-powergateway_config-YYYYMMDD.sql
```

### 2. 找到 my.ini

默认位置：`C:\ProgramData\MySQL\MySQL Server 8.0\my.ini`（Windows 安装版）

或用 `services.msc` 找 MySQL80 服务 → 右键属性 → "可执行文件路径"含 `--defaults-file=...` 参数所指路径。

### 3. 修改配置

管理员身份打开记事本编辑 `my.ini`，找到（或新增）以下 3 个段：

```ini
[mysqld]
character-set-server=utf8mb4
collation-server=utf8mb4_unicode_ci
init-connect='SET NAMES utf8mb4'

[client]
default-character-set=utf8mb4

[mysql]
default-character-set=utf8mb4
```

保存文件。

### 4. 重启 MySQL 服务

管理员 PowerShell：

```powershell
Restart-Service MySQL80
```

或用 `services.msc` 手工重启 MySQL80 服务。

### 5. 核验字符集

用 MySQL 客户端登录：

```sql
SHOW VARIABLES LIKE 'character_set%';
```

**预期**：除 `character_set_filesystem=binary` 外，其他行 Value 应全部为 `utf8mb4`。

```
+--------------------------+--------------------+
| Variable_name            | Value              |
+--------------------------+--------------------+
| character_set_client     | utf8mb4            |
| character_set_connection | utf8mb4            |
| character_set_database   | utf8mb4            |
| character_set_filesystem | binary             |
| character_set_results    | utf8mb4            |
| character_set_server     | utf8mb4            |
| character_set_system     | utf8mb3            |
+--------------------------+--------------------+
```

### 6. 修复已有乱码

执行仓库中的 `backend/src/main/resources/db/migration-encoding-fix.sql`：

```sql
SOURCE D:/Project/powergateway/backend/src/main/resources/db/migration-encoding-fix.sql;
```

预期无报错。

### 7. 冒烟验证

浏览器访问 `http://localhost:5173/system/config`：**参数注释列应恢复中文显示，不再有黄色"编码异常"占位。**

## 常见问题

**Q：客户业务库表的中文注释也乱怎么办？**
A：本指南仅覆盖 PG 自身的配置库。客户业务表若也需修复，可按同样思路建自定义 SQL 脚本（把 `migration-encoding-fix.sql` 中的 `sys_config / db_connection` 替换为业务表名即可）。

**Q：Linux MySQL 也适用吗？**
A：适用。配置位置为 `/etc/mysql/my.cnf` 或 `/etc/my.cnf`；重启命令 `systemctl restart mysqld`。

**Q：修改后新的中文数据还是乱码？**
A：检查 JDBC URL 是否含 `useUnicode=true&characterEncoding=UTF-8`。项目 `TableMetaService.normalizeUrlForMetaQuery` 已在元数据查询时注入这些参数（CHG-012），业务连接可在数据源配置里检查。
````

- [ ] **Step 2: 提交**

```bash
git add "docs/03-开发/MySQL字符集配置指南.md"
git commit -m "docs(UX-F): 新增 MySQL 字符集配置指南（Windows 修复步骤）"
```

---

### Task 5: 变更记录 CHG-021 + 问题清单条目搬迁

**Files:**
- Modify: `docs/03-开发/变更记录.md`（追加 CHG-021）
- Modify: `docs/03-开发/问题清单.md`（OPS-01 从"待解决"搬到"已解决"）

**Interfaces:**
- Consumes: 无
- Produces: 项目管理文档同步

- [ ] **Step 1: 追加 CHG-021**

在 `docs/03-开发/变更记录.md` 中 CHG-015 之前（时间倒序）插入：

```markdown
### CHG-021 UX-F 中文注释乱码修复收尾

- **日期**：2026-07-XX（本单元交付日）
- **影响单元**：UX-F（新增，阶段六第 1 波）
- **变更类型**：Bug 修复 + 用户指南补齐
- **变更前**：
  - CHG-011 OBS-1 / CHG-012 已给根因，`TableMetaService.normalizeUrlForMetaQuery` 部分缓解（约 10% 列注释恢复）
  - `sys_config.remark` 显示 `????` / 锟斤拷 —— 用户无从修复
- **变更后**：
  - 新增前端 `frontend/src/utils/textSanitizer.js` 兜底工具（检测明显乱码 → 黄色斜体占位符）
  - `SysConfig.vue`（及发现的其它 remark 展示列表）挂 `sanitize` 无视觉污染
  - 新增 `backend/src/main/resources/db/migration-encoding-fix.sql` 幂等反向解码脚本
  - 新增 `docs/03-开发/MySQL字符集配置指南.md` 引导客户改 `my.ini` + `Restart-Service MySQL80`
- **影响文件**：详见任务计划 `docs/03-开发/任务计划/2026-07-19-UX-F-encoding-fix.md`
- **原因**：问题清单 OPS-01（源自 111.txt #17）
- **设计文档**：`docs/02-设计/详细设计/2026-07-19-UX-F-encoding-fix-design.md`
- **验证**：
  - 手工插入被 latin1 污染的中文 remark → 迁移脚本执行后恢复正常显示
  - 迁移脚本二次执行无异常无数据变化（幂等）
  - 前端 `textSanitizer` 6 条 assertion 全通过
- **未纳入**：客户业务库表的中文注释乱码（本单元只修配置库，指南中已告知处理方法）

---
```

- [ ] **Step 2: 搬迁问题清单条目**

在 `docs/03-开发/问题清单.md`：
1. 在"已解决"章节顶部新增小节 `## 已解决（UX-F 批次 2026-07-XX）`
2. 从"2026-07-19 批次 → F 组"表格中提取 OPS-01 一行，粘到新小节，标"已解决"
3. 从"待解决 → F 组"中删除 OPS-01（若 F 组仅此一项，可整个 F 组小表格删除或加"（已全部解决）"注记）

- [ ] **Step 3: 提交**

```bash
git add "docs/03-开发/变更记录.md" "docs/03-开发/问题清单.md"
git commit -m "docs(UX-F): 追加 CHG-021 + 搬迁 OPS-01 到已解决"
```

---

## 全计划自审清单

- [x] **spec 覆盖率**：设计层（Task 3 数据 + Task 4 环境 + Task 1-2 表现）三管齐下全覆盖；spec § 7 实施顺序对齐
- [x] **无 TBD / TODO / 待定**：全部 Step 内容具体
- [x] **类型一致性**：`isGarbled` / `sanitize` 签名在 Task 1 定义、Task 2 使用一致
- [x] **文件路径精确**：所有 Files 用绝对路径或明确相对路径
- [x] **每 Task 独立可 review**：每个 Task 都有独立 commit + 可验证 deliverable
- [x] **零依赖其它 UX 组**：UX-F 与 UX-A/B/C/D/E 都无文件重叠，可完全并行

## 实施顺序说明

Task 1 → Task 2 依赖（前端兜底）；Task 3 → Task 4 无依赖但推荐先脚本后指南（脚本相对好验证）；Task 5 最后收尾。

**总计 5 个 Task**，串行执行约 1-1.5 人时。可与 UX-A 完全并行。
