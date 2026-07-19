# 2026-07-19 全站体验重塑与功能补齐 · 总览设计

- **需求来源**：仓库根 `111.txt`（用户 2026-07-19 反馈的 21 项问题清单）
- **归档位置**：`docs/03-开发/问题清单.md`（编号 UI-01 ~ OPS-01 共 20 项）
- **开发单元**：`docs/03-开发/开发计划.md` 阶段六（UX-A ~ UX-F 共 6 个可并行单元）
- **视觉参考**：`mockups/v3-combined-switchable/index.html`（用户已确认）

## 目标

在不推翻已交付 28 个单元的前提下，通过 6 个正交工作组一次性提升 PowerGateway 全站体验一致性与功能完备度，成为可以对外演示的 v1.0。

## 6 个工作组关系

```
┌─────────────────────────────────────────────────────────┐
│  第 1 波（可立刻并行开工）                              │
│  ┌────────────────────┐  ┌──────────────────────────┐  │
│  │ UX-A 视觉重塑      │  │ UX-F 中文乱码修复        │  │
│  │  · tokens.css      │  │  · SQL 迁移脚本          │  │
│  │  · 主题切换 4 模式 │  │  · MySQL 字符集指南      │  │
│  │  · 双主题存储      │  │  · 前端渲染兜底          │  │
│  └──────────┬─────────┘  └──────────────────────────┘  │
│             │ tokens.css 定稿                          │
├─────────────▼───────────────────────────────────────────┤
│  第 2 波（依赖 UX-A 视觉 token）                        │
│  ┌────────────────┐ ┌──────────────┐ ┌──────────────┐  │
│  │ UX-B 信息架构  │ │ UX-C 字段    │ │ UX-E 可视化  │  │
│  │  · 菜单重排    │ │  映射/加工/  │ │  接口扩展    │  │
│  │  · TopBar 跳转 │ │  公式补齐    │ │  · 6 项      │  │
│  └───────┬────────┘ └──────────────┘ └──────────────┘  │
│          │ 菜单分组落定                                │
├──────────▼──────────────────────────────────────────────┤
│  第 3 波（依赖 UX-B 信息架构）                          │
│  ┌───────────────────────────────────────────────┐     │
│  │ UX-D 接口转换向导                             │     │
│  │  · 抽公共 WizardShell.vue                     │     │
│  │  · 新增 TransformInterfaceSteps.vue（7 步）   │     │
│  └───────────────────────────────────────────────┘     │
└─────────────────────────────────────────────────────────┘
```

## 每组独立文档

| 组 | 详细设计 | 任务计划 |
|----|---------|---------|
| UX-A · 视觉重塑 + 双主题 | `2026-07-19-UX-A-visual-refresh-design.md` | `docs/03-开发/任务计划/2026-07-19-UX-A-visual-refresh.md` |
| UX-B · 信息架构 | `2026-07-19-UX-B-nav-restructure-design.md` | `docs/03-开发/任务计划/2026-07-19-UX-B-nav-restructure.md` |
| UX-C · 字段映射/加工/公式 | `2026-07-19-UX-C-field-mapping-formula-design.md` | `docs/03-开发/任务计划/2026-07-19-UX-C-field-mapping-formula.md` |
| UX-D · 接口转换向导 | `2026-07-19-UX-D-transform-wizard-design.md` | `docs/03-开发/任务计划/2026-07-19-UX-D-transform-wizard.md` |
| UX-E · 可视化接口扩展 | `2026-07-19-UX-E-interface-extensions-design.md` | `docs/03-开发/任务计划/2026-07-19-UX-E-interface-extensions.md` |
| UX-F · 编码修复 | `2026-07-19-UX-F-encoding-fix-design.md` | `docs/03-开发/任务计划/2026-07-19-UX-F-encoding-fix.md` |

## Subagent 并行执行边界

第 2 波三个组能安全并行的关键：三组文件基本互不重叠。

| 组 | 关键触及范围 | 冲突预防 |
|----|-------------|---------|
| UX-B | `frontend/src/components/layout/SideMenu.vue`、`TopBar.vue`、`router/index.js`（菜单顺序） | 只调排序 + 增分组组件 |
| UX-C | `frontend/src/views/convert/FieldMapping.vue`、`FieldProcess.vue`、新增 `views/convert/FieldFormula.vue`；backend 新增 `FieldFormula*.java` | 全新文件为主，改动已有文件仅 bug 修复 |
| UX-E | 新增 `views/interface/InterfaceDocument.vue`、`InterfaceImport.vue`；backend 新增 6 个类；改 `ExecController` 增 Accept header 支持 | 新增文件 + `ExecController` 单点扩展 |

`router/index.js` 是唯一多组会碰的文件，用 append-only 策略：UX-B/C/D/E 各自新增独立 route 块，不修改既有 route。合并时用 `git merge --strategy-option=union`。

## 通用交付要求

- 每单元 TDD Red-Green-Refactor，`@ActiveProfiles("test")` 强制加
- 每单元完成时更新 `docs/03-开发/变更记录.md` 追加 `CHG-015 ~ CHG-020` 对应条目
- 每单元完成后回填 `问题清单.md` 中对应编号从「待解决」移动到「已解决」
- 每单元合并前跑一次 pg-testkit 冒烟（登录 → 主题切 → 字段公式 → 转换向导 → 可视化接口 exec）

## 未纳入本批次

- 111.txt #9、#10、#11（用户明确"功能上没什么疑问"）
- OPT-16（报文格式增强，路线图已存在，UX-E 借用其 XML/CSV 基础但不重复投入）
- 双主题的图表 palette 二次调优（Dashboard 图表暗色下部分色需 QA 二次微调，作为 UX-A 内部检查项）

## 待用户 review

写完 6 份分组 design doc 后，请逐一 review：

1. 每份 spec 的"验收标准"是否符合你的期望
2. 是否有遗漏的功能点（对照 111.txt 原文）
3. 并行策略与 subagent 边界是否可接受

review 通过后，转 `superpowers:writing-plans` skill 生成对应 implementation plan，然后用 `subagent-driven-development` skill 分派 subagent 并行执行。
