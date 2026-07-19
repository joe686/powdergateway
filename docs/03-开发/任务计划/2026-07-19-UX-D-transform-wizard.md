# UX-D · 接口转换向导 · 实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 抽出公共 `WizardShell.vue` 骨架并把原 SYS-5 十步向导迁移到 `SelectInterfaceSteps.vue`，同时新增 `/convert/wizard` 七步「接口转换配置向导」（FN-04 + FN-05），既有 `/interface/wizard` 行为零退化。

**Architecture:** `WizardShell.vue` 只承载步骤条 / 前后一步 / 校验回调 / 草稿提示等骨架职责；两个业务视图组件（`SelectInterfaceSteps.vue`、`TransformInterfaceSteps.vue`）通过 `default` 作用域插槽渲染各自步骤；两个 Pinia store（既有 `wizard.js` 与新增 `transformWizard.js`）各自持有状态并双写 localStorage；后端仅在 `port_route` / `convert_template` 增 3 列可空字段 + `list()` 支持 `functionCode` 过滤，不新增 Controller。

**Tech Stack:** Vue 3 `<script setup>`, Element Plus (`el-steps`/`el-step`/`el-form`/`el-select`/`el-radio-group`/`el-input-number`/`el-alert`/`el-tag`/`ElMessage`/`ElMessageBox`), Pinia + localStorage 双层草稿, Spring Boot 2.7.18, MyBatis-Plus 3.5.7, H2 测试基础设施

## Global Constraints

- 所有文档、注释、提交信息、UI 文案一律中文
- 前端 HTTP 一律走 `frontend/src/api/request.js` 的 axios 实例；禁止裸 `axios` 与 `fetch`
- vue-draggable-next 只使用 default slot + `v-for`，禁 `<template #item>`（本单元虽不用拖拽，仍作为项目铁律列出）
- `/interface/wizard` 原 10 步向导 SELECT/INSERT/UPDATE/DELETE 全流程行为完全不退化（`Task 3` 为硬门槛，未通过不得进入后续 Task）
- 每次新增前端路由必须同步 `backend/src/main/java/com/powergateway/config/MenuPermission.java` 的 `ADMIN_MENUS` 与 `USER_MENUS`；`READONLY_MENUS` 不加（回顾 CHG-011 E2E-5：SYS-5 曾漏 `/interface/wizard` 白名单导致 admin 被踢回 dashboard）
- Pinia store 与 `localStorage` 双层草稿保存（复用 SYS-5 `wizard.js` 的 `_skipNextPersist` / `persist()` / `hasDraft()` / `loadDraft()` / `reset()` 五方法契约）
- 深层字段访问一律用 `?.` 或 `v-if` 兜底（回顾 CHG-011 E2E-6：`wizard.tables[0]?.tableName` 空引用崩溃）
- 所有 Java 测试类必须加 `@ActiveProfiles("test")`
- TDD Red-Green-Refactor，禁止跳步：先失败测试 → 最小实现 → 通过 → 重构 → 再通过 → commit
- 每个 Task 结束时 `git commit`，提交信息前缀 `feat(UX-D):` / `test(UX-D):` / `refactor(UX-D):` / `chore(UX-D):`
- 完成后追加 `CHG-019 UX-D 接口转换向导` 到 `docs/03-开发/变更记录.md`，问题清单 FN-04/FN-05 从「待解决」搬到「已解决」

---

## 文件清单速览

| 操作 | 路径 | 用途 |
|------|------|------|
| 新增 | `frontend/src/components/wizard/WizardShell.vue` | 公共向导骨架（步骤条 + 前后一步 + 校验钩子） |
| 新增 | `frontend/src/composables/useWizardShell.js` | 草稿保存 composable（`useDraft` / `promptRestoreDraft`） |
| 新增 | `frontend/src/views/interface/SelectInterfaceSteps.vue` | 从 `InterfaceWizard.vue` 迁出的 10 步业务视图 |
| 修改 | `frontend/src/views/interface/InterfaceWizard.vue` | 瘦身为 Shell + Steps 容器 |
| 新增 | `frontend/src/store/transformWizard.js` | 转换向导 Pinia store（`transform_wizard_draft` key） |
| 新增 | `frontend/src/api/functionCode.js` | 功能号存在性检查（内部调 `/api/port-route/list?functionCode=`） |
| 修改 | `frontend/src/api/template.js` | `listTemplates(params)` 已支持传参，无需改；仅补一个便捷方法 `listTemplatesByFunctionCode(fc)` |
| 修改 | `frontend/src/api/portRoute.js` | `savePortRoute()` 已透传所有字段（axios 序列化整对象），确认可传 `functionCode`/`functionName` |
| 新增 | `frontend/src/views/convert/TransformWizard.vue` | 转换向导 Shell 容器（约 30 行） |
| 新增 | `frontend/src/views/convert/TransformInterfaceSteps.vue` | 转换向导 7 步业务视图 |
| 修改 | `frontend/src/router/index.js` | append `/convert/wizard` 路由 |
| 修改 | `frontend/src/components/layout/SideMenu.vue` | 在「接口转换配置」子菜单顶部插入向导入口 + `CONVERT_PATHS` 追加 |
| 修改 | `frontend/src/views/convert/PortRoute.vue` | 编辑弹窗新增 `functionCode` / `functionName` 输入；列表可选展示「功能号」列 |
| 修改 | `frontend/src/views/convert/TemplateList.vue` | 编辑弹窗新增 `functionCode` 输入；列表可选展示「功能号」列 |
| 新增 | `backend/src/main/resources/db/migration-ux-d-function-code.sql` | 幂等 ALTER 迁移脚本（存储过程检测列存在性） |
| 修改 | `backend/src/main/resources/db/init.sql` | `port_route` / `convert_template` DDL 追加 3 列 |
| 修改 | `backend/src/main/java/com/powergateway/model/PortRoute.java` | 加 `functionCode` / `functionName` 字段 |
| 修改 | `backend/src/main/java/com/powergateway/model/ConvertTemplate.java` | 加 `functionCode` 字段 |
| 修改 | `backend/src/main/java/com/powergateway/model/dto/PortRouteSaveRequest.java` | 加 `functionCode` / `functionName` 字段 |
| 修改 | `backend/src/main/java/com/powergateway/model/dto/TemplateQueryRequest.java` | 加 `functionCode` 字段 |
| 修改 | `backend/src/main/java/com/powergateway/controller/PortRouteController.java` | `list()` 端点新增可选参数 `functionCode` |
| 修改 | `backend/src/main/java/com/powergateway/service/PortRouteService.java` | `listRoutes(page,size,channelCode,functionCode)` 过滤 + `saveRoute` 透传 |
| 修改 | `backend/src/main/java/com/powergateway/service/TemplateService.java` | `listTemplates()` 支持 `functionCode` 精确匹配 |
| 修改 | `backend/src/main/java/com/powergateway/config/MenuPermission.java` | `ADMIN_MENUS` / `USER_MENUS` 追加 `/convert/wizard` |
| 新增 | `backend/src/test/java/com/powergateway/UXDPortRouteFunctionCodeTest.java` | 测试 `functionCode` 过滤 + 字段存取 |
| 新增 | `backend/src/test/java/com/powergateway/UXDTemplateFunctionCodeTest.java` | 测试模板 `functionCode` 过滤 |
| 新增 | `backend/src/test/java/com/powergateway/UXDMenuPermissionTest.java` | 断言 `/convert/wizard` 在 ADMIN/USER 白名单，且不在 READONLY |
| 修改 | `docs/03-开发/变更记录.md` | 追加 `CHG-019` |
| 修改 | `docs/03-开发/问题清单.md` | FN-04 / FN-05 从「D 组 · 待解决」移到「已解决」 |
| 修改 | `docs/03-开发/开发计划.md` | UX-D 行填交付时间与状态 |
| 修改 | `docs/01-需求/需求拆分与最小实现方案.md` | 阶段六 UX-D 单元描述定稿 |

---

## Task 1: `WizardShell.vue` 公共骨架 + `useWizardShell` composable

**Files:**
- Create: `frontend/src/components/wizard/WizardShell.vue`
- Create: `frontend/src/composables/useWizardShell.js`
- Create: `frontend/tests/components/WizardShell.spec.js`（Vitest 组件测试，若项目还没 Vitest 则本 Task 内一并落 `vitest.config.js` + `package.json` script）

**Interfaces:**
- Consumes: 无（第一份骨架）
- Produces:
  - 组件 `WizardShell.vue`，Props：`title:String`（必填）、`steps:Array<{key:String,label:String,tip?:String,skipWhen?:(state)=>Boolean}>`（必填）、`currentStep:Number`（v-model，必填）、`draftSaved:Boolean`（可选，默认 false）、`submitLabel:String`（可选，默认 `'完成'`）、`validateNext:Function`（可选，`(currentKey)=>true|false|string`）；Slots：`default={ currentKey, isActive }`、`header-right`、`footer-extra`；Events：`update:currentStep(newIndex)`、`submit()`、`back()`；computed `visibleSteps`（过滤 `skipWhen`）、`currentStepDef`、`isLastStep`
  - composable `useWizardShell.js`：`useDraft(storeInstance, options?)` 返回 `{ draftSaved:Ref<boolean>, promptRestoreDraft:()=>Promise<boolean> }`；内部通过 `watch(store.$state, deep) → store.persist()` 触发，且在 `promptRestoreDraft` 中调用 `ElMessageBox.confirm`；成功恢复调 `store.loadDraft()` 返回 `true`，取消调 `store.reset()` 返回 `false`

- [ ] **Step 1 (Red)**：先落 `frontend/tests/components/WizardShell.spec.js`，写以下 4 个用例并全部失败：
  - `渲染 steps 空数组时不崩溃且步骤条 el-step 数量为 0`
  - `传 skipWhen 返回 true 的步骤在 visibleSteps 中不出现`
  - `validateNext 返回字符串时点击下一步阻止前进且触发 ElMessage.warning`
  - `v-model:currentStep 双向绑定：外部改 → 内部渲染切换；内部点下一步 → emit update:currentStep`

  如项目未装 Vitest：先在 `frontend/package.json` 追加 `"test": "vitest run"` 与 `"vitest"`/`"@vue/test-utils"` devDependencies；`frontend/vitest.config.js` 用 jsdom 环境。执行 `npm run test` 确认 4 个用例全 fail（模块不存在）。

