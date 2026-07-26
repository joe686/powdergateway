---
name: pg-run
description: Use when the user or Claude needs to launch PowerGateway locally (backend 8080 + frontend 5173 + pg-testkit 8081/9999), restart after code changes, stop all services, or troubleshoot startup issues. Triggered by phrases like "启动 / 拉起 / 起服 / 跑起来 / 重启 / 停服 / 关掉 / 试试看" or when Claude needs to verify a manual UI/API change works. **Always uses the existing scripts/start.* / stop.* rather than reinventing.**
---

# pg-run

## 用途

PowerGateway 有三个服务需要协同启动（backend 8080 · frontend 5173 · pg-testkit 8081 + Mock 9999），加上 MySQL/Redis 前置依赖。`scripts/` 目录下已经有完整的三平台脚本（`start.bat` / `start.ps1` / `start.sh` + 对应 `stop`），自带**依赖自检 + 端口占用检查 + 服务自启 + PID/日志管理**。

**本 skill 的唯一职责**：拦截"启动 / 拉起 / 起服"类意图，让 Claude 走已有脚本，不要重新写 `mvn spring-boot:run` + `npm run dev` + `pg-testkit` 三条独立命令，也不要每次都重造检查逻辑。

## 触发条件

**必须调用**：
- 用户说"启动 / 拉起 / 起服 / 跑起来 / 起一下 / 起服务"
- 用户说"重启 / 重新启动"
- 用户说"停服 / 关掉 / 停一下 / 关闭所有服务"
- 用户说"服务起来没 / 端口通不通 / 服务状态"
- Claude 需要**手工验证 UI / API 改动**（改前端组件后要 F5 看效果、改后端接口后要 Swagger 调用）—— 走 `verify` skill 之前 or 之内先起服务
- Claude 需要**跑手工测试指南**（`docs/04-测试/v0.1.0-手工测试指南.md`）前的环境准备

**不触发**：
- 单元测试 `mvn test` / `npm run build` —— 这些不需要长驻服务，直接跑
- 编译检查 `mvn compile` —— 同上
- 用户明确说"不要启动，我自己起了" —— 尊重

## 快速决策

```
用户/我要 ...              → 走这条
────────────────────────────────────────────────
启动全套服务                  → Step 1
只重启某一个（backend/前端）  → Step 2
停服                        → Step 3
看服务状态 / 端口            → Step 4
诊断启动失败                  → Step 5
```

## Step 1 · 一键启动全套

**Windows PowerShell（默认）**：
```powershell
scripts\start.ps1
```

**Git Bash（用户 CLAUDE.md 声明的主 shell）**：
```bash
scripts/start.sh
```

**Windows cmd**：
```cmd
scripts\start.bat
```

**脚本会自动做的事**（无需 Claude 手工重复检查）：
- ✅ 检查 JDK / Maven / Node / npm 命令可用
- ✅ 自动 `net start MySQL80` / `net start Redis`（需管理员权限，10s 超时）
- ✅ 检查 3306 / 6379 端口监听
- ✅ 检查 `frontend/node_modules` 存在
- ✅ 检查 8080 / 5173 / 8081 / 9999 端口未被占用
- ✅ 后台启动三服务，PID 记录到 `logs/*.pid`，日志到 `logs/*.log`

**验证启动成功**（等 30~60s 后）：
```bash
curl -s http://localhost:8080/api/health         # backend
curl -s http://localhost:5173/                    # frontend（HTML）
curl -s http://localhost:8081/test/health         # pg-testkit
```

或读日志：
```bash
tail -f logs/backend.log         # Git Bash
Get-Content -Wait logs\backend.log   # PowerShell
```

## Step 2 · 只重启某一个服务

三服务分别独立。停单个后手工启单个：

```powershell
# 停某一个
$pid = Get-Content logs\backend.pid
Stop-Process -Id $pid -Force
Remove-Item logs\backend.pid

# 启某一个（背景）
Start-Process -FilePath $env:ComSpec `
  -ArgumentList '/c mvn spring-boot:run' `
  -WorkingDirectory .\backend `
  -RedirectStandardOutput logs\backend.log `
  -WindowStyle Hidden
```

