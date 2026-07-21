# 制品发布

本章节存放 PowerGateway 各交付形态制品的产品说明、部署手册、下载指引。

| 文档 | 用途 |
|------|------|
| [产品说明书.md](./产品说明书.md) | 各 SKU 定位/规格/资源要求/功能裁剪矩阵/中间件与数据库支持（含常见问题 + 变更历史 + 版本升级） |

**打包脚本源码**：见 [`scripts/build/`](../../scripts/build/)（`build-portable.sh` / `build-standard.sh` / `jlink-jre.sh` + `package-templates/`）
**发布方式（CHG-027 起）**：单人开发模式，不使用 GitHub Actions；本地跑打包脚本 → 本地跑 [`scripts/ci/verify-artifacts.sh`](../../scripts/ci/verify-artifacts.sh) 冒烟 → `git tag v*.*.*` 标记版本（首版 v0.1.0）→ 本地分发 zip
**打包便携版部署使用手册**：见便携版 zip 解压后的 `README.txt`
**标准版部署手册**：见标准版 zip 解压后的 `README.md`（同源自 `scripts/build/package-templates/README-standard.md`）

## 后续规划

以下拆解到独立文档，v1.1 补齐（一句话说明 + 引导到当前"产品说明书.md"对应章节）：
- 快速上手（5 分钟）便携版 → 目前见 [产品说明书.md §2 便携版详细规格](./产品说明书.md#2-便携版详细规格portable) + §11 常见问题
- 生产部署手册（标准版）→ 目前见 [产品说明书.md §3 标准版详细规格](./产品说明书.md#3-标准版详细规格standard) + zip 内 README.md
- 升级迁移指南 → 目前见 [产品说明书.md §10 版本升级与迁移](./产品说明书.md#10-版本升级与迁移)