- [ ] **Step 2 (Green)**：实现 `frontend/src/components/wizard/WizardShell.vue`，模板结构：

  ```vue
  <template>
    <div class="wizard-shell">
      <div class="wizard-header">
        <span class="wizard-title">{{ title }}</span>
        <div class="wizard-header-right">
          <span v-if="draftSaved" class="draft-hint">草稿已自动保存</span>
          <slot name="header-right">
            <el-button size="small" @click="$emit('back')">返回列表</el-button>
          </slot>
        </div>
      </div>
      <el-steps :active="currentStep" finish-status="success" align-center class="wizard-steps">
        <el-step v-for="s in visibleSteps" :key="s.key" :title="s.label" />
      </el-steps>
      <el-card class="wizard-content">
        <template #header>
          <div class="step-header">
            <span>Step {{ currentStep + 1 }} · {{ currentStepDef.label }}</span>
            <el-tooltip v-if="currentStepDef.tip" :content="currentStepDef.tip" placement="left">
              <span class="help-btn">?</span>
            </el-tooltip>
          </div>
        </template>
        <slot :current-key="currentStepDef.key" :is-active="isActive" />
      </el-card>
      <div class="wizard-footer">
        <el-button :disabled="currentStep === 0" @click="onPrev">← 上一步</el-button>
        <span class="step-counter">步骤 {{ currentStep + 1 }} / {{ visibleSteps.length }}</span>
        <slot name="footer-extra" />
        <el-button v-if="!isLastStep" type="primary" @click="onNext">下一步 →</el-button>
        <el-button v-else type="success" @click="onSubmit">{{ submitLabel }}</el-button>
      </div>
    </div>
  </template>
  ```

  `<script setup>` 关键：
  - `const props = defineProps({ title:{type:String,required:true}, steps:{type:Array,required:true}, currentStep:{type:Number,required:true}, draftSaved:{type:Boolean,default:false}, submitLabel:{type:String,default:'完成'}, validateNext:{type:Function,default:null} })`
  - `const emit = defineEmits(['update:currentStep','submit','back'])`
  - `const visibleSteps = computed(() => (props.steps || []).filter(s => !(typeof s.skipWhen === 'function' && s.skipWhen())))`
  - `const currentStepDef = computed(() => visibleSteps.value[props.currentStep] ?? { key:'', label:'', tip:'' })`  ← **保留 CHG-011 E2E-6 教训：默认对象保证 `.label`/`.tip` 深引用不崩溃**
  - `const isLastStep = computed(() => visibleSteps.value.length > 0 && props.currentStep === visibleSteps.value.length - 1)`
  - `function isActive(key){ return currentStepDef.value.key === key }`
  - `function onNext(){ const res = props.validateNext ? props.validateNext(currentStepDef.value.key) : true; if (res === false){ return }; if (typeof res === 'string'){ ElMessage.warning(res); return }; if (props.currentStep < visibleSteps.value.length - 1){ emit('update:currentStep', props.currentStep + 1) } }`
  - `function onPrev(){ if (props.currentStep > 0){ emit('update:currentStep', props.currentStep - 1) } }`
  - `function onSubmit(){ const res = props.validateNext ? props.validateNext(currentStepDef.value.key) : true; if (res === false) return; if (typeof res === 'string'){ ElMessage.warning(res); return }; emit('submit') }`

  再实现 `frontend/src/composables/useWizardShell.js`：

  ```js
  import { ref, watch } from 'vue'
  import { ElMessageBox } from 'element-plus'

  export function useDraft(store) {
    const draftSaved = ref(false)
    watch(() => store.$state, () => { store.persist(); draftSaved.value = true }, { deep: true })
    async function promptRestoreDraft(message = '发现未完成的向导配置，是否恢复？') {
      if (!store.hasDraft()) return false
      try {
        await ElMessageBox.confirm(message, '恢复草稿', {
          confirmButtonText: '恢复', cancelButtonText: '重新开始', type: 'info'
        })
        store.loadDraft()
        return true
      } catch {
        store.reset()
        return false
      }
    }
    return { draftSaved, promptRestoreDraft }
  }
  ```

  Run `npm run test` → 4 用例全绿。

- [ ] **Step 3 (Refactor)**：把 `<style scoped>` 从既有 `InterfaceWizard.vue` 完整搬进 `WizardShell.vue`（`.wizard-page`/`.wizard-header`/`.wizard-title`/`.wizard-header-right`/`.draft-hint`/`.wizard-steps`/`.wizard-content`/`.step-header`/`.help-btn`/`.wizard-footer`/`.step-counter`），改根 class 名 `.wizard-page` → `.wizard-shell`。再跑 `npm run test`，仍 4 绿。

- [ ] **Step 4 (Commit)**：
  ```bash
  git add frontend/src/components/wizard/WizardShell.vue frontend/src/composables/useWizardShell.js frontend/tests/components/WizardShell.spec.js frontend/vitest.config.js frontend/package.json
  git commit -m "feat(UX-D): add WizardShell skeleton and useDraft composable"
  ```

---

## Task 2: 迁移原 10 步到 `SelectInterfaceSteps.vue`

**Files:**
- Create: `frontend/src/views/interface/SelectInterfaceSteps.vue`
- Modify: `frontend/src/views/interface/InterfaceWizard.vue`（瘦身为 ≤ 60 行的 Shell 容器）

**Interfaces:**
- Consumes: `WizardShell.vue`（Task 1），`useDraft`（Task 1），既有 `useWizardStore`（`frontend/src/store/wizard.js`，不改）
- Produces:
  - 组件 `SelectInterfaceSteps.vue`，Props：`isActive:Function`（必填，由 Shell 作用域插槽注入）；不 emit；对外暴露 `defineExpose({ validateStep, buildPayload, onSubmit })`：
    - `validateStep(currentKey):true|string` — 原 `nextStep` 内所有 `if (key === ...)` 分支的校验逻辑
    - `buildPayload():object` — 原文件 line 685-739
    - `onSubmit():Promise<void>` — 与原 `doPublish` 同语义，成功后 push `/interface/list`
  - `InterfaceWizard.vue` 瘦身容器：只保留 `WizardShell` + `SelectInterfaceSteps` 组合、`STEP_DEFS`、Shell 的 `v-model:currentStep`、`validate-next` 桥接、`@submit` 调 `steps.value.onSubmit()`、`@back` push `/interface/list`

- [ ] **Step 1 (Red)**：新建 `frontend/tests/views/InterfaceWizardMigration.spec.js`，只写一个"占位"测试断言：`import InterfaceWizard from '@/views/interface/InterfaceWizard.vue'; expect(InterfaceWizard.__file || 'ok').toBeTruthy()`。这不是行为测试，但强制 IDE 加载新结构；后续 Task 3 的 pg-testkit 冒烟才是真正的回归验收。Run `npm run test` → 因 SelectInterfaceSteps.vue 尚未新建，import 链会失败。

- [ ] **Step 2 (Green — 迁移)**：
  1. 拷贝 `InterfaceWizard.vue` 现文件全部内容到 `SelectInterfaceSteps.vue`
  2. `SelectInterfaceSteps.vue` 的模板结构改为**只输出 10 段 `<div v-show="...">`**（第 33-329 行内容），最外层用 `<template>` 包裹，删除 `.wizard-page`/`.wizard-header`/`el-steps`/`el-card`/`.wizard-footer`
  3. 把 `isActive(key)` 从内部 computed 改为 Props：`const props = defineProps({ isActive: { type: Function, required: true } })`，模板所有 `isActive('xxx')` 改成 `props.isActive('xxx')`
  4. 保留 script 中所有原函数（`onTypeChange`/`onDbChange`/`rebuildSelectedColumns`/`buildPayload`/`doPreview`/`doSave`/`doPublish` 等）
  5. 删除 `onMounted` 中的草稿恢复 `ElMessageBox.confirm` 块（迁到父容器 InterfaceWizard.vue 使用 `useDraft.promptRestoreDraft`），保留 `dbList`/`shardList` 加载
  6. 保留 `wizard.tables[0]?.tableName` 的 `?.` 保护 + 138 行的 `v-if="wizard.tables[0]"`（CHG-011 E2E-6 铁律）
  7. 把原 `nextStep` 拆成两半：留下 `wizard.currentStep++` 由 Shell 负责；抽出 `function validateStep(key) { /* 原所有 if(key===...) 分支，return true 或字符串 */ }`
  8. 抽出 `async function onSubmit(){ await doPublish() }`（发布走完整走 doPublish 语义）
  9. 底部 `defineExpose({ validateStep, buildPayload, onSubmit })`

  然后重写 `InterfaceWizard.vue`：

  ```vue
  <template>
    <WizardShell
      title="接口配置向导"
      :steps="visibleSteps"
      v-model:current-step="wizard.currentStep"
      :draft-saved="draftSaved"
      :validate-next="onValidateNext"
      @submit="onSubmit"
      @back="goList"
    >
      <template #default="{ isActive }">
        <SelectInterfaceSteps ref="stepsRef" :is-active="isActive" />
      </template>
    </WizardShell>
  </template>

  <script setup>
  import { ref, computed, onMounted } from 'vue'
  import { useRouter } from 'vue-router'
  import WizardShell from '@/components/wizard/WizardShell.vue'
  import SelectInterfaceSteps from '@/views/interface/SelectInterfaceSteps.vue'
  import { useWizardStore } from '@/store/wizard'
  import { useDraft } from '@/composables/useWizardShell'

  const router = useRouter()
  const wizard = useWizardStore()
  const stepsRef = ref(null)
  const { draftSaved, promptRestoreDraft } = useDraft(wizard)

  const STEP_DEFS = [ /* 10 项，与迁移前完全一致 */ ]
  const visibleSteps = computed(() =>
    STEP_DEFS.filter(s => !wizard.interfaceType || !s.skipFor.includes(wizard.interfaceType))
  )
  function onValidateNext(key) {
    return stepsRef.value ? stepsRef.value.validateStep(key) : true
  }
  async function onSubmit() {
    if (stepsRef.value) await stepsRef.value.onSubmit()
  }
  function goList() { router.push('/interface/list') }

  onMounted(async () => {
    if (wizard.interfaceType === '') await promptRestoreDraft()
    // UPDATE/DELETE tables[0] 兜底（CHG-011 E2E-6 教训）
    if ((wizard.interfaceType === 'UPDATE' || wizard.interfaceType === 'DELETE') && wizard.tables.length === 0) {
      wizard.tables = [{ tableName: '' }]
    }
  })
  </script>
  ```

  行数目标 ≤ 60 行（不含空行）。

