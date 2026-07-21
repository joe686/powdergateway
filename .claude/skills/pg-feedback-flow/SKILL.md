---
name: pg-feedback-flow
description: Use when the user gives PowerGateway feedback, reports a bug, requests a new feature, or asks for a visual/UX adjustment. Triggered by phrases like "这个不好用" "有个问题" "反馈一下" "新需求" "能不能加个" "这里不对" or when a message describes symptoms/screenshots without asking a technical question. Enforces the fixed 6-step ingest → triage → plan → execute → archive → release workflow so user feedback is always captured in `docs/06-项目管理/反馈簿.md` with a stable FB-XXX identifier.
---

# pg-feedback-flow

## 用途

PowerGateway 项目单人开发模式下，用户反馈的**固定流转流程**。避免"这条记到哪、优先级怎么定、要不要写 CHG"每次重问，所有反馈按 6 步走。

**核心不变量**：
- 反馈簿唯一入口：`docs/06-项目管理/反馈簿.md`
- FB-XXX 编号全局唯一，一 FB 一次流转
- 状态回写到反馈簿，不散落在别处

## 触发条件

- 用户说"反馈"、"有个问题"、"这个不好用"、"能不能加"、"新需求"、"不对"、"报错了"、"卡住了"
- 用户贴截图 / 描述现象 / 说使用场景问题
- 用户已完成一次交付后再次提出改动诉求
- 不触发：单纯技术咨询（"这段代码怎么写"）、纯操作命令（"跑一下测试"）

## 6 步流程（固定 recipe）

### Step 1 · Ingest 登记

