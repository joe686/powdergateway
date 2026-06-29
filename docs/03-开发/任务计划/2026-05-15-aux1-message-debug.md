# AUX-1 报文调试工具 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 实现报文调试工具页面，支持格式转换调试和接口调用调试两种模式，复用现有后端接口，无需新增后端代码。

**Architecture:** 纯前端单文件组件 `MessageDebug.vue`，通过顶部 Radio 切换两种调试模式，左右分区展示输入和结果。格式转换模式调用 `/api/convert`，接口调用模式调用 `/api/exec/{id}`，结果区使用 highlight.js 语法高亮。

**Tech Stack:** Vue 3 Composition API、Element Plus、highlight.js、现有 api/template.js 和 api/interface.js

---

## 文件结构

| 操作 | 路径 | 说明 |
|------|------|------|
| CREATE | `frontend/src/views/tools/MessageDebug.vue` | 调试工具主页面（全部业务逻辑在此） |
| MODIFY | `frontend/src/api/interface.js` | 补充 `execInterface(id, body)` 函数 |
| MODIFY | `frontend/src/router/index.js` | `tools/debug` 路由替换为 `MessageDebug` 组件 |
| INSTALL | `highlight.js` | `npm install highlight.js` |

---

## Task 1：安装 highlight.js 并补充 execInterface API

**Files:**
- Modify: `frontend/src/api/interface.js`（末尾追加）

- [ ] **Step 1: 安装 highlight.js**

在 `frontend/` 目录下执行：
```bash
cd frontend
npm install highlight.js
```
预期输出：`added 1 package` 类似信息，无报错。

- [ ] **Step 2: 在 interface.js 末尾追加 execInterface**

打开 `frontend/src/api/interface.js`，在文件末尾追加：
```js
/** 执行已发布接口（M2-7 统一执行入口） */
export function execInterface(id, body) {
  return request.post(`/exec/${id}`, body)
}
```

- [ ] **Step 3: 提交**

```bash
cd frontend && git add src/api/interface.js package.json package-lock.json
git commit -m "feat(AUX-1): install highlight.js, add execInterface API"
```

---

## Task 2：创建 MessageDebug.vue 骨架（布局 + 模式切换）

**Files:**
- Create: `frontend/src/views/tools/MessageDebug.vue`

- [ ] **Step 1: 创建文件，写入完整骨架**

创建 `frontend/src/views/tools/MessageDebug.vue`，内容如下：