- [ ] **Step 3 (Green — 验证)**：`npm run test` → Vitest 通过；然后 `npm run dev`，浏览器手动进入 `/interface/wizard`：
  - 页面能加载，无控制台报错
  - 步骤条显示 10 步（interfaceType 为空时）
  - 切换 SELECT → 步骤条不变；切换 DELETE → 步骤条变 8 步
  - 底部按钮点击可切换步骤

  Ctrl+C 停止。

- [ ] **Step 4 (Commit)**：
  ```bash
  git add frontend/src/views/interface/InterfaceWizard.vue frontend/src/views/interface/SelectInterfaceSteps.vue frontend/tests/views/InterfaceWizardMigration.spec.js
  git commit -m "refactor(UX-D): extract SelectInterfaceSteps and slim down InterfaceWizard to Shell container"
  ```

---

## Task 3: `/interface/wizard` 全流程回归门槛

**Files:** 无代码改动（本 Task 是纯验收 gate），可写脚本记录：
- Create: `docs/04-测试/UX-D-regression-log.md`（手工冒烟记录）

**Interfaces:**
- Consumes: Task 2 产物
- Produces: 一份带勾选清单的回归日志，且必须全绿方可继续 Task 4

**这是硬门槛：SYS-5 原 7 条验收标准全部复跑，任何一项失败必须回到 Task 2 修复而不是继续前进。**

- [ ] **Step 1**：启动后端 `mvn spring-boot:run`（backend/）与前端 `npm run dev`（frontend/），登录 admin/Admin@123。

- [ ] **Step 2 · SELECT 全流程**：`/interface/wizard`
  1. Step ① 选 SELECT → 步骤条 10 步
  2. Step ② 填名 "R-SELECT-测试"，选已有 MySQL 连接
  3. Step ③ 选主表 + 一个 LEFT JOIN
  4. Step ④ 勾选 3 字段 + 改 1 个别名
  5. Step ⑤/⑥ 跳过
  6. Step ⑦ 配一个 `id = :id` 条件
  7. Step ⑧ 日志开
  8. Step ⑨ 输参数、执行预览、结果非空
  9. Step ⑩ 保存并发布 → `/interface/list` 可见，status = published
  10. `curl -X POST http://localhost:8080/api/exec/{newId} -H 'satoken: xxx' -d '{"id":1}'` → 200 且 data 非空

- [ ] **Step 3 · INSERT 全流程**：
  1. Step ① 选 INSERT → 步骤条自动去掉 ⑦（条件配置）
  2. Step ③ 加 1 张目标表，字段来源含 REQUEST/CONST/CALC 各一
  3. Step ⑨ 预览成功
  4. Step ⑩ 发布 → `/api/exec/{id}` 调用成功且业务库有插入记录

- [ ] **Step 4 · UPDATE 全流程**：
  1. Step ① UPDATE → 步骤条 10 步
  2. Step ⑦ 不配条件直接下一步 → 弹 warning "UPDATE/DELETE 必须配置至少一个条件"（阻止前进）
  3. 配主键条件后正常前进
  4. Step ⑩ 发布 → exec 调用成功

- [ ] **Step 5 · DELETE 全流程**：
  1. Step ① DELETE → 步骤条 8 步（无 ④⑥）
  2. Step ⑨ 显示预览警告 "以下将预览待删除数据..."
  3. 完整走通后发布 → exec 调用成功

- [ ] **Step 6 · 草稿恢复**：走到 Step 4 后**关闭浏览器 Tab**，重新打开 `/interface/wizard` → 弹恢复提示 → 点"恢复" → 从 Step 4 继续；再次关掉、重新打开、点"重新开始" → 从 Step 1 开始且 localStorage 清空。

- [ ] **Step 7 · CHG-011 E2E-6 空引用防退化**：
  - 打开浏览器 devtools → Console
  - 直接访问 `/interface/wizard`（新 tab、清 localStorage、interfaceType 空态）
  - 点 Step ③ 时 `wizard.tables` 若为空 → 页面 UPDATE/DELETE 分支必须显示"请选择目标表"而非崩溃
  - Console 全程无 `Cannot read properties of undefined (reading 'tableName')` 报错

- [ ] **Step 8 (Log)**：在 `docs/04-测试/UX-D-regression-log.md` 记录每步实际耗时、发现的偏差；若有任何一步失败，回退 Task 2 修复代码后重新跑完整 Task 3，不得跳过任何步骤。

- [ ] **Step 9 (Commit)**：
  ```bash
  git add docs/04-测试/UX-D-regression-log.md
  git commit -m "test(UX-D): regression log for /interface/wizard after WizardShell extraction"
  ```

---

## Task 4: 后端字段与迁移脚本

**Files:**
- Create: `backend/src/main/resources/db/migration-ux-d-function-code.sql`
- Modify: `backend/src/main/resources/db/init.sql`
- Modify: `backend/src/main/java/com/powergateway/model/PortRoute.java`
- Modify: `backend/src/main/java/com/powergateway/model/ConvertTemplate.java`
- Modify: `backend/src/main/java/com/powergateway/model/dto/PortRouteSaveRequest.java`
- Modify: `backend/src/main/java/com/powergateway/model/dto/TemplateQueryRequest.java`
- Modify: `backend/src/main/java/com/powergateway/service/PortRouteService.java`
- Modify: `backend/src/main/java/com/powergateway/service/TemplateService.java`
- Modify: `backend/src/main/java/com/powergateway/controller/PortRouteController.java`
- Create: `backend/src/test/java/com/powergateway/UXDPortRouteFunctionCodeTest.java`
- Create: `backend/src/test/java/com/powergateway/UXDTemplateFunctionCodeTest.java`

**Interfaces:**
- Consumes: 无（后端独立单元，可与前端 Task 2/3 并行）
- Produces:
  - 数据库列 `port_route.function_code VARCHAR(64) NULL` / `port_route.function_name VARCHAR(128) NULL` / `convert_template.function_code VARCHAR(64) NULL`
  - `PortRoute.functionCode:String` / `PortRoute.functionName:String`
  - `ConvertTemplate.functionCode:String`
  - `PortRouteSaveRequest.functionCode:String` / `PortRouteSaveRequest.functionName:String`
  - `TemplateQueryRequest.functionCode:String`
  - `PortRouteService.listRoutes(int page, int size, String channelCode, String functionCode):Page<PortRoute>` — 新签名保持向后兼容（旧签名 `listRoutes(page,size,channelCode)` 作为默认参数为 null 的重载）
  - `TemplateService.listTemplates(TemplateQueryRequest req)` 新增 `functionCode` 精确匹配分支
  - `PortRouteController.list(...)` GET 端点新增 `@RequestParam(required = false) String functionCode`

- [ ] **Step 1 (Red · PortRoute)**：写 `backend/src/test/java/com/powergateway/UXDPortRouteFunctionCodeTest.java`：
  ```java
  @SpringBootTest
  @ActiveProfiles("test")
  @Transactional
  class UXDPortRouteFunctionCodeTest {
    @Autowired PortRouteService svc;

    @Test void 保存路由并回读_functionCode与functionName生效() {
      PortRouteSaveRequest req = new PortRouteSaveRequest();
      req.setChannelCode("UXD_CH_1");
      req.setPortAddress("http://localhost:9999/mock");
      req.setFunctionCode("UXD_TEST_01");
      req.setFunctionName("UXD 测试01");
      Long id = svc.saveRoute(req);
      PortRoute r = svc.getById(id);   // 需要 svc 补一个 getById 或用 mapper.selectById
      assertEquals("UXD_TEST_01", r.getFunctionCode());
      assertEquals("UXD 测试01", r.getFunctionName());
    }

    @Test void listRoutes_按functionCode精确过滤() {
      // 构造 2 条：fc=A、fc=B，断言按 A 查只返回一条
      ...
    }

    @Test void listRoutes_functionCode为空_仍走原channelCode过滤() {
      ...
    }
  }
  ```
  `mvn test -Dtest=UXDPortRouteFunctionCodeTest` → 全 fail（编译错误：字段不存在）。

- [ ] **Step 2 (Green · PortRoute 实体 + DTO + Service)**：
  - `PortRoute.java` 追加：
    ```java
    /** 功能号（UX-D），软唯一（代码层校验），可空 */
    private String functionCode;
    /** 功能号中文名（UX-D），可空 */
    private String functionName;
    ```
  - `PortRouteSaveRequest.java` 追加同名两字段
  - `PortRouteService.java`：
    - `listRoutes` 改签名为 `listRoutes(int page, int size, String channelCode, String functionCode)`，wrapper 追加 `if (functionCode != null && !functionCode.trim().isEmpty()) wrapper.eq(PortRoute::getFunctionCode, functionCode.trim());`
    - 保留原三参重载：`public Page<PortRoute> listRoutes(int page, int size, String channelCode) { return listRoutes(page, size, channelCode, null); }`
    - `saveRoute` 中 `route.setFunctionCode(req.getFunctionCode()); route.setFunctionName(req.getFunctionName());`
    - 补一个 `public PortRoute getById(Long id) { return portRouteMapper.selectById(id); }`（若没有）
  - `PortRouteController.java` `list` 端点：
    ```java
    @GetMapping("/api/port-route/list")
    public Result<Page<PortRoute>> list(
        @RequestParam(defaultValue = "1") int page,
        @RequestParam(defaultValue = "10") int size,
        @RequestParam(required = false) String channelCode,
        @RequestParam(required = false) String functionCode) {
      return Result.success(portRouteService.listRoutes(page, size, channelCode, functionCode));
    }
    ```
  - `init.sql`：`port_route` 表 DDL 在 `header_config TEXT ...` 后追加两行：
    ```sql
    function_code        VARCHAR(64)  COMMENT '功能号（UX-D），可空',
    function_name        VARCHAR(128) COMMENT '功能号中文名（UX-D），可空',
    ```

  `mvn test -Dtest=UXDPortRouteFunctionCodeTest` → 3 用例全绿。

