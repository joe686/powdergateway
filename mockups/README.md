# PowerGateway 视觉重塑样板（A 组样板）

对应问题清单 111.txt 第 1、3、4、16、18 项 —— 全站毛玻璃圆角风格化。

## 怎么看

浏览器双击打开各目录下的 `index.html`（不需要启后端 / 前端，纯静态）。建议宽度 ≥ 1440px。

## 三个 mockup

| 方向 | 目录 | 一句话概括 | 参考感觉 |
|---|---|---|---|
| **V1 · Aurora 亮色极光** | `v1-light-aurora/` | 白底 + 淡紫蓝渐变背景 blob + 半透明白卡片 | Vercel Cloud / Linear 亮版 / 现代 SaaS |
| **V2 · Nebula 暗色星云** | `v2-dark-nebula/` | 深空蓝底 + 霓虹青紫强调 + 玻璃卡片高光 | Vercel / Railway / Fly.io / 现代 DevTools |
| **V3 · 双主题可切换 ★** | `v3-combined-switchable/` | 融合 V1 + V2，顶栏一键切主题，齿轮开抽屉设置模式（手动 / 定时 / 跟随系统 / 日出日落） | 就是最终交付形态 |

**推荐直接看 V3**——可切亮暗、可以点齿轮弹出主题设置抽屉体会 4 种模式 UI。

两个 mockup 都在展示同一屏 —— `Dashboard 系统概览`，用于挑选风格基线。挑定方向后：

- 亮色/暗色底色 · backdrop-filter 模糊参数 · 圆角规格 · 阴影强度 · 主色板 —— 一并锁定成一份 `frontend/src/styles/tokens.css`
- SideMenu / TopBar / Card / Table / Button / Input / Dialog 等所有组件按 token 覆盖 Element Plus 默认变量
- 其他 22 个业务页面自动跟上（改的是全局 CSS 变量，不是逐页改）

## 已在 mockup 中修的问题

- 问题 4：5 张 KPI 卡片改用 flex 均分 + gap，最右卡片必然与右侧其它模块对齐（原代码用 `:span="5"` 导致 5×5=25 溢出）
- 问题 3：SideMenu active 高亮改成柔和光带 + 侧栏折叠动画
- 问题 1：卡片/输入/按钮/弹窗全部圆角 12–16px + backdrop-filter blur

## 未在 mockup 中展示（后续 spec 细化）

- 问题 2 右上角个人信息跳"用户权限管理"（NAV 组）
- 问题 16 可视化接口菜单美化（视觉 token 定完后自动跟上）
- 问题 18 报文调试美化（同上）
