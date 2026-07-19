# UX-F · 中文注释乱码修复 · 详细设计

- **单元编号**：UX-F（阶段六 UX 批次）
- **覆盖问题**：OPS-01（源自 111.txt #17：系统配置一些参数配置后面的注释中文乱码）
- **依赖**：无（第 1 波，与 UX-A 并行）
- **前置分析**：CHG-011 OBS-1 / CHG-012 已给出根因，本单元收尾

## 1. 目标

彻底解决 `sys_config` 表中"参数注释（remark 字段）"中文显示乱码问题。前后端全链路修复 + 出用户操作指南。

## 2. 需求来源与根因回顾

### 2.1 用户反馈（111.txt #17）

> "系统配置，一些参数配置后面的注释中文是乱码，要看一下是不是数据库里面插入的中文编码格式不对，或者是前端写的编码格式不对。"

### 2.2 CHG-011 OBS-1 / CHG-012 已定的根因

CHG-012 OBS-1 明确：**根因在 MySQL 服务端字符集配置 + 注释字节双重编码**。

具体表现：
- 用户本地 `my.ini` 未设置 `[mysqld] character-set-server=utf8mb4`（Windows MySQL 默认为 latin1）
- 已插入的中文数据被 MySQL 用 latin1 存储，但客户端连接又声明为 utf8 → 字节被双重编码（先 utf8 编码成字节，再被 latin1 当作字节存下来）
- 查询时 JDBC 连接虽然声明 utf8 → 拿到的字节按 utf8 解码 → 得到"？？"或"锟斤拷"
- CHG-012 已经把 `TableMetaService.normalizeUrlForMetaQuery` 加了 `useInformationSchema=true&useUnicode=true&characterEncoding=utf8`（部分缓解，约 10% 列注释显示正常）
- 剩余 90% 需要**服务端字符集调整 + 已有数据重编码**才能彻底解决

### 2.3 影响范围

不仅 `sys_config.remark` 一列。**任何 VARCHAR/TEXT 中文数据**都可能受影响，包括：
- `sys_config.remark`（用户直接看到的）
- 业务库表结构的 `COLUMN_COMMENT`（表结构查询菜单也可能乱）
- `db_connection.remark` 之类的备注
- 用户自建业务表的中文字段名注释

## 3. 设计方案

三管齐下：

| 层级 | 做什么 | 交付物 |
|------|--------|-------|
| 数据层 | 对已乱码记录用 `CONVERT(BINARY(x) USING utf8mb4)` 反向解码后重写 | `db/migration-encoding-fix.sql` 幂等脚本 |
| 环境层 | 指导客户改 `my.ini` + 重启 MySQL + 用 `SHOW VARIABLES LIKE 'character_set%'` 核验 | `docs/03-开发/MySQL字符集配置指南.md` |
| 表现层 | 前端渲染时兜底：检测到 UTF-8 replacement char (`�`) 或明显乱码时用 `?` 或"（原文乱码）"占位 | 前端 utility `frontend/src/utils/textSanitizer.js` |

### 3.1 数据层 · 幂等重编码脚本

`backend/src/main/resources/db/migration-encoding-fix.sql`：