- [ ] **Step 3 (Red · Template)**：写 `backend/src/test/java/com/powergateway/UXDTemplateFunctionCodeTest.java`：
  ```java
  @SpringBootTest @ActiveProfiles("test") @Transactional
  class UXDTemplateFunctionCodeTest {
    @Autowired TemplateService svc;

    @Test void listTemplates_按functionCode过滤_仅返回匹配模板() {
      // 构造 3 个模板：fc=A / fc=B / fc=null，查 fc=A 只返回 1 个
      ...
    }
    @Test void listTemplates_functionCode为空_返回全部() { ... }
  }
  ```
  fail（编译错误）。

- [ ] **Step 4 (Green · Template)**：
  - `ConvertTemplate.java` 追加 `private String functionCode;`
  - `TemplateQueryRequest.java` 追加 `private String functionCode;`
  - `TemplateService.listTemplates(TemplateQueryRequest req)`：在 wrapper 构造处追加 `if (req.getFunctionCode() != null && !req.getFunctionCode().trim().isEmpty()) wrapper.eq(ConvertTemplate::getFunctionCode, req.getFunctionCode().trim());`
  - 若 `TemplateController` 的 `list` 端点是 `@GetMapping` 带 query 参数（非 body），确认能自动绑定 `TemplateQueryRequest` 的 `functionCode`（Spring MVC 自动映射 `?functionCode=`）；若使用 `@RequestBody` 则无需改
  - `init.sql`：`convert_template` 表 DDL 在 `creator VARCHAR(64),` 前追加 `function_code VARCHAR(64) COMMENT '功能号（UX-D），可空',`

  `mvn test -Dtest=UXDTemplateFunctionCodeTest` → 全绿。

- [ ] **Step 5 (Migration Script)**：`backend/src/main/resources/db/migration-ux-d-function-code.sql`：
  ```sql
  -- UX-D 迁移：port_route + convert_template 各加 function_code(_name) 字段，幂等
  DELIMITER $$
  DROP PROCEDURE IF EXISTS migrate_uxd_function_code$$
  CREATE PROCEDURE migrate_uxd_function_code()
  BEGIN
    IF NOT EXISTS (
      SELECT 1 FROM information_schema.columns
      WHERE table_schema = DATABASE() AND table_name = 'port_route' AND column_name = 'function_code'
    ) THEN
      ALTER TABLE port_route ADD COLUMN function_code VARCHAR(64) COMMENT '功能号（UX-D），可空';
    END IF;
    IF NOT EXISTS (
      SELECT 1 FROM information_schema.columns
      WHERE table_schema = DATABASE() AND table_name = 'port_route' AND column_name = 'function_name'
    ) THEN
      ALTER TABLE port_route ADD COLUMN function_name VARCHAR(128) COMMENT '功能号中文名（UX-D），可空';
    END IF;
    IF NOT EXISTS (
      SELECT 1 FROM information_schema.columns
      WHERE table_schema = DATABASE() AND table_name = 'convert_template' AND column_name = 'function_code'
    ) THEN
      ALTER TABLE convert_template ADD COLUMN function_code VARCHAR(64) COMMENT '功能号（UX-D），可空';
    END IF;
  END$$
  DELIMITER ;
  CALL migrate_uxd_function_code();
  DROP PROCEDURE IF EXISTS migrate_uxd_function_code;
  ```
  在开发环境 MySQL 手工执行一次验证幂等（第二次执行不报错）。

- [ ] **Step 6 (Refactor + 全量回归)**：`mvn test` 跑全量，326 用例（含本单元新增 5 用例，达 331）必须全绿。若既有测试被字段变更打破，修 broken 用例（大概率无破坏因为 3 个字段都可空）。

- [ ] **Step 7 (Commit)**：
  ```bash
  git add backend/src/main/resources/db/init.sql backend/src/main/resources/db/migration-ux-d-function-code.sql backend/src/main/java/com/powergateway/model/PortRoute.java backend/src/main/java/com/powergateway/model/ConvertTemplate.java backend/src/main/java/com/powergateway/model/dto/PortRouteSaveRequest.java backend/src/main/java/com/powergateway/model/dto/TemplateQueryRequest.java backend/src/main/java/com/powergateway/service/PortRouteService.java backend/src/main/java/com/powergateway/service/TemplateService.java backend/src/main/java/com/powergateway/controller/PortRouteController.java backend/src/test/java/com/powergateway/UXDPortRouteFunctionCodeTest.java backend/src/test/java/com/powergateway/UXDTemplateFunctionCodeTest.java
  git commit -m "feat(UX-D): add function_code to port_route and convert_template with idempotent migration"
  ```

---

## Task 5: `MenuPermission.java` 白名单 + 路由 + 菜单入口（防 CHG-011 E2E-5 重演）

**Files:**
- Modify: `backend/src/main/java/com/powergateway/config/MenuPermission.java`
- Create: `backend/src/test/java/com/powergateway/UXDMenuPermissionTest.java`
- Modify: `frontend/src/router/index.js`
- Modify: `frontend/src/components/layout/SideMenu.vue`
- Create: `frontend/src/views/convert/TransformWizard.vue`（占位版，Task 8 才完全实现，本 Task 只让路由能落地）

**Interfaces:**
- Consumes: 无
- Produces:
  - `MenuPermission.ADMIN_MENUS` 包含 `/convert/wizard`
  - `MenuPermission.USER_MENUS` 包含 `/convert/wizard`
  - `MenuPermission.READONLY_MENUS` **不包含** `/convert/wizard`
  - 前端路由 `/convert/wizard` 已注册，占位组件展示"接口转换配置向导（开发中）"
  - `CONVERT_PATHS` 数组新增 `/convert/wizard`；菜单项在"接口转换配置"分组最上方

**这是关键防退化 Task：绝不能拖到最后。CHG-011 E2E-5 铁的教训。**

- [ ] **Step 1 (Red · Backend)**：写 `backend/src/test/java/com/powergateway/UXDMenuPermissionTest.java`：
  ```java
  @ActiveProfiles("test")
  class UXDMenuPermissionTest {
    @Test void ADMIN_MENUS_包含转换向导路径() {
      assertTrue(MenuPermission.ADMIN_MENUS.contains("/convert/wizard"));
    }
    @Test void USER_MENUS_包含转换向导路径() {
      assertTrue(MenuPermission.USER_MENUS.contains("/convert/wizard"));
    }
    @Test void READONLY_MENUS_不包含转换向导路径() {
      assertFalse(MenuPermission.READONLY_MENUS.contains("/convert/wizard"));
    }
  }
  ```
  `mvn test -Dtest=UXDMenuPermissionTest` → 前两个 fail。

- [ ] **Step 2 (Green · Backend)**：编辑 `MenuPermission.java`：
  - `ADMIN_MENUS` 第 4 行 `"/convert/format", "/convert/field-mapping", "/convert/field-process",` 前面插入 `"/convert/wizard",`（保持"向导 → 基础配置 → 端口路由"顺序）
  - `USER_MENUS` 同位置同样插入
  - `READONLY_MENUS` 不改

  `mvn test -Dtest=UXDMenuPermissionTest` → 3 绿。

- [ ] **Step 3 (Frontend · 占位)**：
  - `frontend/src/views/convert/TransformWizard.vue`：
    ```vue
    <template>
      <div style="padding:20px">接口转换配置向导（开发中，Task 6-8 补齐）</div>
    </template>
    <script setup></script>
    ```
  - `frontend/src/router/index.js`：在 `/convert/template` 路由块之后插入（注意保持"接口转换配置（模块一）"注释块内）：
    ```js
    {
      path: 'convert/wizard',
      name: 'TransformWizard',
      component: () => import('@/views/convert/TransformWizard.vue'),
      meta: { title: '接口转换配置向导' }
    },
    ```
  - `frontend/src/components/layout/SideMenu.vue`：
    - `CONVERT_PATHS` 数组头部追加 `'/convert/wizard'`
    - 在 `<el-menu-item v-if="can('/convert/format')" ...>` 之前插入：
      ```html
      <el-menu-item v-if="can('/convert/wizard')" index="/convert/wizard">接口转换配置向导</el-menu-item>
      ```

- [ ] **Step 4 (Manual · 三角色实测)**：`mvn spring-boot:run` + `npm run dev`：
  - admin/Admin@123 登录 → 左侧「接口转换配置」下第一项是「接口转换配置向导」→ 点击可访问，页面显示"开发中"
  - 用一个 user 角色账号（若无，通过 `/system/user` 建一个）登录 → 同样可见并可访问
  - 用一个 readonly 角色账号 → 菜单项**不显示**；直接手输 URL `/convert/wizard` → 路由守卫踢回 `/dashboard`

- [ ] **Step 5 (Commit)**：
  ```bash
  git add backend/src/main/java/com/powergateway/config/MenuPermission.java backend/src/test/java/com/powergateway/UXDMenuPermissionTest.java frontend/src/router/index.js frontend/src/components/layout/SideMenu.vue frontend/src/views/convert/TransformWizard.vue
  git commit -m "feat(UX-D): register /convert/wizard route and menu with ADMIN/USER whitelist"
  ```

---

## Task 6: `transformWizard.js` Pinia store + `functionCode.js` API

**Files:**
- Create: `frontend/src/store/transformWizard.js`
- Create: `frontend/src/api/functionCode.js`
- Create: `frontend/tests/store/transformWizard.spec.js`

