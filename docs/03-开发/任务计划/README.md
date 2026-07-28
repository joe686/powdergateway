# 任务计划

每份文档对应一个交付单元的实现拆分，与 [../../02-设计/详细设计/](../../02-设计/详细设计/) 同日期文件配对。

| 日期 | 文件 | 对应单元 |
|------|------|---------|
| 2026-03-27 | [header-config.md](./2026-03-27-header-config.md) | M1-7 端口分发报文头配置 |
| 2026-03-28 | [m1-bugfix.md](./2026-03-28-m1-bugfix.md) | M1 系列 bug 修复 |
| 2026-03-28 | [M2-1-db-connection.md](./2026-03-28-M2-1-db-connection.md) | M2-1 数据库连接管理 |
| 2026-05-04 | [m2-5-update-config.md](./2026-05-04-m2-5-update-config.md) | M2-5 修改接口配置 |
| 2026-05-05 | [m2-6-delete-config.md](./2026-05-05-m2-6-delete-config.md) | M2-6 删除接口配置 |
| 2026-05-05 | [m2-7-interface-publish.md](./2026-05-05-m2-7-interface-publish.md) | M2-7 接口发布与统一执行入口 |
| 2026-05-06 | [m210-cache.md](./2026-05-06-m210-cache.md) | M2-10 双层缓存 |
| 2026-05-12 | [m28-shard-config.md](./2026-05-12-m28-shard-config.md) | M2-8 分库分表 |
| 2026-05-13 | [sys1-log-management.md](./2026-05-13-sys1-log-management.md) | SYS-1 日志管理 |
| 2026-05-13 | [sys3-user-permission.md](./2026-05-13-sys3-user-permission.md) | SYS-3 用户与权限 |
| 2026-05-14 | [sys2-perf-stats.md](./2026-05-14-sys2-perf-stats.md) | SYS-2 性能统计 |
| 2026-05-14 | [sys4-system-config.md](./2026-05-14-sys4-system-config.md) | SYS-4 系统配置 |
| 2026-05-14 | [sys5-interface-wizard.md](./2026-05-14-sys5-interface-wizard.md) | SYS-5 九步接口配置向导 |
| 2026-05-15 | [aux1-message-debug.md](./2026-05-15-aux1-message-debug.md) | AUX-1 报文调试工具 |
| 2026-05-20 | [AUX-2.md](./2026-05-20-AUX-2.md) | AUX-2 首页概览 |
| 2026-07-22 | [2026-07-22-FN-11-import-export.md](./2026-07-22-FN-11-import-export.md) | FN-11 扩展 · 配置导入导出（Excel/Markdown/菜单合并） |
| 2026-07-22 | [2026-07-22-REG-1-registry.md](./2026-07-22-REG-1-registry.md) | REG-1 · 注册中心集成（Nacos + Eureka） |
| 2026-07-22 | [2026-07-22-FN-11-import-export.md](./2026-07-22-FN-11-import-export.md) | FN-11 · 配置导入导出扩展（Excel/Markdown/菜单合并） |
| 2026-07-22 | [2026-07-22-FB-037.md](./2026-07-22-FB-037.md) | FB-037 · 未登录跳转（v0.1.1 patch） |
| 2026-07-22 | [2026-07-22-FB-038.md](./2026-07-22-FB-038.md) | FB-038 · 登录页毛玻璃（v0.1.1 patch） |
| 2026-07-26 | [2026-07-26-FN-12-backend.md](./2026-07-26-FN-12-backend.md) | FN-12 · 字典映射后端（v0.2.0 ①） |
| 2026-07-26 | [2026-07-26-v0.5.0-FN-BIZ-implementation.md](./2026-07-26-v0.5.0-FN-BIZ-implementation.md) | v0.5.0 · FN-BIZ 业务菜单生成（未开工 · 设计定稿） |
| 2026-07-26 | [2026-07-26-v0.5.0-SYS-3-upgrade-implementation.md](./2026-07-26-v0.5.0-SYS-3-upgrade-implementation.md) | v0.5.0 · SYS-3 权限升级（未开工 · 设计定稿） |
| 2026-07-27 | [2026-07-27-FN-12-processor.md](./2026-07-27-FN-12-processor.md) | FN-12 · M1-3 Processor 集成（v0.2.0 ②） |
| 2026-07-27 | [2026-07-27-FN-12-frontend.md](./2026-07-27-FN-12-frontend.md) | FN-12 · 前端管理页 + 向导集成（v0.2.0 ③） |
| 2026-07-27 | [2026-07-27-FN-12-x-FN-09.md](./2026-07-27-FN-12-x-FN-09.md) | FN-12 · FN-09 联动 · Excel xlsx 多 sheet（v0.2.0 ④） |

## 归档

已交付单元的任务计划归档到 [`归档/`](./归档/) 目录。归档时机 = **版本 tag 前** · 见 [归档区 README](./归档/README.md) 触发条件。

**归档规划**（下次 v0.3.0 tag 前统一执行）：

- v0.1.0 相关（22 份 M1/M2/SYS/AUX/UX/FN-11/REG-1 系列）→ `归档/v0.1.0/`
- v0.1.1 相关（2 份 FB-037/038）→ `归档/v0.1.1/`
- v0.2.0 相关（4 份 FN-12 全链路）→ `归档/v0.2.0/`

**待开工的任务计划**（未写）：

- v0.3.0 SOCK-1~4：`2026-07-28-FB-052-part1-lcpt-host-outbound.md`（CR-005 SOCK-5-D 补 + 分帧默认调整 拍板后写）
- v0.3.2 SOCK-5-A/B/C/D：`2026-07-28-FB-052-part2-lcpt-bank-inbound.md`（同上）