**更简单**：直接 `scripts\stop.ps1 && scripts\start.ps1`，代价是全部重启（重启 backend 通常 20~40s，可以接受）。

## Step 3 · 停服

```powershell
scripts\stop.ps1
```
或
```bash
scripts/stop.sh
```

策略：先按 PID 终止整个进程树，再按端口兜底清理（防子进程残留）。

## Step 4 · 看服务状态 / 端口

```powershell
netstat -ano | Select-String -Pattern "(:8080|:5173|:8081|:9999|:3306|:6379).*LISTENING"
```

```bash
netstat -ano | grep -E "(:8080|:5173|:8081|:9999|:3306|:6379).*LISTENING"
```

## Step 5 · 启动失败诊断

| 症状 | 排查 |
|------|------|
| `[缺失] 未找到命令: mvn` | `PATH` 没含 Maven，装 Maven 或改 `.bashrc` |
| `[缺失] MySQL 端口 3306 未在监听` | 以管理员跑脚本，或手动 `net start MySQL80` |
| `[占用] backend 端口已被占用` | 先跑 `scripts/stop.ps1`；或 `netstat -ano \| findstr :8080` → `taskkill /PID <pid> /T /F` |
| `frontend\node_modules 不存在` | `cd frontend && npm install`（首次要 3~5 min） |
| Backend 起了 3 分钟还没 200 | 读 `logs/backend.log` 找 `Started PowergatewayApplication`；如果卡在 Redis/MySQL 连接，检查密码 `qwe12345` |
| PowerShell 说 "无法加载 .ps1" | `powershell -ExecutionPolicy Bypass -File scripts\start.ps1` |

## 与其他 skill / 内置命令的关系

| skill | 关系 |
|------|------|
| **内置 `run` skill** | 通用 launch，不知道 PowerGateway 有三服务。本 skill **优先**，因为项目 skill 覆盖内置 |
| **内置 `verify` skill** | 手工验证 UI/API 时会需要服务在线，本 skill 是它的前置 |
| **`pg-feedback-flow`** | Bug 修完 Step 4 后走 `verify`，`verify` 又走本 skill |

## 反面模式（不要做）

- ❌ **重造检查逻辑**：不要在会话里手写 `mvn spring-boot:run` + `npm run dev` + `cd pg-testkit && mvn spring-boot:run` 三条独立 background 命令
- ❌ **等启动完 sleep 5 秒**：脚本已经检查依赖，直接跑；如果需要等服务 ready，用 curl 轮询 `/api/health`，不要盲等
- ❌ **跳过 stop 就重启**：会遇 `[占用]` 报错。永远 stop → start
- ❌ **改端口**：CLAUDE.md 明确"端口固定不得变更"（8080 / 5173），有占用先杀进程

## 常见任务快查

```
【要跑手工测试】
1. scripts\start.ps1
2. 等 30~60s
3. curl http://localhost:8080/api/health   # 确认 ok
4. 打开 docs/04-测试/v0.1.0-手工测试指南.md，从 MT-01-04 开始

【改了 backend 代码要验证】
1. scripts\stop.ps1
2. mvn -pl backend test -Dtest=<改动相关的 Test>   # 先跑单测
3. scripts\start.ps1                                # 起全套
4. Swagger UI 或 Postman 调 API 验证

【改了前端组件要看效果】
1. 检查 frontend 服务是否在跑（Step 4）
2. 如果在跑，Vite HMR 会自动热更新，不用重启
3. 打开浏览器 http://localhost:5173，Ctrl+F5 硬刷新

【会话结束前】
1. scripts\stop.ps1  # 释放端口和资源
```

## 相关文件

- `scripts/start.ps1` / `start.bat` / `start.sh`
- `scripts/stop.ps1` / `stop.bat` / `stop.sh`
- `scripts/README.md`（脚本文档）
- `docs/04-测试/连接配置速查.md`（端口、账号、URL 汇总）
- `logs/*.log` / `logs/*.pid`