**Interfaces:**
- Consumes: 无
- Produces:
  - `useTransformWizardStore` — 与 SYS-5 `wizard.js` 同构的 state/actions 契约：
    - state 字段：见「结构定义」下方
    - actions：`reset()` / `persist()` / `hasDraft():boolean` / `loadDraft():boolean`
    - localStorage key：`transform_wizard_draft`
    - `persist()` 排除字段：`testOutput`、`testError`、`_skipNextPersist`
  - `checkFunctionCodeExists(functionCode):Promise<boolean>` — 内部 `request.get('/port-route/list', { params:{ page:1, size:1, functionCode } })` 返回 `total > 0`

- [ ] **Step 1 (Red)**：`frontend/tests/store/transformWizard.spec.js`：
  ```js
  import { setActivePinia, createPinia } from 'pinia'
  import { useTransformWizardStore } from '@/store/transformWizard'

  beforeEach(() => { setActivePinia(createPinia()); localStorage.clear() })

  test('reset 后 state 回默认且 localStorage 被清除', () => {
    const s = useTransformWizardStore()
    s.functionCode = 'X'; s.persist()
    expect(localStorage.getItem('transform_wizard_draft')).toBeTruthy()
    s.reset()
    expect(s.functionCode).toBe('')
    expect(localStorage.getItem('transform_wizard_draft')).toBeNull()
  })

  test('persist 后 loadDraft 状态一致（排除运行时字段）', () => {
    const s = useTransformWizardStore()
    s.functionCode = 'FC_01'; s.testOutput = 'shouldNotPersist'
    s.persist()
    s.reset()
    s.loadDraft()
    expect(s.functionCode).toBe('FC_01')
    expect(s.testOutput).toBe('')  // 被排除
  })
  ```
  fail（模块不存在）。

- [ ] **Step 2 (Green)**：`frontend/src/store/transformWizard.js`（照搬 `wizard.js` 的 `_skipNextPersist` 模式）：
  ```js
  import { defineStore } from 'pinia'
  const DRAFT_KEY = 'transform_wizard_draft'
  function defaultState() {
    return {
      _skipNextPersist: false,
      currentStep: 0,
      // Step 1 · 选择系统
      sourceChannelCode: '', sourceChannelName: '',
      targetChannelCode: '', targetChannelName: '',
      sourceFormat: 'JSON', targetFormat: 'JSON',
      complexity: 'BODY_FIELDS',
      // Step 2 · 功能号
      functionCode: '', functionName: '', messageCategory: '',
      // Step 3 · 端口
      portAddress: '', portMethod: 'POST', timeout: 3000, retryCount: 3,
      // Step 4 · 路由绑定
      headerConfig: { contentType: 'application/json', charset: 'UTF-8', requestHeaders: {}, responseHeaders: {} },
      savedPortRouteId: null,
      // Step 5 · 模板
      templateMode: 'EXISTING',
      savedTemplateId: null,
      newTemplateDraft: { name: '', srcFmt: '', targetFmt: '', mappingRules: [], processRules: [] },
      // Step 6 · 测试
      testInput: '', testMode: 'SIMULATE', testOutput: '', testError: '',
    }
  }
  export const useTransformWizardStore = defineStore('transformWizard', {
    state: defaultState,
    actions: {
      reset() { this._skipNextPersist = true; this.$patch(defaultState()); localStorage.removeItem(DRAFT_KEY) },
      persist() {
        if (this._skipNextPersist) { this._skipNextPersist = false; return }
        try {
          const { testOutput, testError, _skipNextPersist, ...rest } = this.$state
          localStorage.setItem(DRAFT_KEY, JSON.stringify(rest))
        } catch (e) { console.warn('[transformWizardStore] persist failed:', e) }
      },
      hasDraft() { return !!localStorage.getItem(DRAFT_KEY) },
      loadDraft() {
        try {
          const raw = localStorage.getItem(DRAFT_KEY)
          if (!raw) return false
          this.$patch(JSON.parse(raw))
          return true
        } catch { return false }
      },
    },
  })
  ```

  `frontend/src/api/functionCode.js`：
  ```js
  import request from '@/api/request'
  export async function checkFunctionCodeExists(functionCode) {
    const res = await request.get('/port-route/list', { params: { page: 1, size: 1, functionCode } })
    return (res && res.total > 0)
  }
  ```

  `npm run test` → 2 用例绿。

- [ ] **Step 3 (Refactor)**：无实质重构，跑 lint 检查风格与既有 `wizard.js` 一致。

- [ ] **Step 4 (Commit)**：
  ```bash
  git add frontend/src/store/transformWizard.js frontend/src/api/functionCode.js frontend/tests/store/transformWizard.spec.js
  git commit -m "feat(UX-D): add transformWizard store and functionCode existence-check API"
  ```

---

## Task 7: `TransformInterfaceSteps.vue` Step 1-4（选系统 → 功能号 → 端口 → 路由绑定）

**Files:**
- Create: `frontend/src/views/convert/TransformInterfaceSteps.vue`
- Modify: `frontend/src/views/convert/TransformWizard.vue`（占位换成正式 Shell 容器）
- Create: `frontend/tests/views/TransformInterfaceStepsPart1.spec.js`

**Interfaces:**
- Consumes: `WizardShell`（Task 1），`useTransformWizardStore`（Task 6），`checkFunctionCodeExists`（Task 6），`listChannels`（既有 `api/channel.js`），`saveChannel`（既有），`savePortRoute`（既有 `api/portRoute.js`）
- Produces:
  - `TransformInterfaceSteps.vue` 完成 Step 1/2/3/4 四段模板 + 校验逻辑
  - Props：`isActive:Function`
  - `defineExpose({ validateStep, savePortRouteIfNeeded, buildPortRoutePayload })`：
    - `validateStep(key:string):true|string` — Step 1-4 各自校验规则（对应设计 §4.5）
    - `savePortRouteIfNeeded():Promise<void>` — Step 4 → Step 5 前置调用；无 `savedPortRouteId` 时新建，有则更新
    - `buildPortRoutePayload():object` — 组装 `PortRouteSaveRequest`，含 `functionCode`/`functionName`

- [ ] **Step 1 (Red)**：`frontend/tests/views/TransformInterfaceStepsPart1.spec.js`：
  ```js
  test('validateStep(system) 来源/目标渠道均非空且格式非空_返回true', ...)
  test('validateStep(system) 缺来源渠道_返回字符串提示', ...)
  test('validateStep(function) functionCode 5-64位英数下划线_返回true', ...)
  test('validateStep(function) functionCode 含中文_返回错误字符串', ...)
  test('validateStep(port) portAddress 非 http/https 前缀_返回错误', ...)
  test('validateStep(route) headerConfig 结构合法 + 未保存过 savedPortRouteId 时 validateStep 允许前进', ...)
  test('buildPortRoutePayload 包含 functionCode/functionName/headerConfig 各字段', ...)
  ```
  全 fail。

