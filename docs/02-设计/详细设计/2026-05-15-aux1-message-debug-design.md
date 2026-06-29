---
title: AUX-1 报文调试工具设计
date: 2026-05-15
unit: AUX-1
---

# AUX-1 报文调试工具设计

## 概述

报文调试工具是一个纯前端页面，供开发/测试人员手动输入报文或参数，快速验证格式转换配置和接口调用效果，无需新增后端接口。

---

## 架构

### 文件变更

| 类型 | 路径 | 说明 |
|------|------|------|
| 新增 | `frontend/src/views/tools/MessageDebug.vue` | 调试工具主页面 |
| 修改 | `frontend/src/router/index.js` | `tools/debug` 路由从 `PlaceholderView` 替换为 `MessageDebug` |
| 修改 | `frontend/src/api/interface.js` | 补充 `execInterface(id, body)` 函数（`POST /api/exec/{id}`） |
| 依赖 | `highlight.js` | npm 安装，用于结果区语法高亮 |

### 复用的后端接口

| 接口 | 用途 |
|------|------|
| `GET /api/template/list` | 格式转换模式：加载可选模板列表 |
| `POST /api/convert` | 格式转换模式：执行转换 |
| `GET /api/interface/list?size=500` | 接口调用模式：加载接口列表，前端过滤 `status=published` |
| `POST /api/exec/{id}` | 接口调用模式：执行接口调用 |

---

## 页面布局（方案 A：左右分区）

```
┌─────────────────────────────────────────────────────────┐
│  [● 格式转换调试]  [ 接口调用调试]                        │  ← el-radio-group
├─────────────────────────────────────────────────────────┤
│  选择模板：[下拉选择...              ▼]  [执行]           │  ← 配置栏（随模式变化）
├──────────────────────────┬──────────────────────────────┤
│  源报文                   │  转换结果                     │
│                           │                              │
│  el-input (textarea)      │  <pre><code> 语法高亮块       │
│                           │                              │
│                           │  状态栏：耗时 12ms            │
└──────────────────────────┴──────────────────────────────┘
```

- 左右各占 50% 宽度，高度固定（如 `calc(100vh - 200px)`）使文本框可滚动
- 「执行」按钮始终在配置栏右侧

---

## 两种调试模式

### 格式转换调试

- 配置栏：`el-select` 加载 `GET /api/template/list`，显示模板名
- 左区 placeholder：`输入源报文（JSON / XML / CSV 均可）`
- 点执行：`POST /api/convert { templateId, message: 左区内容 }`
- 右区：展示 `data.result`，状态栏显示 `data.costMs`

### 接口调用调试

- 配置栏：`el-select` 加载 `GET /api/interface/list?size=500`，前端过滤 `status === 'published'`，显示接口名和路径
- 左区 placeholder：`输入请求参数（JSON 格式，如 {"status": 1}）`
- 点执行：将左区 JSON 解析后作为请求体，`POST /api/exec/{id}`
- 右区：展示接口返回的 `data` 字段（JSON.stringify 格式化），状态栏显示耗时

---

## 结果区语法高亮

使用 `highlight.js` 的 `hljs.highlightAuto(result)` 自动识别 JSON / XML 语法并着色：

```js
import hljs from 'highlight.js'
import 'highlight.js/styles/github-dark.css'

const highlighted = hljs.highlightAuto(resultText).value
// 写入 <pre><code v-html="highlighted"></code></pre>
```

无法识别时降级为纯文本，不报错。

---

## 错误处理

| 场景 | 处理方式 |
|------|------|
| 未选模板/接口就点执行 | `ElMessage.warning('请先选择模板/接口')` |
| 左区报文/参数为空 | `ElMessage.warning('请输入报文或参数')` |
| 接口调用模式下左区非合法 JSON | `ElMessage.warning('参数格式错误，请输入合法 JSON')` |
| 后端返回错误 | `request.js` 拦截器已统一处理；右区同时显示红色错误信息 |
| 接口已禁用（后端返 403） | 右区显示"接口已禁用"，状态栏文字标红 |
| highlight.js 识别失败 | 降级纯文本展示 |

---

## 验收标准

1. 格式转换模式：选已有模板 → 输入 JSON 报文 → 点执行 → 右区显示正确转换结果（含语法高亮）
2. 接口调用模式：选已发布接口 → 输入合法参数 JSON → 点执行 → 右区显示接口返回数据
3. 空报文/未选模板点执行 → warning 提示，不发请求
4. JSON 结果和 XML 结果均有颜色高亮
5. 路由 `/tools/debug` 正常跳转，侧边栏"报文调试"菜单可见

---

## 不含

- 请求历史记录（不持久化调试记录）
- 参数 KV 表格（左区直接输入 JSON 作为参数）
- 新增任何后端接口