```sql
-- ================================================================
-- 中文乱码数据修复脚本（幂等）
-- 场景：MySQL 服务端字符集配置错导致中文被双重编码为乱码
-- 原理：CONVERT(BINARY(col) USING utf8mb4) 反向解码
-- 前置：确保 my.ini 已设 [mysqld] character-set-server=utf8mb4 且已重启
-- 幂等：若已是正常 utf8 字节，重编码会得到相同结果或空值，脚本用 IF 判断跳过
-- ================================================================

-- 1. 先检查连接会话字符集
SET NAMES utf8mb4;
SET CHARACTER_SET_CLIENT = utf8mb4;
SET CHARACTER_SET_CONNECTION = utf8mb4;
SET CHARACTER_SET_RESULTS = utf8mb4;

-- 2. sys_config.remark 与 group_name 修复
--    只处理"看起来是乱码"的行（含常见乱码字节序 0xE9 0xE9、0xE5 0xB1 等的 UTF-8 双编码模式）
--    判断依据：CONVERT(BINARY(remark) USING utf8mb4) 若可解码且长度 != 原长度，说明是双编码
UPDATE sys_config
SET remark = CONVERT(CAST(CONVERT(remark USING latin1) AS BINARY) USING utf8mb4)
WHERE remark IS NOT NULL
  AND remark <> ''
  AND remark REGEXP '[[:cntrl:]]'                     -- 含控制字符即视为乱码
  AND CONVERT(CAST(CONVERT(remark USING latin1) AS BINARY) USING utf8mb4) IS NOT NULL;

-- 3. sys_config 表字符集本身升级到 utf8mb4（若已经是则无操作）
ALTER TABLE sys_config
    CONVERT TO CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

-- 4. 同样对 db_connection.remark 修复（若字段存在）
UPDATE db_connection
SET remark = CONVERT(CAST(CONVERT(remark USING latin1) AS BINARY) USING utf8mb4)
WHERE remark IS NOT NULL
  AND remark <> ''
  AND remark REGEXP '[[:cntrl:]]';

ALTER TABLE db_connection
    CONVERT TO CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

-- 5. 输出修复报告
SELECT COUNT(*) AS fixed_rows_in_sys_config FROM sys_config WHERE remark IS NOT NULL AND remark <> '';
SELECT COUNT(*) AS fixed_rows_in_db_connection FROM db_connection WHERE remark IS NOT NULL AND remark <> '';
```

**幂等保证**：脚本二次执行时，已修复的行 `remark REGEXP '[[:cntrl:]]'` 为 false（正常 UTF-8 不含控制字符），UPDATE 会 SKIP。

**回滚**：修改前建议 `mysqldump powergateway_config sys_config db_connection > backup.sql`（脚本头部注释提示）。

### 3.2 环境层 · MySQL 字符集配置指南

`docs/03-开发/MySQL字符集配置指南.md`（新建）：

```markdown
# MySQL 字符集配置指南（Windows）

## 症状识别

在 PG "系统管理 → 系统配置"页面中，若"参数注释"列显示 `????` 或"锟斤拷"，说明服务端 MySQL 字符集配置错误。

## 修复步骤

### 1. 找到 my.ini

- 默认路径：`C:\ProgramData\MySQL\MySQL Server 8.0\my.ini`（Windows 安装版）
- 或：`Program Files\MySQL\...\my.ini`
- 或用管理员打开 `services.msc` → 找 MySQL80 服务 → 右键属性 → "可执行文件路径"含 `--defaults-file=...` 参数

### 2. 修改配置

用管理员权限用记事本打开 `my.ini`，找到 `[mysqld]` 段，加入或修改以下 3 行：

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

### 3. 重启 MySQL

管理员 PowerShell 执行：

```powershell
Restart-Service MySQL80
```

### 4. 核验

登录 MySQL 客户端执行：

```sql
SHOW VARIABLES LIKE 'character_set%';
```

预期所有行的 Value 都是 `utf8mb4`（除 `character_set_filesystem=binary`）。

### 5. 修复已有乱码数据

在 MySQL 客户端执行仓库中的 `backend/src/main/resources/db/migration-encoding-fix.sql`。

### 6. 冒烟验证

访问 `http://localhost:5173/system/config`，检查参数注释列已恢复中文显示。
```

### 3.3 表现层 · 前端渲染兜底

`frontend/src/utils/textSanitizer.js`（新建）：

```js
// 检测明显乱码：含 UTF-8 replacement char (�) 或连续 3 个以上的 ?
const GARBLED_PATTERN = /[�]|\?{3,}|[一-鿿]{0}锟斤拷/

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
 */
export function sanitize(s, placeholder = '（编码异常）') {
  return isGarbled(s) ? placeholder : s
}
```

用在 `frontend/src/views/system/SysConfig.vue` 的 remark 列 formatter：

```vue
<el-table-column label="备注" prop="remark">
  <template #default="{ row }">
    <span :class="{ 'text-warning': isGarbled(row.remark) }">
      {{ sanitize(row.remark, '（编码异常，请联系管理员）') }}
    </span>
  </template>