读 `docs/06-项目管理/反馈簿.md` 的 [活跃反馈区](../../../docs/06-项目管理/反馈簿.md#三活跃反馈区)，找到"**下一个可用编号**"。追加新条目**到活跃区顶部**，格式：

```markdown
### FB-XXX <一句话现象>

- **时间**：YYYY-MM-DD（用户消息日期）
- **反馈原文**：<用户消息完整原文或紧密概括>
- **分类**：（Step 2 填）
- **优先级**：（Step 2 填）
- **目标版本**：（Step 2 填）
- **状态**：📥 待处理
- **处理链路**：（Step 3~5 逐步补）
```

**更新[顶部 "下一个可用编号"](../../../docs/06-项目管理/反馈簿.md#三活跃反馈区)** = FB-XXX+1。

### Step 2 · Triage 分类

| 分类 | 判定 |
|------|------|
| **Bug** | 已交付功能不符合预期 / 报错 / 数据错误 |
| **视觉调整** | 布局 / 颜色 / 对齐 / 文案 / IA 微调 |
| **新需求** | 需要新增未曾交付的功能点或大范围重构 |

| 优先级 | 判定 |
|---|---|
| **P0** | 阻塞主流程或阻塞发布 |
| **P1** | 影响主流程但有 workaround |
| **P2** | 视觉 / 交互 / 文档 / 打磨 |

**目标版本**参考 [路线图.md](../../../docs/06-项目管理/路线图.md)：
- Bug 一般 → v0.1.x 补丁通道
- 新需求 → 匹配路线图对应版本，或提到 CR 待评审

回写 Step 1 条目的 3 个字段，状态改 `🔍 已受理规划中`。

### Step 3 · Plan 规划

按分类走不同路径：

- **小 Bug / 单点视觉调整**：直接改，跳到 Step 4
- **大 Bug / 涉及多文件 / 新功能**：
  - 在 `docs/03-开发/任务计划/YYYY-MM-DD-FB-XXX.md` 写 TDD 分解（Task 1/2/3... 每 Task 一个测试点 + 影响文件 + 验收条件）
  - 反馈条目"处理链路"栏加 `→ 见任务计划/YYYY-MM-DD-FB-XXX.md`
- **新需求（超出当前版本范围）**：
  - 到 [`待办与缺陷池.md § 三`](../../../docs/06-项目管理/待办与缺陷池.md#三变更申请模板) 落 CR-XXX 待评审条目
  - 反馈条目状态改 `💤 已延后 → vX.Y.Z` 并加"处理链路：CR-XXX"
  - **不实施**，返回等待用户确认

### Step 4 · Execute 实施

强制遵守项目根 `CLAUDE.md` 定义的 TDD 规范：Red → Green → Refactor。

- 后端：`mvn test` 全绿（必须包含新加的用例）
- 前端：`npm run build` 通过 + 相关 vitest 通过
- 一 bug 一 commit（用户偏好，见 memory `feedback_commit_style.md`）
- commit 消息格式：`fix(单元): FB-XXX <一句话现象>` 或 `feat(单元): FB-XXX ...`

反馈条目状态：`🔧 开发中 → 🧪 测试中`。

### Step 5 · Archive 归档

判断是否属于**范围变更**（对已交付单元的行为增删）：

- **是** → 按项目根 `CLAUDE.md` 的变更规约三步走：
  1. `docs/03-开发/变更记录.md` 新增 CHG-XXX
  2. `docs/01-需求/需求拆分与最小实现方案.md` 更新对应单元描述
  3. `docs/03-开发/开发计划.md` 更新对应行
- **否** → 无需 CHG（纯 Bug 修复且不改行为）

反馈条目"处理链路"追加：`落地 → CHG-XXX` 或 `落地 → commit <SHA>`，状态改 `✅ 已交付`。

### Step 6 · Release 发版

**不是每次都触发**，累计到打新 tag 时统一做：

- `git tag -a vX.Y.Z -m "..."` + `git push origin master vX.Y.Z`
- [`路线图.md`](../../../docs/06-项目管理/路线图.md) 已发布表追加新版本
- 反馈簿把该批次已 ✅ / 🚫 / 💤 条目归档到 [`历史归档`](../../../docs/06-项目管理/反馈簿.md#四历史归档) 段的新批次

## 决策速查

```
用户发消息
  ├─ 是反馈类？→ Step 1 登记
  │    └─ Step 2 分类
  │         ├─ Bug 小 → Step 4 直接改
  │         ├─ Bug 大 / 新功能且在版本内 → Step 3 写任务计划 → Step 4
  │         └─ 新需求超版本 → Step 3 落 CR → 暂停，报告用户
  │    └─ Step 4 编码 + 测试全绿
  │    └─ Step 5 归档（判是否 CHG）
  │    └─ Step 6（累计发版时统一做）
  └─ 不是反馈类 → 不走本 skill
```

## 相关文件（速查路径）

| 用途 | 路径 |
|------|------|
| 反馈簿 | `docs/06-项目管理/反馈簿.md` |
| CR 池 | `docs/06-项目管理/待办与缺陷池.md` |
| 路线图 | `docs/06-项目管理/路线图.md` |
| 变更记录 | `docs/03-开发/变更记录.md` |
| 任务计划目录 | `docs/03-开发/任务计划/` |
| TDD 规范 | `docs/04-测试/TDD规范.md` |

## 常见判断难点

| 场景 | 处理 |
|------|------|
| 用户一次提多个问题（如 111.txt 21 条） | 每条独立 FB-XXX，共享同一时间戳 |
| 分类边界模糊（比如"这个报错但也想顺便优化下 UI"） | 拆成两条 FB，一 Bug 一 视觉调整 |
| 反馈的其实是**已延后**的 CR | 不新开 FB，链接到既有 CR-XXX，状态直接 `💤 已延后 → vX.Y.Z` |
| 用户否决之前的规划 | 反馈条目状态改 `🚫 已拒绝`，下方写"拒绝原因：xxx"，**不删条目** |
| 一次发版涉及多 FB | Step 6 集中处理，逐条把状态归档到反馈簿历史区 |

## 反面模式（不要做）

- ❌ 不登记直接改代码 —— 用户看不到状态，一周后忘了这事怎么处理的
- ❌ 一 FB 拆成多个编号 —— 破坏"一 FB 一次流转"的可追溯性
- ❌ 状态不回写 —— 反馈簿变成 write-only 日志，失去中枢价值
- ❌ 归档时把老 FB 删掉 —— 项目决策的历史证据丢失
- ❌ 大改不写任务计划直接干 —— 违反 TDD + 无法审计
- ❌ CHG 该写不写 —— 违反项目根 CLAUDE.md 的强制规约