- [ ] **Step 2 (Green)**：新建 `TransformInterfaceSteps.vue`。模板骨架：
  ```vue
  <template>
    <div>
      <!-- Step 1 · 选择系统 -->
      <div v-show="isActive('system')">
        <el-form label-width="120px" style="max-width:720px">
          <el-form-item label="来源系统" required>
            <el-select v-model="s.sourceChannelCode" filterable placeholder="选择来源系统" @change="onSourceChannelChange">
              <el-option v-for="c in channels" :key="c.channelCode" :label="`${c.channelName} · ${c.channelCode}`" :value="c.channelCode" />
            </el-select>
            <el-button link @click="openAddChannel('source')">+ 新增渠道</el-button>
          </el-form-item>
          <el-form-item label="目标系统" required>
            <el-select v-model="s.targetChannelCode" filterable placeholder="选择目标系统" @change="onTargetChannelChange">
              <el-option v-for="c in channels" :key="c.channelCode" :label="`${c.channelName} · ${c.channelCode}`" :value="c.channelCode" />
            </el-select>
            <el-button link @click="openAddChannel('target')">+ 新增渠道</el-button>
          </el-form-item>
          <el-form-item label="来源报文格式" required>
            <el-radio-group v-model="s.sourceFormat">
              <el-radio-button label="JSON" /><el-radio-button label="XML" /><el-radio-button label="CSV" /><el-radio-button label="FormData" />
            </el-radio-group>
          </el-form-item>
          <el-form-item label="目标报文格式" required>
            <el-radio-group v-model="s.targetFormat">
              <el-radio-button label="JSON" /><el-radio-button label="XML" /><el-radio-button label="CSV" /><el-radio-button label="FormData" />
            </el-radio-group>
          </el-form-item>
          <el-form-item label="转换复杂度">
            <el-radio-group v-model="s.complexity">
              <el-radio label="HEADER_ONLY">仅换报文头</el-radio>
              <el-radio label="BODY_FIELDS">换报文体字段</el-radio>
              <el-radio label="FORMAT_AND_PROCESS">换格式+字段+加工</el-radio>
            </el-radio-group>
          </el-form-item>
        </el-form>
        <!-- 新增渠道弹窗 -->
        <el-dialog v-model="addChannelVisible" title="新增渠道" width="480px">
          <el-form label-width="100px">
            <el-form-item label="渠道编码" required><el-input v-model="newChannel.channelCode" /></el-form-item>
            <el-form-item label="渠道名称" required><el-input v-model="newChannel.channelName" /></el-form-item>
            <el-form-item label="识别字段"><el-input v-model="newChannel.identifyField" /></el-form-item>
          </el-form>
          <template #footer>
            <el-button @click="addChannelVisible=false">取消</el-button>
            <el-button type="primary" @click="confirmAddChannel">确定</el-button>
          </template>
        </el-dialog>
      </div>

      <!-- Step 2 · 功能号 -->
      <div v-show="isActive('function')">
        <el-form label-width="140px" style="max-width:720px">
          <el-form-item label="功能号" required>
            <el-input v-model="s.functionCode" placeholder="如 CBS_QUERY_ACCOUNT" @blur="checkFcExists" />
            <div v-if="fcWarning" style="color:#E6A23C;font-size:12px">{{ fcWarning }}</div>
          </el-form-item>
          <el-form-item label="功能号中文名"><el-input v-model="s.functionName" /></el-form-item>
          <el-form-item label="报文类别">
            <el-select v-model="s.messageCategory" placeholder="选择类别">
              <el-option label="查询类" value="QUERY" />
              <el-option label="交易类" value="TRANSACTION" />
              <el-option label="通知类" value="NOTIFY" />
              <el-option label="其他" value="OTHER" />
            </el-select>
          </el-form-item>
        </el-form>
      </div>

      <!-- Step 3 · 端口配置 -->
      <div v-show="isActive('port')">
        <el-form label-width="140px" style="max-width:720px">
          <el-form-item label="端口地址" required><el-input v-model="s.portAddress" placeholder="http(s)://host:port/path" /></el-form-item>
          <el-form-item label="HTTP Method" required>
            <el-select v-model="s.portMethod">
              <el-option label="GET" value="GET" /><el-option label="POST" value="POST" />
              <el-option label="PUT" value="PUT" /><el-option label="DELETE" value="DELETE" />
            </el-select>
          </el-form-item>
          <el-form-item label="超时（ms）"><el-input-number v-model="s.timeout" :min="100" :max="60000" /></el-form-item>
          <el-form-item label="失败重试次数"><el-input-number v-model="s.retryCount" :min="0" :max="10" /></el-form-item>
          <div style="color:#909399;font-size:12px;margin-left:140px">发布后可在 Step 6 端到端测试中验证连通性</div>
        </el-form>
      </div>

      <!-- Step 4 · 端口路由绑定 -->
      <div v-show="isActive('route')">
        <el-descriptions :column="2" border style="margin-bottom:16px">
          <el-descriptions-item label="来源系统">{{ s.sourceChannelName }} · {{ s.sourceChannelCode }}</el-descriptions-item>
          <el-descriptions-item label="目标系统">{{ s.targetChannelName }} · {{ s.targetChannelCode }}</el-descriptions-item>
          <el-descriptions-item label="功能号">{{ s.functionCode }}</el-descriptions-item>
          <el-descriptions-item label="端口地址">{{ s.portAddress }}</el-descriptions-item>
        </el-descriptions>
        <el-form label-width="140px" style="max-width:720px">
          <el-form-item label="Content-Type">
            <el-select v-model="s.headerConfig.contentType">
              <el-option label="application/json" value="application/json" />
              <el-option label="application/xml" value="application/xml" />
              <el-option label="text/plain" value="text/plain" />
              <el-option label="application/x-www-form-urlencoded" value="application/x-www-form-urlencoded" />
            </el-select>
          </el-form-item>
          <el-form-item label="Charset">
            <el-select v-model="s.headerConfig.charset">
              <el-option label="UTF-8" value="UTF-8" /><el-option label="GBK" value="GBK" /><el-option label="ISO-8859-1" value="ISO-8859-1" />
            </el-select>
          </el-form-item>
        </el-form>
        <div v-if="s.savedPortRouteId" style="color:#67C23A;margin-top:8px">已保存路由 ID：{{ s.savedPortRouteId }}（继续将更新此记录）</div>
      </div>
    </div>
  </template>
  ```

  `<script setup>` 要点：
  - `import { useTransformWizardStore } from '@/store/transformWizard'`
  - `import { listChannels, saveChannel } from '@/api/channel'`
  - `import { savePortRoute } from '@/api/portRoute'`
  - `import { checkFunctionCodeExists } from '@/api/functionCode'`
  - `const s = useTransformWizardStore()`
  - `const channels = ref([])`；`onMounted` 加载
  - `onSourceChannelChange(code)` 从 `channels` 找出 name 回填 `s.sourceChannelName`；同理目标
  - `openAddChannel/confirmAddChannel` 使用 `saveChannel` 保存后 push 到 `channels.value`
  - `checkFcExists()` 调 `checkFunctionCodeExists(s.functionCode)`，命中则 `fcWarning.value = '该功能号已被使用，继续将覆盖既有路由'`
  - `validateStep(key)`：
    ```js
    function validateStep(key) {
      if (key === 'system') {
        if (!s.sourceChannelCode) return '请选择来源系统'
        if (!s.targetChannelCode) return '请选择目标系统'
        if (!s.sourceFormat) return '请选择来源报文格式'
        if (!s.targetFormat) return '请选择目标报文格式'
        return true
      }
      if (key === 'function') {
        if (!/^[A-Za-z0-9_]{5,64}$/.test(s.functionCode)) return '功能号需为 5-64 位英文/数字/下划线'
        return true
      }
      if (key === 'port') {
        if (!/^https?:\/\//.test(s.portAddress)) return '端口地址必须以 http:// 或 https:// 开头'
        if (!s.portMethod) return '请选择 HTTP Method'
        if (!(s.timeout > 0)) return '超时必须大于 0'
        if (s.retryCount < 0 || s.retryCount > 10) return '重试次数需在 0-10 之间'
        return true
      }
      if (key === 'route') {
        if (!s.headerConfig?.contentType) return 'Content-Type 不能为空'
        if (!s.headerConfig?.charset) return 'Charset 不能为空'
        return true
      }
      return true
    }
    ```
  - `function buildPortRoutePayload() { return { id: s.savedPortRouteId || undefined, channelCode: s.sourceChannelCode, portAddress: s.portAddress, portMethod: s.portMethod, timeout: s.timeout, retryCount: s.retryCount, functionCode: s.functionCode, functionName: s.functionName, headerConfig: s.headerConfig } }`
  - `async function savePortRouteIfNeeded(){ const id = await savePortRoute(buildPortRoutePayload()); s.savedPortRouteId = id }`
  - `defineExpose({ validateStep, savePortRouteIfNeeded, buildPortRoutePayload })`

  然后重写 `TransformWizard.vue`（取代 Task 5 的占位）：
  ```vue
  <template>
    <WizardShell
      title="接口转换配置向导"
      :steps="visibleSteps"
      v-model:current-step="s.currentStep"
      :draft-saved="draftSaved"
      :validate-next="onValidateNext"
      submit-label="保存并启用"
      @submit="onSubmit"
      @back="goList"
    >
      <template #default="{ isActive }">
        <TransformInterfaceSteps ref="stepsRef" :is-active="isActive" />
      </template>
    </WizardShell>
  </template>

  <script setup>
  import { ref, onMounted } from 'vue'
  import { useRouter } from 'vue-router'
  import { ElMessage } from 'element-plus'
  import WizardShell from '@/components/wizard/WizardShell.vue'
  import TransformInterfaceSteps from '@/views/convert/TransformInterfaceSteps.vue'
  import { useTransformWizardStore } from '@/store/transformWizard'
  import { useDraft } from '@/composables/useWizardShell'

  const router = useRouter()
  const s = useTransformWizardStore()
  const stepsRef = ref(null)
  const { draftSaved, promptRestoreDraft } = useDraft(s)

  const visibleSteps = [
    { key: 'system',   label: '选择系统',   tip: '定义来源系统 → 目标系统与报文格式' },
    { key: 'function', label: '功能号',     tip: '给这次转换指定一个稳定业务标识' },
    { key: 'port',     label: '端口配置',   tip: '目标系统物理接入点' },
    { key: 'route',    label: '路由绑定',   tip: '把渠道+功能号+端口绑定成一条 port_route' },
    { key: 'template', label: '转换模板',   tip: '选或新建报文转换规则（映射+加工）' },
    { key: 'test',     label: '测试',       tip: '端到端跑通转换链路' },
    { key: 'publish',  label: '发布',       tip: '汇总回显 + 一键启用' },
  ]

  async function onValidateNext(key) {
    if (!stepsRef.value) return true
    const res = stepsRef.value.validateStep(key)
    if (res !== true) return res
    // 离开 route 前保存路由
    if (key === 'route') {
      try { await stepsRef.value.savePortRouteIfNeeded() }
      catch { return '路由保存失败，请重试' }
    }
    return true
  }
  async function onSubmit() {
    if (stepsRef.value?.onSubmit) await stepsRef.value.onSubmit()
    else { s.reset(); router.push('/convert/port-route') }
    ElMessage.success('转换配置已启用')
  }
  function goList() { router.push('/convert/port-route') }

  onMounted(async () => { if (!s.functionCode) await promptRestoreDraft() })
  </script>
  ```

  `npm run test` → Step 1 的 7 用例绿。

- [ ] **Step 3 (Manual · 冒烟 Step 1-4)**：`npm run dev`：
  - `/convert/wizard` 页面显示 7 步骤条
  - Step 1 选来源/目标渠道，格式选 JSON / JSON
  - Step 2 输入 `TESTFC_01`，输入非法值 `abc-` → 点下一步弹 warning
  - Step 3 输入 `http://localhost:9999/mock`
  - Step 4 点"下一步" → Console 无报错，`transform_wizard_draft` 中 `savedPortRouteId` 有值；MySQL `port_route` 表出现新记录且 `function_code = 'TESTFC_01'`

- [ ] **Step 4 (Commit)**：
  ```bash
  git add frontend/src/views/convert/TransformInterfaceSteps.vue frontend/src/views/convert/TransformWizard.vue frontend/tests/views/TransformInterfaceStepsPart1.spec.js
  git commit -m "feat(UX-D): implement transform wizard steps 1-4 (system/function/port/route)"
  ```

---

## Task 8: `TransformInterfaceSteps.vue` Step 5-7（模板 → 测试 → 发布）

**Files:**
- Modify: `frontend/src/views/convert/TransformInterfaceSteps.vue`
- Modify: `frontend/src/api/template.js`（补 `listTemplatesByFunctionCode`）
- Modify: `frontend/src/api/portRoute.js`（`savePortRoute()` 已能透传 `functionCode`，本次仅补 JSDoc）
- Create: `frontend/tests/views/TransformInterfaceStepsPart2.spec.js`

**Interfaces:**
- Consumes: Task 7 骨架 + `saveTemplate` / `listTemplates`（既有），`request.post('/convert', ...)` / `request.post('/dispatch', ...)`
- Produces:
  - Step 5 分支 A/B 完整实现（选既有 / 新建模板）+ 保存后回更 `port_route.request_template_id`
  - Step 6 SIMULATE / LIVE 双模式测试
  - Step 7 汇总回显 + `onSubmit()` 走 store reset + 跳 `/convert/port-route`
  - `defineExpose` 追加 `onSubmit`