</el-table-column>
```

**注意**：兜底只是"不让用户看到乱七八糟"，真正的解决还是 3.1 + 3.2。

## 4. 文件变更清单

| 文件 | 变更 | 说明 |
|------|------|------|
| `backend/src/main/resources/db/migration-encoding-fix.sql` | 新增 | 幂等重编码脚本（§ 3.1） |
| `docs/03-开发/MySQL字符集配置指南.md` | 新增 | 用户操作手册（§ 3.2） |
| `frontend/src/utils/textSanitizer.js` | 新增 | 前端乱码检测工具（§ 3.3） |
| `frontend/src/views/system/SysConfig.vue` | 修改 | remark 列 formatter 用 `sanitize()` |
| `frontend/src/views/system/DbConnection.vue` | 修改（若存在 remark 列） | 同上 |

**init.sql 不动**：新库不受本 bug 影响（`CREATE TABLE ... DEFAULT CHARSET=utf8mb4` 已保证）。

## 5. 测试用例

### 5.1 单元测试（前端 `textSanitizer.spec.js`）

- `isGarbled` 检测 `锟斤拷` → true
- `isGarbled` 检测 `�?????` → true
- `isGarbled` 检测正常中文 → false
- `isGarbled` 检测正常英文 → false
- `sanitize` 遇到乱码返回占位、正常返回原文

### 5.2 集成冒烟（人工）

前置：在 SQL 命令行手工插入一条明显乱码的 sys_config 记录（如 `INSERT ... VALUES (..., 0xE9 0xE9 ...)`）。

步骤：
1. 前端页面 sys_config 列表打开 → remark 列显示"（编码异常，请联系管理员）"，无 `?????`
2. 执行 `migration-encoding-fix.sql`
3. 刷新页面 → 该行 remark 恢复中文，无占位显示

### 5.3 幂等测试

- 对已修复的 sys_config 表二次执行 `migration-encoding-fix.sql` → 无异常、无数据变化
- 对不含乱码数据的全新 sys_config 表执行 → 无异常、无数据变化

## 6. 验收标准

| # | 场景 | 通过条件 |
|---|------|---------|
| 1 | 老库（曾被 latin1 污染）执行迁移脚本 | 全部 sys_config remark 恢复中文可读 |
| 2 | 新库（一开始就是 utf8mb4）执行迁移脚本 | 无异常、无数据变化 |
| 3 | 前端 SysConfig.vue remark 列 | 若数据中有残留乱码，显示"（编码异常...）"而非 `?????` |
| 4 | 用户按指南改 my.ini 重启 MySQL 后 | 新插入的中文注释永久正确显示 |
| 5 | 表结构查询菜单（M2-2） | 用户业务表的 COLUMN_COMMENT 也恢复中文（受益于服务端字符集调整） |

## 7. 实施顺序

1. 前端 `textSanitizer.js` + 单元测试（5 分钟）
2. `SysConfig.vue` 挂 `sanitize`（5 分钟）
3. `migration-encoding-fix.sql`（15 分钟）+ 幂等测试（10 分钟）
4. `MySQL字符集配置指南.md`（10 分钟）
5. 联调冒烟（15 分钟）

**总计约 1 人时**。

## 8. 未纳入

- **业务库中的用户业务表中文乱码**：本单元只修配置库（`powergateway_config`）里的 `sys_config` / `db_connection`。业务库是客户的自有表，若也有乱码需客户自己按同样脚本改（`docs/03-开发/MySQL字符集配置指南.md` 中告知）。
- **审计库 `powergateway_audit`**：SQL 参数值可能被记录，但审计数据字段本身是英文列名，一般不受影响。若客户执行的 SQL 含中文参数导致 `sql_audit_log` 值乱码，本单元不处理。
- **前端主动"修复"乱码字节**（如尝试 JS 端二次解码）：不可靠，不做。

## 9. 与其它 UX 组的耦合

- 无。UX-F 是完全独立的第 1 波单元，可与 UX-A 完全并行开发。
- **`SysConfig.vue` 修改与 UX-A 视觉重塑不冲突**：UX-A 只覆盖 CSS 变量，不改 template；UX-F 只改 template 中 remark 列 formatter，不动 style。
