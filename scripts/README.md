# PowerGateway 一键启停脚本

> 一键拉起 backend (8080) + frontend (5173) + pg-testkit (8081, mock 9999) 三个服务，日志后台落盘，配套停服脚本。

## 文件清单

| 平台 / Shell | 启动 | 停止 |
|--------------|------|------|
| Windows cmd  | `scripts\start.bat` | `scripts\stop.bat` |
| PowerShell   | `scripts\start.ps1` | `scripts\stop.ps1` |
| Git Bash     | `scripts/start.sh`  | `scripts/stop.sh`  |

三套脚本行为一致，按自己习惯选一套即可。

## 启动前置条件

- 已安装 JDK 1.8+、Maven、Node.js（含 npm）
- MySQL 与 Redis 已作为 Windows 服务安装（默认服务名：`MySQL80`、`Redis`）
  - 若端口未监听，脚本会自动 `net start <服务名>` 尝试拉起（最多等 10s）
  - 此步骤需要管理员权限；如果非管理员且服务确实未启动，会提示手工启动
- 至少执行过一次 `cd frontend && npm install`（脚本会检查 `frontend/node_modules`）

启动脚本会逐项检查上述条件并在失败时给出具体提示。

## 用法

### Windows cmd

```cmd
scripts\start.bat
scripts\stop.bat
```

### PowerShell

```powershell
# 如系统未允许脚本执行：
# powershell -ExecutionPolicy Bypass -File scripts\start.ps1
scripts\start.ps1
scripts\stop.ps1
```

### Git Bash

```bash
scripts/start.sh
scripts/stop.sh
```

## 日志与 PID

- 日志：`logs/backend.log`、`logs/frontend.log`、`logs/pg-testkit.log`
- PID：`logs/backend.pid`、`logs/frontend.pid`、`logs/pg-testkit.pid`（`.bat` 版仅记录端口）
- 停服策略：先按 PID 终止整个进程树，再按端口兜底清理（避免子进程残留）

跟踪某个服务的日志：

```bash
# Git Bash
tail -f logs/backend.log
```

```powershell
# PowerShell
Get-Content -Wait logs\backend.log
```

## 常见问题

- **端口被占用**：脚本会提示 `[占用]`，先执行对应 stop 脚本清理，或手动 `netstat -ano | findstr :8080` 找到 PID 后 `taskkill /PID <pid> /T /F`。
- **`mvn`/`npm` 找不到**：确认其加入了 `PATH`；脚本通过 `where`/`Get-Command`/`command -v` 探测。
- **PowerShell 拒绝执行 .ps1**：用 `powershell -ExecutionPolicy Bypass -File scripts\start.ps1` 临时绕过。
- **首次启动 backend 慢**：Maven 拉依赖耗时，正常现象；后续启动会快很多。