- [ ] **Step 1 (Red)**：`frontend/tests/views/TransformInterfaceStepsPart2.spec.js`：
  ```js
  test('Step 5 templateMode=EXISTING 且 savedTemplateId 为空_validateStep 返回错误', ...)
  test('Step 5 templateMode=NEW 且 newTemplateDraft.name 为空_validateStep 返回错误', ...)
  test('Step 6 未执行过测试_validateStep 返回错误', ...)
  test('Step 6 执行过_validateStep 返回 true（testOutput 或 testError 非空）', ...)
  test('onSubmit 调 store.reset 且 push /convert/port-route', ...)
  ```
  fail。

- [ ] **Step 2 (Green)**：向 `TransformInterfaceSteps.vue` 追加 Step 5/6/7 的 `<div v-show>` 段与对应函数。

  Step 5 模板（要点）：
  ```vue
  <div v-show="isActive('template')">
    <el-radio-group v-model="s.templateMode">
      <el-radio-button label="EXISTING">选择已有模板</el-radio-button>
      <el-radio-button label="NEW">新建模板</el-radio-button>
    </el-radio-group>
    <div v-if="s.templateMode === 'EXISTING'" style="margin-top:16px">
      <el-select v-model="s.savedTemplateId" placeholder="选择模板" filterable style="width:360px" @change="onExistingTemplatePicked">
        <el-option v-for="t in fcTemplates" :key="t.id" :label="t.name" :value="t.id" />
      </el-select>
      <div v-if="fcTemplates.length === 0" style="color:#E6A23C;margin-top:8px">该功能号暂无模板，请切换到"新建"</div>
    </div>
    <div v-else style="margin-top:16px">
      <el-form label-width="120px" style="max-width:720px">
        <el-form-item label="模板名" required><el-input v-model="s.newTemplateDraft.name" /></el-form-item>
        <el-form-item label="字段映射">
          <el-table :data="s.newTemplateDraft.mappingRules" border size="small">
            <el-table-column label="源字段"><template #default="{row}"><el-input v-model="row.src" size="small" /></template></el-table-column>
            <el-table-column label="目标字段"><template #default="{row}"><el-input v-model="row.target" size="small" /></template></el-table-column>
            <el-table-column label="加工规则" width="140"><template #default="{row}">
              <el-select v-model="row.process" size="small" clearable>
                <el-option label="TRIM" value="TRIM" /><el-option label="UPPER" value="UPPER" />
                <el-option label="LOWER" value="LOWER" /><el-option label="SUBSTRING" value="SUBSTRING" /><el-option label="PAD" value="PAD" />
              </el-select>
            </template></el-table-column>
            <el-table-column label="操作" width="70"><template #default="{$index}">
              <el-button link type="danger" @click="s.newTemplateDraft.mappingRules.splice($index,1)">删除</el-button>
            </template></el-table-column>
          </el-table>
          <el-button size="small" style="margin-top:6px" @click="s.newTemplateDraft.mappingRules.push({src:'',target:'',process:''})">+ 添加映射</el-button>
        </el-form-item>
        <el-form-item>
          <el-link type="primary" href="/convert/template" target="_blank">需要更复杂映射？跳转完整字段映射页配置 →</el-link>
        </el-form-item>
      </el-form>
    </div>
  </div>
  ```

  Step 6 模板（要点）：
  ```vue
  <div v-show="isActive('test')">
    <el-form label-width="120px">
      <el-form-item label="测试模式">
        <el-radio-group v-model="s.testMode">
          <el-radio label="SIMULATE">模拟调用（/api/convert）</el-radio>
          <el-radio label="LIVE">实际调用（/api/dispatch）</el-radio>
        </el-radio-group>
      </el-form-item>
      <el-form-item label="输入报文">
        <el-input type="textarea" v-model="s.testInput" :rows="8" />
        <el-button size="small" style="margin-top:6px" @click="fillExample">填充示例</el-button>
      </el-form-item>
    </el-form>
    <el-button type="primary" :loading="testing" @click="runTest">执行测试</el-button>
    <div v-if="s.testOutput" style="margin-top:16px">
      <p style="font-weight:500">转换/应答报文：</p>
      <el-input type="textarea" :model-value="s.testOutput" :rows="10" readonly />
    </div>
    <el-alert v-if="s.testError" :title="s.testError" type="error" style="margin-top:12px" />
    <el-link type="primary" style="margin-top:12px;display:block" @click="skipTestToPublish">跳过测试直接发布</el-link>
  </div>
  ```

  Step 7 模板：
  ```vue
  <div v-show="isActive('publish')">
    <el-descriptions :column="2" border>
      <el-descriptions-item label="来源→目标">{{ s.sourceChannelCode }} → {{ s.targetChannelCode }}</el-descriptions-item>
      <el-descriptions-item label="报文格式">{{ s.sourceFormat }} → {{ s.targetFormat }}</el-descriptions-item>
      <el-descriptions-item label="功能号">{{ s.functionCode }} · {{ s.functionName || '—' }}</el-descriptions-item>
      <el-descriptions-item label="端口">{{ s.portMethod }} {{ s.portAddress }}</el-descriptions-item>
      <el-descriptions-item label="路由 ID">{{ s.savedPortRouteId || '—' }}</el-descriptions-item>
      <el-descriptions-item label="模板 ID">{{ s.savedTemplateId || '—' }}</el-descriptions-item>
    </el-descriptions>
    <div style="color:#909399;margin-top:12px">点击"保存并启用"将回到端口路由列表，可以看到刚才配置的记录。</div>
  </div>
  ```

  script 追加：
  ```js
  import { saveTemplate, listTemplates } from '@/api/template'
  import request from '@/api/request'

  const fcTemplates = ref([])
  const testing = ref(false)

  watch(() => s.functionCode, async fc => {
    if (fc && s.templateMode === 'EXISTING') {
      const res = await listTemplates({ page: 1, size: 100, functionCode: fc })
      fcTemplates.value = res?.records || []
    }
  })

  function onExistingTemplatePicked(id) { s.savedTemplateId = id; syncTemplateToRoute() }

  async function syncTemplateToRoute() {
    if (!s.savedPortRouteId || !s.savedTemplateId) return
    await savePortRoute({
      id: s.savedPortRouteId, channelCode: s.sourceChannelCode, portAddress: s.portAddress,
      portMethod: s.portMethod, timeout: s.timeout, retryCount: s.retryCount,
      functionCode: s.functionCode, functionName: s.functionName,
      headerConfig: s.headerConfig, requestTemplateId: s.savedTemplateId
    })
  }

  async function saveNewTemplateAndBind() {
    const tpl = await saveTemplate({
      name: s.newTemplateDraft.name || `${s.functionCode}_TPL_${new Date().toISOString().slice(0,10).replace(/-/g,'')}`,
      srcFormat: s.sourceFormat, targetFormat: s.targetFormat,
      mappingRule: JSON.stringify(s.newTemplateDraft.mappingRules),
      functionCode: s.functionCode
    })
    s.savedTemplateId = tpl
    await syncTemplateToRoute()
  }

  function fillExample() {
    if (s.newTemplateDraft.mappingRules.length) {
      const obj = {}
      for (const r of s.newTemplateDraft.mappingRules) obj[r.src] = 'example'
      s.testInput = JSON.stringify(obj, null, 2)
    }
  }

  async function runTest() {
    testing.value = true; s.testOutput = ''; s.testError = ''
    try {
      const payload = { message: s.testInput, srcFormat: s.sourceFormat, targetFormat: s.targetFormat, templateId: s.savedTemplateId }
      const res = s.testMode === 'SIMULATE'
        ? await request.post('/convert', payload)
        : await request.post('/dispatch', { channelCode: s.sourceChannelCode, message: s.testInput })
      s.testOutput = typeof res === 'string' ? res : JSON.stringify(res, null, 2)
    } catch (e) {
      s.testError = e?.message || '测试失败'
    } finally { testing.value = false }
  }

  function skipTestToPublish() { s.testOutput = '(已跳过测试)' }

  // 扩展 validateStep：template/test/publish 分支
  function validateStep(key) {
    // ...前面 Task 7 的四段保留...
    if (key === 'template') {
      if (s.templateMode === 'EXISTING' && !s.savedTemplateId) return '请选择模板或切换到"新建"'
      if (s.templateMode === 'NEW') {
        if (!s.newTemplateDraft.name?.trim() && !s.functionCode) return '请填写模板名'
      }
      return true
    }
    if (key === 'test') {
      if (!s.testOutput && !s.testError) return '请至少执行一次测试或点"跳过测试直接发布"'
      return true
    }
    return true
  }

  async function onSubmit() {
    // Step 5 若是 NEW 且未保存 → 保存
    if (s.templateMode === 'NEW' && !s.savedTemplateId) await saveNewTemplateAndBind()
    s.reset()
    router.push('/convert/port-route')
  }

  defineExpose({ validateStep, savePortRouteIfNeeded, buildPortRoutePayload, onSubmit })
  ```

  同时在 `frontend/src/api/template.js` 追加：
  ```js
  export function listTemplatesByFunctionCode(functionCode) {
    return request.get('/template/list', { params: { page: 1, size: 100, functionCode } })
  }
  ```

  `npm run test` → 5 用例绿。

- [ ] **Step 3 (Manual · 端到端冒烟)**：`npm run dev`：
  - `/convert/wizard` 完整走完 7 步，功能号用 `E2E_UXD_01`
  - Step 5 切"新建"，加 1 条 `src=userId → target=user_id`
  - Step 6 SIMULATE 模式跑一次 → `testOutput` 非空
  - Step 7 点"保存并启用" → 跳 `/convert/port-route`，列表出现新记录，`function_code = E2E_UXD_01`

- [ ] **Step 4 (Commit)**：
  ```bash
  git add frontend/src/views/convert/TransformInterfaceSteps.vue frontend/src/api/template.js frontend/src/api/portRoute.js frontend/tests/views/TransformInterfaceStepsPart2.spec.js
  git commit -m "feat(UX-D): implement transform wizard steps 5-7 (template/test/publish)"
  ```