```vue
<template>
  <div class="message-debug">
    <!-- 模式切换 -->
    <div class="mode-bar">
      <el-radio-group v-model="mode" @change="onModeChange">
        <el-radio-button label="convert">格式转换调试</el-radio-button>
        <el-radio-button label="exec">接口调用调试</el-radio-button>
      </el-radio-group>
    </div>

    <!-- 配置栏 -->
    <div class="config-bar">
      <template v-if="mode === 'convert'">
        <span class="config-label">选择模板：</span>
        <el-select
          v-model="selectedTemplateId"
          placeholder="请选择转换模板"
          style="width: 320px"
          filterable
          clearable
        >
          <el-option
            v-for="t in templates"
            :key="t.id"
            :label="t.name"
            :value="t.id"
          />
        </el-select>
      </template>
      <template v-else>
        <span class="config-label">选择接口：</span>
        <el-select
          v-model="selectedInterfaceId"
          placeholder="请选择已发布接口"
          style="width: 320px"
          filterable
          clearable
        >
          <el-option
            v-for="i in publishedInterfaces"
            :key="i.id"
            :label="`${i.name}（${i.path}）`"
            :value="i.id"
          />
        </el-select>
      </template>
      <el-button
        type="primary"
        :loading="executing"
        style="margin-left: 12px"
        @click="execute"
      >
        执行
      </el-button>
    </div>

    <!-- 主区：左右分区 -->
    <div class="main-area">
      <!-- 左区：输入 -->
      <div class="panel">
        <div class="panel-header">
          {{ mode === 'convert' ? '源报文' : '请求参数' }}
        </div>
        <el-input
          v-model="inputText"
          type="textarea"
          :placeholder="inputPlaceholder"
          class="input-area"
          resize="none"
        />
      </div>

      <!-- 右区：结果 -->
      <div class="panel">
        <div class="panel-header">
          执行结果
          <span v-if="costMs !== null" class="cost-tag">耗时 {{ costMs }}ms</span>
        </div>
        <div class="result-area">
          <pre v-if="resultHtml" class="hljs result-pre">
            <code v-html="resultHtml" />
          </pre>
          <div v-else class="result-empty">执行后结果显示在此处</div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import hljs from 'highlight.js'
import 'highlight.js/styles/github-dark.css'
import { listTemplates } from '@/api/template'
import { listInterfaces, execInterface } from '@/api/interface'
import { convertMessage } from '@/api/convert'

const mode = ref('convert')

// 格式转换模式
const templates = ref([])
const selectedTemplateId = ref(null)

// 接口调用模式
const allInterfaces = ref([])
const selectedInterfaceId = ref(null)
const publishedInterfaces = computed(() =>
  allInterfaces.value.filter(i => i.status === 'published')
)

// 公共状态
const inputText = ref('')
const resultHtml = ref(null)
const costMs = ref(null)
const executing = ref(false)

const inputPlaceholder = computed(() =>
  mode.value === 'convert'
    ? '输入源报文（JSON / XML / CSV 均可）'
    : '输入请求参数（JSON 格式，如 {"status": 1}）'
)

async function loadTemplates() {
  const res = await listTemplates({ page: 1, size: 200, latestOnly: true })
  templates.value = res.records ?? []
}

async function loadInterfaces() {
  const res = await listInterfaces('', 1, 500)
  allInterfaces.value = res.records ?? []
}

function onModeChange() {
  inputText.value = ''
  resultHtml.value = null
  costMs.value = null
}

function renderResult(text) {
  try {
    resultHtml.value = hljs.highlightAuto(text).value
  } catch {
    resultHtml.value = text
  }
}

async function execute() {
  if (!inputText.value.trim()) {
    ElMessage.warning('请输入报文或参数')
    return
  }
  if (mode.value === 'convert' && !selectedTemplateId.value) {
    ElMessage.warning('请先选择转换模板')
    return
  }
  if (mode.value === 'exec' && !selectedInterfaceId.value) {
    ElMessage.warning('请先选择接口')
    return
  }

  executing.value = true
  resultHtml.value = null
  costMs.value = null

  try {
    if (mode.value === 'convert') {
      const res = await convertMessage({
        templateId: selectedTemplateId.value,
        message: inputText.value
      })
      renderResult(res.result)
      costMs.value = res.costMs
    } else {
      let body
      try {
        body = JSON.parse(inputText.value)
      } catch {
        ElMessage.warning('参数格式错误，请输入合法 JSON')
        return
      }
      const t0 = Date.now()
      const res = await execInterface(selectedInterfaceId.value, body)
      costMs.value = Date.now() - t0
      const text = typeof res === 'string' ? res : JSON.stringify(res, null, 2)
      renderResult(text)
    }
  } catch (e) {
    const msg = e?.message || '执行失败'
    resultHtml.value = `<span style="color:#f38ba8">${msg}</span>`
  } finally {
    executing.value = false
  }
}

onMounted(() => {
  loadTemplates()
  loadInterfaces()
})
</script>

<style scoped>
.message-debug {
  display: flex;
  flex-direction: column;
  height: calc(100vh - 120px);
  padding: 16px;
  gap: 12px;
}

.mode-bar {
  flex-shrink: 0;
}

.config-bar {
  flex-shrink: 0;
  display: flex;
  align-items: center;
}

.config-label {
  margin-right: 8px;
  color: #606266;
  font-size: 14px;
}

.main-area {
  flex: 1;
  display: flex;
  gap: 12px;
  overflow: hidden;
}

.panel {
  flex: 1;
  display: flex;
  flex-direction: column;
  border: 1px solid #dcdfe6;
  border-radius: 4px;
  overflow: hidden;
}

.panel-header {
  padding: 8px 12px;
  background: #f5f7fa;
  border-bottom: 1px solid #dcdfe6;
  font-size: 13px;
  font-weight: 500;
  color: #303133;
  display: flex;
  align-items: center;
  gap: 8px;
  flex-shrink: 0;
}

.cost-tag {
  font-size: 12px;
  color: #67c23a;
  font-weight: normal;
}

.input-area {
  flex: 1;
  height: 100%;
}

.input-area :deep(.el-textarea__inner) {
  height: 100%;
  font-family: monospace;
  font-size: 13px;
  border: none;
  border-radius: 0;
}

.result-area {
  flex: 1;
  overflow: auto;
  background: #0d1117;
}

.result-pre {
  margin: 0;
  padding: 12px;
  font-size: 13px;
  line-height: 1.6;
  white-space: pre-wrap;
  word-break: break-all;
}

.result-empty {
  padding: 24px;
  color: #909399;
  font-size: 13px;
}
</style>
```

