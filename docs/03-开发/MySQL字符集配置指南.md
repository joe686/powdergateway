# MySQL 字符集配置指南（Windows）

## 症状识别

在 PG "系统管理 → 系统配置"页面中，若"参数说明"列显示 `????` 或"锟斤拷"，或前端已挂 `sanitize` 后显示黄色斜体"（编码异常...）"，说明服务端 MySQL 字符集配置错误。

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

浏览器访问 `http://localhost:5173/system/config`：**参数说明列应恢复中文显示，不再有黄色"编码异常"占位。**

## 常见问题

**Q：客户业务库表的中文注释也乱怎么办？**
A：本指南仅覆盖 PG 自身的配置库。客户业务表若也需修复，可按同样思路建自定义 SQL 脚本（把 `migration-encoding-fix.sql` 中的 `sys_config` 替换为业务表名即可）。

**Q：Linux MySQL 也适用吗？**
A：适用。配置位置为 `/etc/mysql/my.cnf` 或 `/etc/my.cnf`；重启命令 `systemctl restart mysqld`。

**Q：修改后新的中文数据还是乱码？**
A：检查 JDBC URL 是否含 `useUnicode=true&characterEncoding=UTF-8`。项目 `TableMetaService.normalizeUrlForMetaQuery` 已在元数据查询时注入这些参数（CHG-012），业务连接可在数据源配置里检查。