---

## Task 9: 6 CRUD 页面同步 `functionCode` 输入 + 显示

**Files:**
- Modify: `frontend/src/views/convert/PortRoute.vue`
- Modify: `frontend/src/views/convert/TemplateList.vue`

**Interfaces:**
- Consumes: Task 4 后端字段
- Produces: 两个 CRUD 列表页可显示与编辑 `functionCode`；老记录（NULL）显示为 "—" 且不阻塞编辑

- [ ] **Step 1**：`PortRoute.vue`：
  - `el-table` 追加 `<el-table-column prop="functionCode" label="功能号" width="160" show-overflow-tooltip><template #default="{row}">{{ row.functionCode || '—' }}</template></el-table-column>`
  - 编辑弹窗 `el-form` 增：
    ```html
    <el-form-item label="功能号"><el-input v-model="form.functionCode" placeholder="可选，如 CBS_QUERY_ACCOUNT" /></el-form-item>
    <el-form-item label="功能号中文名"><el-input v-model="form.functionName" placeholder="可选" /></el-form-item>
    ```
  - `form` 初始对象加两个字段 default 空字符串；保存时 axios 自动透传

- [ ] **Step 2**：`TemplateList.vue` 同理：
  - 列 `functionCode`
  - 编辑弹窗加 `<el-form-item label="功能号"><el-input v-model="form.functionCode" placeholder="可选" /></el-form-item>`

- [ ] **Step 3 (Manual · 冒烟)**：`npm run dev`：
  - `/convert/port-route` 列表新增列显示，向导创建的记录有值，老记录显示 "—"
  - 点编辑打开弹窗，输入功能号后保存 → 列表刷新可见
  - `/convert/template` 同样验证

- [ ] **Step 4 (Commit)**：
  ```bash
  git add frontend/src/views/convert/PortRoute.vue frontend/src/views/convert/TemplateList.vue
  git commit -m "feat(UX-D): expose functionCode in port_route and template CRUD pages"
  ```

---

## Task 10: 端到端冒烟（UX-D-E01 ～ E07）

**Files:**
- Modify: `docs/04-测试/UX-D-regression-log.md`（Task 3 已建，追加"转换向导 E01-E07"节）

**Interfaces:** 无代码改动

- [ ] **Step 1**：启动 backend + frontend + pg-testkit（可选，用于 LIVE 测试）：
  - `backend/` `mvn spring-boot:run`
  - `frontend/` `npm run dev`
  - `pg-testkit/` `mvn spring-boot:run`（端口 8081，Mock 9999）

- [ ] **Step 2 · UX-D-E01**：admin 登录，走完 7 步（`E2E_UXD_01`、JSON→JSON、简单映射），Step 4 后 `port_route` 表可见 `function_code=E2E_UXD_01`，Step 6 SIMULATE 返回预期，Step 7 跳 `/convert/port-route` 可见新记录。

- [ ] **Step 3 · UX-D-E02**：Step 1 弹窗新增渠道 `E2E_CH_X` → 立即出现在下拉，无需刷新。

- [ ] **Step 4 · UX-D-E03**：Step 2 输入 `E2E_UXD_01`（E01 已建）→ blur 触发 fcWarning 黄字警告；点下一步能继续。

- [ ] **Step 5 · UX-D-E04**：先手工在 `/convert/template` 建一个 `functionCode=OTHER_FC` 的模板 → 向导 Step 5 使用 `E2E_UXD_01` 时该模板不出现。

- [ ] **Step 6 · UX-D-E05**：Step 6 LIVE 模式指向不存在端口（如 `http://localhost:65535`）→ `testError` 显示错误 → "跳过测试直接发布"链接可用 → Step 7 仍能保存。

- [ ] **Step 7 · UX-D-E06**：Step 4 保存后回退 Step 3 改端口地址为 `http://newhost/mock` → 再前进 Step 4 → `port_route` 记录被 UPDATE 而非新增（`id` 不变）。

- [ ] **Step 8 · UX-D-E07**：走到 Step 5 后关浏览器 Tab → 重开 `/convert/wizard` → 恢复提示 → 恢复后从 Step 5 继续，所有前 4 步字段完整还原。

- [ ] **Step 9 · 权限 P01-P04**：
  - admin/user 均能访问 `/convert/wizard`
  - readonly 手输 URL 被踢回 `/dashboard`
  - readonly 菜单无"接口转换配置向导"

- [ ] **Step 10 (Log + Commit)**：
  ```bash
  git add docs/04-测试/UX-D-regression-log.md
  git commit -m "test(UX-D): E2E smoke and permission verification (E01-E07 + P01-P04)"
  ```

---

## Task 11: 文档三步齐 + CHG-019 + 问题清单搬迁

**Files:**
- Modify: `docs/03-开发/变更记录.md`
- Modify: `docs/03-开发/问题清单.md`
- Modify: `docs/03-开发/开发计划.md`
- Modify: `docs/01-需求/需求拆分与最小实现方案.md`

**Interfaces:** 无代码

- [ ] **Step 1 · CHG-019**：在 `变更记录.md` `CHG-015` 之后按时间顺序追加：
  ```markdown
  ### CHG-019 2026-07-19 UX-D 接口转换配置流程重构 + 转换向导

  - **日期**：2026-07-19
  - **影响单元**：新增阶段六 UX-D；不改动 M1-1 ～ M1-7 对外行为；SYS-5 InterfaceWizard 由单文件重构为 WizardShell + SelectInterfaceSteps 组合，行为完全等价
  - **变更类型**：功能新增（FN-04 流程重构 + FN-05 转换向导）+ 架构收敛（抽 WizardShell）
  - **变更前**：接口转换配置六个页面平铺，无引导流程；`/convert/wizard` 不存在
  - **变更后**：
    - 新增 `WizardShell.vue` + `useWizardShell` composable，两个向导共用骨架
    - 新增 `TransformInterfaceSteps.vue` 7 步向导：系统 → 功能号 → 端口 → 路由 → 模板 → 测试 → 发布
    - 原 `InterfaceWizard.vue` 迁到 `SelectInterfaceSteps.vue`（行为不变）
    - `port_route` 新增 `function_code`(64)/`function_name`(128) 可空字段
    - `convert_template` 新增 `function_code`(64) 可空字段
    - `PortRouteService.listRoutes` / `TemplateService.listTemplates` 支持 `functionCode` 过滤
    - `MenuPermission` ADMIN/USER 白名单加 `/convert/wizard`
  - **原因**：用户反馈接口转换配置流程割裂（111.txt #12），需要仿 SYS-5 提供向导（111.txt #13）
  - **验收**：
    - 原 `/interface/wizard` SELECT/INSERT/UPDATE/DELETE 全流程回归通过（Task 3）
    - 新 `/convert/wizard` E01-E07 全通过（Task 10）
    - 权限 P01-P04 全通过
    - `mvn test` 全绿（+5 新用例）
  - **保护点**：
    - `WizardShell.currentStepDef` 默认对象兜底（延续 CHG-011 E2E-6）
    - `SelectInterfaceSteps.vue` 保留 `wizard.tables[0]?.tableName` 的 `?.` + `v-if`
    - Task 5 提前处理 `MenuPermission` 白名单（防 CHG-011 E2E-5 重演）
  ```

- [ ] **Step 2 · 问题清单**：把 FN-04、FN-05 两行从"D 组 · 待解决"移动到文件底部"已解决"章节，加日期 `2026-07-19` 与 CHG-019 引用。

- [ ] **Step 3 · 开发计划**：在阶段六表格 UX-D 行填「交付日期 2026-07-19、状态 已完成、对应 CHG CHG-019」。

- [ ] **Step 4 · 需求拆分**：在阶段六 UX-D 单元描述节写入本次定稿的范围/不含/实现方案/验收标准（照搬 spec §2、§9）。

- [ ] **Step 5 (Commit)**：
  ```bash
  git add docs/03-开发/变更记录.md docs/03-开发/问题清单.md docs/03-开发/开发计划.md docs/01-需求/需求拆分与最小实现方案.md
  git commit -m "docs(UX-D): add CHG-019 and move FN-04/FN-05 to resolved"
  ```

---

## 关键防退化门槛（Pre-merge Checklist）

在合入 master 之前，必须逐项打勾：

- [ ] `mvn test` 全绿（含新增 5 用例）
- [ ] `frontend/` `npm run test` 全绿
- [ ] Task 3 `/interface/wizard` 回归 7 步全部勾选（`docs/04-测试/UX-D-regression-log.md`）
- [ ] Task 10 UX-D-E01～E07 + P01～P04 全部勾选
- [ ] `MenuPermission.java` ADMIN/USER 白名单含 `/convert/wizard`，READONLY 不含
- [ ] `SelectInterfaceSteps.vue` 仍有 `wizard.tables[0]?.tableName` 的 `?.` 保护 + `v-if`
- [ ] `WizardShell.currentStepDef` 有默认对象兜底
- [ ] `migration-ux-d-function-code.sql` 在开发库幂等执行两次不报错
- [ ] `InterfaceWizard.vue` 行数 ≤ 60（不含空行、注释）
- [ ] CHG-019 已写入 `变更记录.md`，问题清单 FN-04/05 已归入"已解决"

---

## 建议实施顺序

**串行必需**：Task 1 → Task 2 → **Task 3（硬门槛，未通过不得进入 Task 4+）** → Task 5（防 CHG-011 E2E-5）→ Task 6 → Task 7 → Task 8 → Task 9 → Task 10 → Task 11

**可并行**：Task 4（后端字段）与 Task 5 的前端部分可交给两个 subagent 同时开工（互不冲突）。若单人执行，推荐 Task 4 与 Task 6 交叉进行以摊平上下文切换。

**估算**：Task 1（0.5d）+ 2（0.5d）+ 3（0.5d）+ 4（0.5d）+ 5（0.5d）+ 6（0.5d）+ 7（1.5d）+ 8（1.5d）+ 9（0.5d）+ 10（0.5d）+ 11（0.5d）= **约 7.5 人日**，与 spec §10 估算一致。