- [ ] **Step 2: 启动前端确认无编译报错**

```bash
cd frontend && npm run dev
```
在浏览器访问 http://localhost:5173，控制台无红色报错即可（此时路由还未接通，页面不可见）。

- [ ] **Step 3: 提交骨架文件**

```bash
git add frontend/src/views/tools/MessageDebug.vue
git commit -m "feat(AUX-1): add MessageDebug.vue skeleton"
```

---

## Task 3：接通路由

**Files:**
- Modify: `frontend/src/router/index.js`（约第 159-163 行）

- [ ] **Step 1: 替换 tools/debug 路由**

在 `frontend/src/router/index.js` 中找到：
```js
{
  path: 'tools/debug',
  name: 'MessageDebug',
  component: () => import('@/views/placeholder/PlaceholderView.vue'),
  meta: { title: '报文调试' }
},
```
替换为：
```js
{
  path: 'tools/debug',
  name: 'MessageDebug',
  component: () => import('@/views/tools/MessageDebug.vue'),
  meta: { title: '报文调试' }
},
```

- [ ] **Step 2: 浏览器验证路由可访问**

在已登录状态下访问 http://localhost:5173/tools/debug，页面显示"格式转换调试 / 接口调用调试"切换按钮，左右两个面板出现，无报错。

- [ ] **Step 3: 提交**

```bash
git add frontend/src/router/index.js
git commit -m "feat(AUX-1): wire tools/debug route to MessageDebug"
```

---

## Task 4：验收测试

**前提：** 后端运行中（`mvn spring-boot:run`），且数据库中有至少一个转换模板和一个已发布接口。

- [ ] **Step 1: 验收 — 格式转换调试**

1. 访问 http://localhost:5173/tools/debug
2. 模式保持"格式转换调试"，配置栏下拉加载出模板列表
3. 选择一个已有转换模板
4. 左区输入合法 JSON 报文，例如：
   ```json
   {"userId": 1001, "action": "login"}
   ```
5. 点击「执行」
6. **预期**：右区出现带语法高亮的转换结果，顶部状态显示耗时（如 `耗时 12ms`）

- [ ] **Step 2: 验收 — 接口调用调试**

1. 切换模式为「接口调用调试」，配置栏下拉加载出已发布接口列表
2. 选择一个已发布的查询接口
3. 左区输入接口所需参数，例如：
   ```json
   {"status": 1}
   ```
4. 点击「执行」
5. **预期**：右区显示接口返回的 JSON 数据，带语法高亮

- [ ] **Step 3: 验收 — 空报文保护**

1. 清空左区文本
2. 点击「执行」
3. **预期**：弹出 `warning` 提示"请输入报文或参数"，不发起网络请求

- [ ] **Step 4: 验收 — 非法 JSON 保护（接口调用模式）**

1. 模式切换为「接口调用调试」，选好接口
2. 左区输入非 JSON 文本，如 `hello world`
3. 点击「执行」
4. **预期**：弹出 `warning` 提示"参数格式错误，请输入合法 JSON"

- [ ] **Step 5: 验收 — 未选模板/接口保护**

1. 格式转换模式，不选模板，输入任意报文，点执行
2. **预期**：弹出 `warning` 提示"请先选择转换模板"

- [ ] **Step 6: 提交最终版本**

```bash
git add -A
git commit -m "feat(AUX-1): complete message debug tool implementation"
```

- [ ] **Step 7: 推送**

```bash
git push origin master
```
