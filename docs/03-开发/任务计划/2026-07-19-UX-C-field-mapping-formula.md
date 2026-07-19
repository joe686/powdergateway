# UX-C · 字段映射/加工/公式补齐 · 实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 一次性交付 FN-01/FN-02 两个字段映射与加工页面的 `vue-draggable-next` slot 用法回滚复发修复，并新建"常用字段公式管理"完整后端+前端（FN-03）。

**Architecture:** 前端修 2 个 draggable 用法 bug + 新建 5 个前端文件（页面/递归编辑器/操作数编辑器/公式选择器/API 封装）；后端新增 `field_formula` 全套 Service/Controller/Validator/DTO，走**配置库**默认 master 数据源（无 `@DS` 注解），复用 `TableMetaService` 校验 COLUMN 引用，走 `SysLogRecord` 记录操作日志。

**Tech Stack:** Vue 3, Element Plus, vue-draggable-next（v2.x - 只用 default slot）, Spring Boot 2.7, MyBatis-Plus, JUnit, H2（测试）, Jackson

## Global Constraints

- **对话/注释/提交信息全部中文**（根 CLAUDE.md）
- 前端 HTTP 请求严格通过 `frontend/src/api/request.js`，禁止 axios 直调（frontend CLAUDE.md）
- **vue-draggable-next 只用 default slot + `v-for`（禁 `<template #item>`）**：本单元触及 `FieldMapping.vue` / `FieldProcess.vue`，属高风险区（CHG-003 → 2026-07-19 已回滚两次），修复后必须在文件顶部加规约注释锁定
- 后端 `field_formula` 表位于**配置库**（`powergateway_config`），Service/Mapper 无 `@DS` 注解，默认使用 `master` 数据源
- 数据库表 utf8mb4 + 中文 COMMENT（CHG-011 OBS-1 已知乱码问题不重新引入）
- 所有测试类必须加 `@ActiveProfiles("test")`（根 CLAUDE.md 强制）
- TDD Red → Green → Refactor 三步走，先写测试运行确认失败，再最小实现，再重构
- 完成后追加 **CHG-017**（对应 UX-C 详细设计文档 §一 已锁定编号）到 `docs/03-开发/变更记录.md`；`问题清单.md` FN-01/02/03 三项从"待解决"移动到"已解决"
- 用户任务描述中提到 CHG-018 系口误，本单元统一使用 spec 中定义的 **CHG-017**（若最终交付前 CHG-016 已被其它 UX 单元占用，需在 spec 与本计划共同确认新编号后再落地）

---

## 文件地图

| 操作 | 文件 |
|------|------|
| Modify | `frontend/src/views/convert/FieldMapping.vue`（FN-01：两处 `<draggable>` 改 default slot + 顶部规约注释） |
| Modify | `frontend/src/views/convert/FieldProcess.vue`（FN-02：一处 `<draggable>` 改 default slot + 顶部规约注释） |
| Modify | `backend/src/main/java/com/powergateway/model/FieldFormula.java`（补 `updateTime` 字段 + `@TableField(fill=INSERT_UPDATE)`） |
| Create | `backend/src/main/resources/db/migration-field-formula.sql`（幂等升级脚本：加 `update_time` 列 + 3 个索引 + 表注释） |
| Create | `backend/src/main/java/com/powergateway/model/dto/FieldFormulaDto.java` |
| Create | `backend/src/main/java/com/powergateway/model/dto/FormulaJson.java` |
| Create | `backend/src/main/java/com/powergateway/model/dto/FormulaOperand.java` |
| Create | `backend/src/main/java/com/powergateway/model/dto/FormulaSaveRequest.java` |
| Create | `backend/src/main/java/com/powergateway/model/dto/FormulaValidateRequest.java` |
| Create | `backend/src/main/java/com/powergateway/model/dto/FormulaValidateResult.java` |
| Create | `backend/src/main/java/com/powergateway/service/FormulaValidator.java` |
| Create | `backend/src/main/java/com/powergateway/service/FieldFormulaService.java` |
| Create | `backend/src/main/java/com/powergateway/controller/FieldFormulaController.java` |
| Create | `backend/src/test/java/com/powergateway/UXC01FormulaValidatorTest.java` |
| Create | `backend/src/test/java/com/powergateway/UXC02FieldFormulaServiceTest.java` |
| Create | `backend/src/test/java/com/powergateway/UXC03FieldFormulaControllerTest.java` |
| Create | `frontend/src/api/fieldFormula.js` |
| Create | `frontend/src/components/formula/OperandInput.vue` |
| Create | `frontend/src/components/formula/FormulaBuilder.vue` |
| Create | `frontend/src/components/formula/FormulaPicker.vue` |
| Create | `frontend/src/views/interface/FieldFormula.vue`（替换现有 PlaceholderView） |
| Modify | `frontend/src/router/index.js`（第 121~125 行现有 `/interface/formula` route 块的 component 指向新组件） |
| Modify | `docs/03-开发/变更记录.md`（追加 CHG-017 完整条目） |
| Modify | `docs/03-开发/问题清单.md`（FN-01/FN-02/FN-03 移入已解决） |
| Modify | `docs/03-开发/开发计划.md`（阶段六 UX-C 行状态改"已完成"） |
| Modify | `docs/01-需求/需求拆分与最小实现方案.md`（追加"字段公式管理"节） |

**MenuPermission.java 无需改**：`/interface/formula` 已存在于 `ADMIN_MENUS` 和 `USER_MENUS`（详见根 `backend/src/main/java/com/powergateway/config/MenuPermission.java` 第 20/31 行）。

---

## Task 1: FN-01 修复 FieldMapping.vue draggable slot 用法回滚

**Files:**
- Modify: `frontend/src/views/convert/FieldMapping.vue`（两处 `<draggable>` 位于第 54~72 行和第 105~168 行）

**Interfaces:**
- Consumes: `vue-draggable-next` 的 `VueDraggableNext` 组件，仅支持 default slot；`srcFields`/`mappingRules` reactive ref
- Produces: 修复后源字段块正确渲染，`onDragAdd(evt)` 保持原签名与语义（evt.newIndex 索引处替换 mappingRules 元素）；`removeSrcField(item)`/`removeMappingRow(index)`/`onUseFixedChange(rule)` 均保持原签名

- [ ] **Step 1: 写手工回归测试用例清单（充当验收断言）**

前端本项目当前无 Vitest/Jest 基础设施（历史所有 UX 前端修复均采用手工回归，参见 CHG-011），本任务遵循同一模式：在 spec §6.1 中的 7 项手工用例（FN-01-T1 ~ FN-01-T7）作为 Red/Green 判据。

在开始改代码前，先跑一遍现状用例，确认 FN-01-T1 失败（左侧源字段区一片空白）：

```bash
# 启动后端与前端
cd D:/Project/powergateway/backend && mvn spring-boot:run &
cd D:/Project/powergateway/frontend && npm install && npm run dev
```

浏览器访问 `http://localhost:5173/convert/field-mapping`，登录 `admin`/`Admin@123`，点"手动添加"输入 `orderId`，观察左侧源字段区。

Expected（Red 状态）：左侧源字段区无 `orderId` 块出现，`el-empty` 消失但没有替代内容（回滚复发状态）。

- [ ] **Step 2: 运行确认失败**

按 Step 1 的浏览器操作复现现状，将复现结果记录为 Red 基线：截图或文字确认"源字段区无渲染元素"。

Expected: FN-01-T1 手工用例失败。

- [ ] **Step 3: 最小实现（改两处 draggable + 加规约注释）**

替换 `frontend/src/views/convert/FieldMapping.vue` 中：

3.1 在 `<script setup>` 上方（约现有第 265 行 `<script setup>` 之前 或 `</template>` 之后）文件层级插入规约注释（如果放 template 上方受 SFC 语法限制，改放 template 内部第一个 draggable 上方，同 CHG-003 处理方式）：

```vue
<!--
  vue-draggable-next v2.x 强制约束（详见 frontend/CLAUDE.md）：
  必须使用 default slot + v-for，禁止 <template #item>。
  #item 是 vuedraggable v4 API，在 vue-draggable-next 中被忽略，
  会导致列表 DOM 渲染为空。
  历史事故：CHG-003 (2026-03-27) 首次修复 → 2026-07-19 FN-01 回滚复发 → CHG-017 二次修复并加此注释锁定。
-->
```

3.2 将第 54~72 行"源字段面板"整块替换为：

```vue
<draggable
  v-model="srcFields"
  :group="{ name: 'srcGroup', pull: 'clone', put: false }"
  :sort="false"
  item-key="id"
  class="src-field-list"
>
  <div v-for="element in srcFields" :key="element.id" class="field-tag">
    <el-icon class="drag-icon"><Grid /></el-icon>
    <span class="field-name">{{ element.name }}</span>
    <el-button
      link size="small" type="danger"
      class="remove-btn"
      @click.stop="removeSrcField(element)"
    ><el-icon><Close /></el-icon></el-button>
  </div>
</draggable>
```

3.3 将第 105~168 行"映射规则列表"整块替换为：

```vue
<draggable
  v-model="mappingRules"
  :group="{ name: 'srcGroup', pull: false, put: true }"
  item-key="id"
  handle=".row-drag-handle"
  @add="onDragAdd"
  class="mapping-rule-list"
>
  <div
    v-for="(element, index) in mappingRules"
    :key="element.id"
    class="mapping-row"
  >
    <el-icon class="row-drag-handle col-drag"><Rank /></el-icon>

    <div class="col-target">
      <el-input v-model="element.targetField" placeholder="目标字段名" size="small" />
    </div>

    <div class="col-src">
      <el-select
        v-model="element.srcField"
        placeholder="选择源字段"
        size="small"
        clearable
        :disabled="element.useFixed"
        style="width:100%"
      >
        <el-option
          v-for="sf in srcFields"
          :key="sf.name"
          :label="sf.name"
          :value="sf.name"
        />
      </el-select>
    </div>

    <div class="col-fixed">
      <el-checkbox
        v-model="element.useFixed"
        size="small"
        @change="onUseFixedChange(element)"
      >固定值</el-checkbox>
      <el-input
        v-if="element.useFixed"
        v-model="element.fixedValue"
        placeholder="填入固定值"
        size="small"
        class="fixed-input"
      />
    </div>

    <div class="col-op">
      <el-popconfirm
        title="确认删除这条映射规则？"
        @confirm="removeMappingRow(index)"
      >
        <template #reference>
          <el-button type="danger" link size="small">
            <el-icon><Delete /></el-icon>
          </el-button>
        </template>
      </el-popconfirm>
    </div>
  </div>
</draggable>
```

3.4 **防退化断言**（关键）：不改动 `<script setup>` 中的 `loadTemplate()` 内 `fixedValue: r.fixedValue ?? null`（CHG-005 F-4）与 `catch (_) { ElMessage.warning('模板映射规则解析失败，已清空规则，请重新配置') }`（CHG-005 F-7）。这两行必须保留原样，避免合并冲突退化。

- [ ] **Step 4: 运行确认通过（手工回归 7 项）**

浏览器操作按 spec §6.1 表格全跑一遍：

| 编号 | 步骤 | Expected |
|------|-----|----------|
| FN-01-T1 | 手工添加 orderId | 左侧出现 `orderId` 字段块 |
| FN-01-T2 | 解析报文 `{"a":1,"b":{"c":2}}` | 左侧新增 `a` 和 `b.c` 两个块 |
| FN-01-T3 | 拖 orderId 到右侧 | 右侧多一行映射规则，targetField=orderId、srcField=orderId |
| FN-01-T4 | 点"添加行"填 targetField=foo，检查"来源"下拉 | 下拉可选 orderId / a / b.c |
| FN-01-T5 | 保存后 `?templateId={id}` 重进 | 规则行完整回显 |
| FN-01-T6 | 勾"固定值"→ 清空输入 → 保存 → 重进 | 该行仍是固定值模式，输入框空（CHG-005 F-4 未退化） |
| FN-01-T7 | DB 手改 `mapping_rule` 为 `"{broken"` → 加载页面 | 弹 warning 且规则区空（CHG-005 F-7 未退化） |

Expected: 7 项全部通过；无 Vue console warning；`vue-draggable-next` 不再返回空 DOM。

- [ ] **Step 5: 提交**

```bash
git add frontend/src/views/convert/FieldMapping.vue
git commit -m "fix(FieldMapping): 修复 vue-draggable-next slot 用法导致源字段不显示（FN-01, 复发自 CHG-003, 二次修复 CHG-017）"
```

---

## Task 2: FN-02 修复 FieldProcess.vue draggable slot 用法回滚

**Files:**
- Modify: `frontend/src/views/convert/FieldProcess.vue`（`<draggable>` 位于第 52~181 行）

**Interfaces:**
- Consumes: 同 Task 1，`vue-draggable-next` VueDraggableNext + `rules` reactive ref
- Produces: 修复后规则行正确渲染；`addRule()` 保持原签名（内部仍有 `if (inputValue.value) runPreview()` 保护）；`removeRule(index)` / `onRuleTypeChange(element)` / `runPreview()` 均保持原签名

- [ ] **Step 1: 写手工回归测试用例清单（充当验收断言）**

同 Task 1，前端无自动化基础设施，以 spec §6.2 中 6 项手工用例（FN-02-T1 ~ FN-02-T6）作为 Red/Green 判据。

在改代码前，浏览器访问 `http://localhost:5173/convert/field-process`，点"添加规则"，确认现状为规则行不出现（Red 基线）。

Expected（Red 状态）：点击"添加规则"后无步骤 1 行出现，`el-empty` 仍显示或空白。

- [ ] **Step 2: 运行确认失败**

按 Step 1 复现，记录 FN-02-T1 失败。

Expected: FN-02-T1 手工用例失败。

- [ ] **Step 3: 最小实现（改一处 draggable + 加规约注释）**

3.1 在 `frontend/src/views/convert/FieldProcess.vue` 第 51 行（`<draggable>` 之前）插入注释：

```vue
<!--
  vue-draggable-next v2.x 强制约束（详见 frontend/CLAUDE.md）：
  必须使用 default slot + v-for，禁止 <template #item>。
  #item 是 vuedraggable v4 API，在 vue-draggable-next 中被忽略，
  会导致列表 DOM 渲染为空。
  历史事故：CHG-003 (2026-03-27) 首次修复 → 2026-07-19 FN-02 回滚复发 → CHG-017 二次修复并加此注释锁定。
-->
```

3.2 将第 52~181 行整块 `<draggable>` 替换为：

```vue
<draggable
  v-model="rules"
  item-key="id"
  handle=".drag-handle"
  animation="200"
  @end="runPreview"
>
  <div
    v-for="(element, index) in rules"
    :key="element.id"
    class="rule-item"
  >
    <!-- 拖拽手柄 -->
    <div class="drag-handle">
      <el-icon><Rank /></el-icon>
    </div>

    <!-- 步骤序号 -->
    <div class="rule-step">
      <el-tag size="small" type="info">步骤 {{ index + 1 }}</el-tag>
    </div>

    <!-- 规则标识 -->
    <el-input
      v-model="element.ruleName"
      placeholder="规则标识"
      size="small"
      style="width: 120px; flex-shrink: 0"
    />

    <!-- 规则类型选择 -->
    <el-select
      v-model="element.type"
      placeholder="选择规则类型"
      style="width: 160px"
      @change="onRuleTypeChange(element)"
    >
      <el-option
        v-for="rt in ruleTypes"
        :key="rt.type"
        :label="rt.label"
        :value="rt.type"
      />
    </el-select>

    <!-- 动态参数表单 -->
    <div class="rule-params" v-if="element.type">
      <template v-if="element.type === 'TRIM'">
        <el-select v-model="element.params.mode" style="width: 140px" @change="runPreview">
          <el-option label="首尾去空格" value="BOTH" />
          <el-option label="左侧去空格" value="LEFT" />
          <el-option label="右侧去空格" value="RIGHT" />
          <el-option label="去除所有空格" value="ALL" />
        </el-select>
      </template>

      <template v-else-if="element.type === 'SUBSTRING'">
        <el-input-number v-model="element.params.start" :min="0" placeholder="起始位(0)" style="width: 130px" @change="runPreview" />
        <el-input-number v-model="element.params.length" :min="1" placeholder="截取长度" style="width: 130px; margin-left: 8px" @change="runPreview" />
      </template>

      <template v-else-if="element.type === 'PAD'">
        <el-select v-model="element.params.direction" style="width: 100px" @change="runPreview">
          <el-option label="左补" value="LEFT" />
          <el-option label="右补" value="RIGHT" />
        </el-select>
        <el-input v-model="element.params.char" placeholder="填充字符" maxlength="1" style="width: 80px; margin: 0 8px" @input="runPreview" />
        <el-input-number v-model="element.params.length" :min="1" placeholder="目标长度" style="width: 110px" @change="runPreview" />
      </template>

      <template v-else-if="element.type === 'CASE'">
        <el-select v-model="element.params.mode" style="width: 160px" @change="runPreview">
          <el-option label="全部大写" value="UPPER" />
          <el-option label="全部小写" value="LOWER" />
          <el-option label="首字母大写" value="CAPITALIZE" />
        </el-select>
      </template>

      <template v-else-if="element.type === 'TYPE_CAST'">
        <el-select v-model="element.params.targetType" style="width: 140px" @change="runPreview">
          <el-option label="字符串" value="STRING" />
          <el-option label="整数" value="INTEGER" />
          <el-option label="小数" value="DECIMAL" />
          <el-option label="布尔值" value="BOOLEAN" />
        </el-select>
      </template>
    </div>

    <!-- 该步骤输出预览 -->
    <div class="rule-preview" v-if="previewSteps[index + 1] !== undefined">
      <el-tag size="small" type="success">
        → {{ previewSteps[index + 1]?.output }}
      </el-tag>
    </div>

    <!-- 删除按钮 -->
    <el-popconfirm title="确认删除此规则？" @confirm="removeRule(index)">
      <template #reference>
        <el-button type="danger" :icon="Delete" circle size="small" class="delete-btn" />
      </template>
    </el-popconfirm>
  </div>
</draggable>
```

3.3 **防退化断言**：`<script setup>` 中的 `addRule()` 必须保留 `if (inputValue.value) runPreview()` 判断，`runPreview()` 必须保留 `if (!inputValue.value || rules.value.length === 0)` 保护（CHG-005 问题 4），不能因合并冲突退化为无条件调用。

- [ ] **Step 4: 运行确认通过（手工回归 6 项）**

浏览器操作按 spec §6.2 表格全跑一遍：

| 编号 | 步骤 | Expected |
|------|-----|----------|
| FN-02-T1 | 点"添加规则" | 步骤 1 行出现，类型默认 TRIM，参数默认 BOTH |
| FN-02-T2 | 连续点 3 次"添加规则" | 步骤 1/2/3 三行 |
| FN-02-T3 | 未输入"测试输入值"时点"添加规则" | **不发起** `/api/field-process/preview` 请求（打开 network 面板观察） |
| FN-02-T4 | 输入 `  hello  `，添加 TRIM | 最终输出显示 `hello` |
| FN-02-T5 | 添加 TRIM + CASE(UPPER)，拖拽调整顺序 | 拖拽结束后自动预览，输出正确 |
| FN-02-T6 | 删除某规则 | 剩余规则重新预览，序号 1..N 正确 |

Expected: 6 项全部通过；无 Vue console warning。

- [ ] **Step 5: 提交**

```bash
git add frontend/src/views/convert/FieldProcess.vue
git commit -m "fix(FieldProcess): 修复 vue-draggable-next slot 用法导致规则行不显示（FN-02, 复发自 CHG-003, 二次修复 CHG-017）"
```

---

## Task 3: FN-03 后端 field_formula 实体补齐 + 迁移脚本

**Files:**
- Modify: `backend/src/main/java/com/powergateway/model/FieldFormula.java`
- Create: `backend/src/main/resources/db/migration-field-formula.sql`

**Interfaces:**
- Consumes: MyBatis-Plus `@TableField(fill = FieldFill.INSERT_UPDATE)` + 现有 `MyMetaObjectHandler`（backend/src/main/java/com/powergateway/config/MyMetaObjectHandler.java）
- Produces: `FieldFormula` 实体新增 `updateTime` 字段（LocalDateTime，自动填充）；迁移 SQL 幂等增列 + 3 个索引 + 表 COMMENT

- [ ] **Step 1: 写实体反序列化测试（Red）**

创建 `backend/src/test/java/com/powergateway/UXC00FieldFormulaEntityTest.java`：

```java
package com.powergateway;

import com.powergateway.model.FieldFormula;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.ActiveProfiles;

import java.lang.reflect.Field;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

@ActiveProfiles("test")
class UXC00FieldFormulaEntityTest {

    @Test
    void 实体应包含updateTime字段_并被MyBatisPlus自动填充注解标注() throws NoSuchFieldException {
        Field updateTime = FieldFormula.class.getDeclaredField("updateTime");
        assertEquals(LocalDateTime.class, updateTime.getType(), "updateTime 类型必须为 LocalDateTime");
        com.baomidou.mybatisplus.annotation.TableField tf =
                updateTime.getAnnotation(com.baomidou.mybatisplus.annotation.TableField.class);
        assertNotNull(tf, "updateTime 必须有 @TableField 注解");
        assertEquals(com.baomidou.mybatisplus.annotation.FieldFill.INSERT_UPDATE, tf.fill(),
                "updateTime 必须配置 FieldFill.INSERT_UPDATE");
    }
}
```

- [ ] **Step 2: 运行确认失败**

```bash
cd D:/Project/powergateway/backend
mvn test -Dtest=UXC00FieldFormulaEntityTest
```

Expected: `NoSuchFieldException: updateTime`（当前实体只有 createTime）。

- [ ] **Step 3: 最小实现**

3.1 修改 `backend/src/main/java/com/powergateway/model/FieldFormula.java`，在 `createTime` 后追加：

```java
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
```

3.2 创建 `backend/src/main/resources/db/migration-field-formula.sql`（幂等）：

```sql
-- migration-field-formula.sql (UX-C · FN-03 · CHG-017)
-- 幂等升级 field_formula 表：补 update_time 列 + 3 个索引 + 表/列 COMMENT
-- 执行前提：powergateway_config 库已存在，field_formula 表已由 init.sql 建立

USE powergateway_config;

-- 1. 补 update_time 列（IF NOT EXISTS 通过存储过程实现，避免 MySQL 版本差异）
DROP PROCEDURE IF EXISTS add_update_time_if_missing;
DELIMITER $$
CREATE PROCEDURE add_update_time_if_missing()
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = 'powergateway_config'
          AND TABLE_NAME = 'field_formula'
          AND COLUMN_NAME = 'update_time'
    ) THEN
        ALTER TABLE field_formula
            ADD COLUMN update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
            COMMENT '更新时间（MyBatis-Plus 自动填充）';
    END IF;
END$$
DELIMITER ;
CALL add_update_time_if_missing();
DROP PROCEDURE add_update_time_if_missing;

-- 2. 补索引（IF NOT EXISTS）
DROP PROCEDURE IF EXISTS add_index_if_missing;
DELIMITER $$
CREATE PROCEDURE add_index_if_missing(IN idx_name VARCHAR(64), IN idx_ddl TEXT)
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.STATISTICS
        WHERE TABLE_SCHEMA = 'powergateway_config'
          AND TABLE_NAME = 'field_formula'
          AND INDEX_NAME = idx_name
    ) THEN
        SET @sql = idx_ddl;
        PREPARE stmt FROM @sql;
        EXECUTE stmt;
        DEALLOCATE PREPARE stmt;
    END IF;
END$$
DELIMITER ;

CALL add_index_if_missing('uk_field_formula_name',
    'ALTER TABLE field_formula ADD UNIQUE KEY uk_field_formula_name (name)');
CALL add_index_if_missing('idx_field_formula_scene',
    'ALTER TABLE field_formula ADD KEY idx_field_formula_scene (scene)');
CALL add_index_if_missing('idx_field_formula_db_conn',
    'ALTER TABLE field_formula ADD KEY idx_field_formula_db_conn (db_connection_id)');
DROP PROCEDURE add_index_if_missing;

-- 3. 表注释
ALTER TABLE field_formula COMMENT = '常用字段公式表（UX-C FN-03 / CHG-017）';
```

- [ ] **Step 4: 运行确认通过**

```bash
cd D:/Project/powergateway/backend
mvn test -Dtest=UXC00FieldFormulaEntityTest
```

Expected: 测试绿。

补充回归：`mvn test` 全量跑一次，确认 326 个存量测试无退化（新增 update_time 字段对现有 Mapper 无破坏，H2 建表由 init.sql 覆盖新列）。

- [ ] **Step 5: 提交**

```bash
git add backend/src/main/java/com/powergateway/model/FieldFormula.java \
        backend/src/main/resources/db/migration-field-formula.sql \
        backend/src/test/java/com/powergateway/UXC00FieldFormulaEntityTest.java
git commit -m "feat(field-formula): 补 FieldFormula.updateTime 字段 + 幂等迁移脚本（FN-03/CHG-017）"
```

---

## Task 4: FN-03 后端 DTO（FormulaJson / FormulaOperand / DTO 三兄弟）

**Files:**
- Create: `backend/src/main/java/com/powergateway/model/dto/FormulaOperand.java`
- Create: `backend/src/main/java/com/powergateway/model/dto/FormulaJson.java`
- Create: `backend/src/main/java/com/powergateway/model/dto/FieldFormulaDto.java`
- Create: `backend/src/main/java/com/powergateway/model/dto/FormulaSaveRequest.java`
- Create: `backend/src/main/java/com/powergateway/model/dto/FormulaValidateRequest.java`
- Create: `backend/src/main/java/com/powergateway/model/dto/FormulaValidateResult.java`

**Interfaces:**
- Consumes: Jackson `ObjectMapper` 默认序列化；Lombok `@Data`
- Produces:
  - `FormulaOperand`：POJO，字段 `kind`（枚举字符串 COLUMN/REQUEST_PARAM/CONST/ARITH/FORMULA_REF）、`tableName`、`columnName`、`paramKey`、`constType`、`constValue`（Object）、`expr`（嵌套 ArithExpr）、`formulaId`
  - `FormulaJson`：POJO，字段 `version`（Integer）、`type`（CONDITION_GROUP/ARITH_EXPR）、`logic`（AND/OR/NOT）、`children`（List<Node>）、`interfaceRefs`（List<InterfaceRef>）
  - `FieldFormulaDto`：Controller/前端交互 VO，含 `id/name/scene/dbConnectionId/formulaJson(String)/remark/creator/createTime/updateTime`
  - `FormulaSaveRequest`：写请求，`id?/name/scene/dbConnectionId/formulaJson(String)/remark`
  - `FormulaValidateRequest`：`dbConnectionId/formulaJson(String)`
  - `FormulaValidateResult`：`ok(boolean)/errors(List<ErrorItem{path,message}>)`

- [ ] **Step 1: 写 DTO 序列化/反序列化测试（Red）**

创建 `backend/src/test/java/com/powergateway/UXC01FormulaDtoTest.java`：

```java
package com.powergateway;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.powergateway.model.dto.FormulaJson;
import com.powergateway.model.dto.FormulaSaveRequest;
import com.powergateway.model.dto.FormulaValidateResult;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.*;

@ActiveProfiles("test")
class UXC01FormulaDtoTest {

    private final ObjectMapper om = new ObjectMapper();

    @Test
    void 反序列化条件组_含嵌套算术表达式() throws Exception {
        String json = "{\"version\":1,\"type\":\"CONDITION_GROUP\",\"logic\":\"AND\",\"children\":["
                + "{\"nodeType\":\"CONDITION\",\"op\":\"GT\","
                + "\"left\":{\"kind\":\"COLUMN\",\"tableName\":\"orders\",\"columnName\":\"amount\"},"
                + "\"right\":{\"kind\":\"CONST\",\"constType\":\"NUMBER\",\"constValue\":100}}"
                + "],\"interfaceRefs\":[]}";
        FormulaJson f = om.readValue(json, FormulaJson.class);
        assertEquals("CONDITION_GROUP", f.getType());
        assertEquals("AND", f.getLogic());
        assertEquals(1, f.getChildren().size());
    }

    @Test
    void FormulaSaveRequest_id可空() throws Exception {
        String json = "{\"name\":\"formula-a\",\"dbConnectionId\":1,\"formulaJson\":\"{}\"}";
        FormulaSaveRequest req = om.readValue(json, FormulaSaveRequest.class);
        assertNull(req.getId());
        assertEquals("formula-a", req.getName());
    }

    @Test
    void FormulaValidateResult_默认okTrue_errors非null() {
        FormulaValidateResult r = new FormulaValidateResult();
        assertTrue(r.isOk());
        assertNotNull(r.getErrors());
    }
}
```

- [ ] **Step 2: 运行确认失败**

```bash
mvn test -Dtest=UXC01FormulaDtoTest
```

Expected: 编译失败（DTO 类不存在）。

- [ ] **Step 3: 最小实现**

3.1 `FormulaOperand.java`：

```java
package com.powergateway.model.dto;

import lombok.Data;

/**
 * 公式操作数（UX-C FN-03）。
 * kind 枚举：COLUMN / REQUEST_PARAM / CONST / ARITH / FORMULA_REF
 * 详细约束见 docs/02-设计/详细设计/2026-07-19-UX-C-field-mapping-formula-design.md §5.2
 */
@Data
public class FormulaOperand {

    /** 操作数种类 */
    private String kind;

    // ─── kind=COLUMN ───
    private String tableName;
    private String columnName;

    // ─── kind=REQUEST_PARAM ───
    private String paramKey;

    // ─── kind=CONST ───
    /** NUMBER / STRING / BOOLEAN / STRING_ARRAY / NUMBER_ARRAY */
    private String constType;
    /** 字面值：单值或数组，Jackson 自动映射 */
    private Object constValue;

    // ─── kind=ARITH ───
    private ArithExpr expr;

    // ─── kind=FORMULA_REF ───
    private Long formulaId;

    @Data
    public static class ArithExpr {
        /** ADD / SUB / MUL / DIV */
        private String op;
        private FormulaOperand left;
        private FormulaOperand right;
    }
}
```

3.2 `FormulaJson.java`：

```java
package com.powergateway.model.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 公式配置 JSON 顶层结构（UX-C FN-03）。
 * 详细 schema：docs/02-设计/详细设计/2026-07-19-UX-C-field-mapping-formula-design.md §5.2
 */
@Data
public class FormulaJson {

    /** 版本号，当前恒为 1 */
    private Integer version = 1;

    /** CONDITION_GROUP / ARITH_EXPR */
    private String type;

    /** 仅 CONDITION_GROUP 使用：AND / OR / NOT */
    private String logic;

    /** 条件组的子节点，元素为 ConditionGroup 或 Condition */
    private List<Node> children = new ArrayList<>();

    /** 接口字段关联 */
    private List<InterfaceRef> interfaceRefs = new ArrayList<>();

    @Data
    public static class Node {
        /** CONDITION_GROUP / CONDITION */
        private String nodeType;

        // 嵌套条件组
        private String logic;
        private List<Node> children;

        // 条件行
        /** EQ / NE / GT / GE / LT / LE / LIKE / IN / BETWEEN / IS_NULL / IS_NOT_NULL */
        private String op;
        private FormulaOperand left;
        private FormulaOperand right;
    }

    @Data
    public static class InterfaceRef {
        private Long interfaceId;
        private String paramKey;
        private String columnHint;
    }
}
```

3.3 `FieldFormulaDto.java`：

```java
package com.powergateway.model.dto;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 字段公式对外传输对象（UX-C FN-03）。
 * formulaJson 以字符串形式回传，前端自行解析成 FormulaJson。
 */
@Data
public class FieldFormulaDto {
    private Long id;
    private String name;
    private String scene;
    private Long dbConnectionId;
    private String formulaJson;
    private String remark;
    private String creator;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
```

3.4 `FormulaSaveRequest.java`：

```java
package com.powergateway.model.dto;

import lombok.Data;

@Data
public class FormulaSaveRequest {
    /** id 为空 = 新增；非空 = 更新 */
    private Long id;
    private String name;
    private String scene;
    private Long dbConnectionId;
    /** 公式配置 JSON 字符串 */
    private String formulaJson;
    private String remark;
}
```

3.5 `FormulaValidateRequest.java`：

```java
package com.powergateway.model.dto;

import lombok.Data;

@Data
public class FormulaValidateRequest {
    private Long dbConnectionId;
    private String formulaJson;
}
```

3.6 `FormulaValidateResult.java`：

```java
package com.powergateway.model.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class FormulaValidateResult {
    private boolean ok = true;
    private List<ErrorItem> errors = new ArrayList<>();

    public void addError(String path, String message) {
        this.ok = false;
        ErrorItem e = new ErrorItem();
        e.setPath(path);
        e.setMessage(message);
        this.errors.add(e);
    }

    @Data
    public static class ErrorItem {
        /** JSON path，例如 children[0].right */
        private String path;
        private String message;
    }
}
```

- [ ] **Step 4: 运行确认通过**

```bash
mvn test -Dtest=UXC01FormulaDtoTest
```

Expected: 3 条测试全绿。

- [ ] **Step 5: 提交**

```bash
git add backend/src/main/java/com/powergateway/model/dto/FormulaOperand.java \
        backend/src/main/java/com/powergateway/model/dto/FormulaJson.java \
        backend/src/main/java/com/powergateway/model/dto/FieldFormulaDto.java \
        backend/src/main/java/com/powergateway/model/dto/FormulaSaveRequest.java \
        backend/src/main/java/com/powergateway/model/dto/FormulaValidateRequest.java \
        backend/src/main/java/com/powergateway/model/dto/FormulaValidateResult.java \
        backend/src/test/java/com/powergateway/UXC01FormulaDtoTest.java
git commit -m "feat(field-formula): 新增 FormulaJson/FormulaOperand/DTO 六个类（FN-03/CHG-017）"
```

---

## Task 5: FN-03 后端 FormulaValidator（校验器，含 TableMetaService 集成）

**Files:**
- Create: `backend/src/main/java/com/powergateway/service/FormulaValidator.java`
- Create: `backend/src/test/java/com/powergateway/UXC02FormulaValidatorTest.java`

**Interfaces:**
- Consumes: `ObjectMapper`、`TableMetaService.getTables(Long dbId)`（返回 `List<TableMeta>`）、`InterfaceConfigMapper.selectById(Long id)`（校验 `interfaceRefs.interfaceId` 存在）
- Produces: `public FormulaValidateResult validate(FormulaValidateRequest req)` — 一次性收集所有错误（不短路），错误项含 JSON path 与人话消息

- [ ] **Step 1: 写校验器单元测试（Red）**

创建 `backend/src/test/java/com/powergateway/UXC02FormulaValidatorTest.java`：

```java
package com.powergateway;

import com.powergateway.dao.DbConnectionMapper;
import com.powergateway.dao.InterfaceConfigMapper;
import com.powergateway.model.DbConnection;
import com.powergateway.model.InterfaceConfig;
import com.powergateway.model.dto.ColumnMeta;
import com.powergateway.model.dto.FormulaValidateRequest;
import com.powergateway.model.dto.FormulaValidateResult;
import com.powergateway.model.dto.TableMeta;
import com.powergateway.service.FormulaValidator;
import com.powergateway.service.TableMetaService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
class UXC02FormulaValidatorTest {

    @Autowired FormulaValidator validator;

    @MockBean TableMetaService tableMetaService;
    @MockBean InterfaceConfigMapper interfaceConfigMapper;

    @BeforeEach
    void setUp() {
        ColumnMeta amount = new ColumnMeta(); amount.setName("amount");
        ColumnMeta status = new ColumnMeta(); status.setName("status");
        TableMeta orders = new TableMeta();
        orders.setTableName("orders");
        orders.setColumns(Arrays.asList(amount, status));
        Mockito.when(tableMetaService.getTables(1L)).thenReturn(Collections.singletonList(orders));
    }

    private FormulaValidateRequest req(String json) {
        FormulaValidateRequest r = new FormulaValidateRequest();
        r.setDbConnectionId(1L);
        r.setFormulaJson(json);
        return r;
    }

    @Test
    void 合法条件组_ok为true() {
        String json = "{\"type\":\"CONDITION_GROUP\",\"logic\":\"AND\",\"children\":["
                + "{\"nodeType\":\"CONDITION\",\"op\":\"GT\","
                + "\"left\":{\"kind\":\"COLUMN\",\"tableName\":\"orders\",\"columnName\":\"amount\"},"
                + "\"right\":{\"kind\":\"CONST\",\"constType\":\"NUMBER\",\"constValue\":100}}"
                + "]}";
        FormulaValidateResult r = validator.validate(req(json));
        assertTrue(r.isOk(), "错误：" + r.getErrors());
    }

    @Test
    void formulaJson为空_报错() {
        FormulaValidateResult r = validator.validate(req(null));
        assertFalse(r.isOk());
        assertEquals(1, r.getErrors().size());
    }

    @Test
    void JSON结构非法_报错() {
        FormulaValidateResult r = validator.validate(req("{broken"));
        assertFalse(r.isOk());
    }

    @Test
    void 根节点type非法_报错() {
        String json = "{\"type\":\"WHATEVER\",\"children\":[]}";
        FormulaValidateResult r = validator.validate(req(json));
        assertFalse(r.isOk());
    }

    @Test
    void CONDITION_GROUP_子节点为空_报错() {
        String json = "{\"type\":\"CONDITION_GROUP\",\"logic\":\"AND\",\"children\":[]}";
        FormulaValidateResult r = validator.validate(req(json));
        assertFalse(r.isOk());
    }

    @Test
    void NOT逻辑只能一个子节点_两个报错() {
        String json = "{\"type\":\"CONDITION_GROUP\",\"logic\":\"NOT\",\"children\":["
                + "{\"nodeType\":\"CONDITION\",\"op\":\"IS_NULL\","
                + "\"left\":{\"kind\":\"COLUMN\",\"tableName\":\"orders\",\"columnName\":\"amount\"}},"
                + "{\"nodeType\":\"CONDITION\",\"op\":\"IS_NULL\","
                + "\"left\":{\"kind\":\"COLUMN\",\"tableName\":\"orders\",\"columnName\":\"status\"}}"
                + "]}";
        FormulaValidateResult r = validator.validate(req(json));
        assertFalse(r.isOk());
    }

    @Test
    void IN操作符右操作数不是数组_报错() {
        String json = "{\"type\":\"CONDITION_GROUP\",\"logic\":\"AND\",\"children\":["
                + "{\"nodeType\":\"CONDITION\",\"op\":\"IN\","
                + "\"left\":{\"kind\":\"COLUMN\",\"tableName\":\"orders\",\"columnName\":\"status\"},"
                + "\"right\":{\"kind\":\"CONST\",\"constType\":\"STRING\",\"constValue\":\"PAID\"}}"
                + "]}";
        FormulaValidateResult r = validator.validate(req(json));
        assertFalse(r.isOk());
    }

    @Test
    void BETWEEN右操作数长度非2_报错() {
        String json = "{\"type\":\"CONDITION_GROUP\",\"logic\":\"AND\",\"children\":["
                + "{\"nodeType\":\"CONDITION\",\"op\":\"BETWEEN\","
                + "\"left\":{\"kind\":\"COLUMN\",\"tableName\":\"orders\",\"columnName\":\"amount\"},"
                + "\"right\":{\"kind\":\"CONST\",\"constType\":\"NUMBER_ARRAY\",\"constValue\":[1,2,3]}}"
                + "]}";
        FormulaValidateResult r = validator.validate(req(json));
        assertFalse(r.isOk());
    }

    @Test
    void COLUMN引用不存在的列_报错含path() {
        String json = "{\"type\":\"CONDITION_GROUP\",\"logic\":\"AND\",\"children\":["
                + "{\"nodeType\":\"CONDITION\",\"op\":\"EQ\","
                + "\"left\":{\"kind\":\"COLUMN\",\"tableName\":\"orders\",\"columnName\":\"nonexistent\"},"
                + "\"right\":{\"kind\":\"CONST\",\"constType\":\"NUMBER\",\"constValue\":1}}"
                + "]}";
        FormulaValidateResult r = validator.validate(req(json));
        assertFalse(r.isOk());
        assertTrue(r.getErrors().get(0).getPath().contains("children[0]"),
                "错误 path 应含 children[0]，实际=" + r.getErrors().get(0).getPath());
    }

    @Test
    void 多个错误一次性返回_不短路() {
        String json = "{\"type\":\"CONDITION_GROUP\",\"logic\":\"AND\",\"children\":["
                + "{\"nodeType\":\"CONDITION\",\"op\":\"IN\","
                + "\"left\":{\"kind\":\"COLUMN\",\"tableName\":\"orders\",\"columnName\":\"nonexistent\"},"
                + "\"right\":{\"kind\":\"CONST\",\"constType\":\"STRING\",\"constValue\":\"x\"}}"
                + "]}";
        FormulaValidateResult r = validator.validate(req(json));
        assertFalse(r.isOk());
        assertTrue(r.getErrors().size() >= 2, "应至少收集 2 个错误（IN 类型错误 + 列不存在），实际=" + r.getErrors());
    }

    @Test
    void interfaceRef接口不存在_报错() {
        Mockito.when(interfaceConfigMapper.selectById(999L)).thenReturn(null);
        String json = "{\"type\":\"CONDITION_GROUP\",\"logic\":\"AND\",\"children\":["
                + "{\"nodeType\":\"CONDITION\",\"op\":\"IS_NULL\","
                + "\"left\":{\"kind\":\"COLUMN\",\"tableName\":\"orders\",\"columnName\":\"amount\"}}"
                + "],\"interfaceRefs\":[{\"interfaceId\":999,\"paramKey\":\"x\"}]}";
        FormulaValidateResult r = validator.validate(req(json));
        assertFalse(r.isOk());
    }
}
```

- [ ] **Step 2: 运行确认失败**

```bash
mvn test -Dtest=UXC02FormulaValidatorTest
```

Expected: 编译失败（`FormulaValidator` 不存在）。

- [ ] **Step 3: 最小实现**

创建 `backend/src/main/java/com/powergateway/service/FormulaValidator.java`：

```java
package com.powergateway.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.powergateway.dao.InterfaceConfigMapper;
import com.powergateway.model.InterfaceConfig;
import com.powergateway.model.dto.*;
import com.powergateway.model.dto.FormulaValidateResult.ErrorItem;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * 字段公式静态校验器（UX-C FN-03）。
 * 一次性收集所有错误，不短路。
 * 校验规则详见 docs/02-设计/详细设计/2026-07-19-UX-C-field-mapping-formula-design.md §5.2
 */
@Slf4j
@Service
public class FormulaValidator {

    @Autowired private ObjectMapper objectMapper;
    @Autowired private TableMetaService tableMetaService;
    @Autowired private InterfaceConfigMapper interfaceConfigMapper;

    private static final Set<String> ROOT_TYPES = new HashSet<>(Arrays.asList("CONDITION_GROUP", "ARITH_EXPR"));
    private static final Set<String> LOGIC_OPS = new HashSet<>(Arrays.asList("AND", "OR", "NOT"));
    private static final Set<String> COND_OPS = new HashSet<>(Arrays.asList(
            "EQ", "NE", "GT", "GE", "LT", "LE", "LIKE", "IN", "BETWEEN", "IS_NULL", "IS_NOT_NULL"));
    private static final Set<String> ARITH_OPS = new HashSet<>(Arrays.asList("ADD", "SUB", "MUL", "DIV"));
    private static final Set<String> CONST_TYPES = new HashSet<>(Arrays.asList(
            "NUMBER", "STRING", "BOOLEAN", "STRING_ARRAY", "NUMBER_ARRAY"));

    public FormulaValidateResult validate(FormulaValidateRequest req) {
        FormulaValidateResult result = new FormulaValidateResult();

        if (req == null || req.getFormulaJson() == null || req.getFormulaJson().trim().isEmpty()) {
            result.addError("$", "公式配置不能为空");
            return result;
        }

        FormulaJson root;
        try {
            root = objectMapper.readValue(req.getFormulaJson(), FormulaJson.class);
        } catch (Exception e) {
            result.addError("$", "公式 JSON 结构非法：" + e.getMessage());
            return result;
        }

        if (root.getType() == null || !ROOT_TYPES.contains(root.getType())) {
            result.addError("$.type", "根节点 type 必须为 CONDITION_GROUP 或 ARITH_EXPR");
            return result;
        }

        // 收集所有 COLUMN 引用，供后续元数据校验
        List<ColumnRefWithPath> columnRefs = new ArrayList<>();

        if ("CONDITION_GROUP".equals(root.getType())) {
            validateGroup(root.getLogic(), root.getChildren(), "$", result, columnRefs);
        }
        // ARITH_EXPR 的根节点校验（本单元最小实现只处理 CONDITION_GROUP，
        // ARITH_EXPR 根类型透传由 FormulaJson.children 结构表达，
        // 若未来需要独立算术根节点可扩展 root 层新字段）

        // 元数据校验：按 dbConnectionId + tableName 分组
        if (req.getDbConnectionId() != null && !columnRefs.isEmpty()) {
            try {
                List<TableMeta> tables = tableMetaService.getTables(req.getDbConnectionId());
                Map<String, Set<String>> tableColumnMap = new HashMap<>();
                for (TableMeta t : tables) {
                    Set<String> cols = new HashSet<>();
                    if (t.getColumns() != null) {
                        for (ColumnMeta c : t.getColumns()) cols.add(c.getName().toLowerCase());
                    }
                    tableColumnMap.put(t.getTableName().toLowerCase(), cols);
                }
                for (ColumnRefWithPath ref : columnRefs) {
                    String tn = ref.tableName == null ? "" : ref.tableName.toLowerCase();
                    String cn = ref.columnName == null ? "" : ref.columnName.toLowerCase();
                    Set<String> cols = tableColumnMap.get(tn);
                    if (cols == null) {
                        result.addError(ref.path,
                                "列引用的表 '" + ref.tableName + "' 在数据库连接中不存在");
                    } else if (!cols.contains(cn)) {
                        result.addError(ref.path,
                                "列 '" + ref.columnName + "' 在表 '" + ref.tableName + "' 中不存在");
                    }
                }
            } catch (Exception e) {
                log.warn("[UX-C] 元数据校验失败: {}", e.getMessage());
                result.addError("$", "无法查询数据库元数据：" + e.getMessage());
            }
        }

        // 接口引用校验
        if (root.getInterfaceRefs() != null) {
            for (int i = 0; i < root.getInterfaceRefs().size(); i++) {
                FormulaJson.InterfaceRef ir = root.getInterfaceRefs().get(i);
                if (ir.getInterfaceId() == null) continue;
                InterfaceConfig cfg = interfaceConfigMapper.selectById(ir.getInterfaceId());
                if (cfg == null) {
                    result.addError("$.interfaceRefs[" + i + "].interfaceId",
                            "引用的接口 " + ir.getInterfaceId() + " 不存在或已删除");
                }
            }
        }

        return result;
    }

    private void validateGroup(String logic, List<FormulaJson.Node> children,
                                String path, FormulaValidateResult result,
                                List<ColumnRefWithPath> columnRefs) {
        if (logic == null || !LOGIC_OPS.contains(logic)) {
            result.addError(path + ".logic", "logic 必须为 AND / OR / NOT");
        }
        if (children == null || children.isEmpty()) {
            result.addError(path + ".children", "条件组子节点不能为空");
            return;
        }
        if ("NOT".equals(logic) && children.size() > 1) {
            result.addError(path + ".children", "NOT 逻辑只能包含一个子节点");
        }
        for (int i = 0; i < children.size(); i++) {
            validateNode(children.get(i), path + ".children[" + i + "]", result, columnRefs);
        }
    }

    private void validateNode(FormulaJson.Node node, String path,
                               FormulaValidateResult result,
                               List<ColumnRefWithPath> columnRefs) {
        if (node.getNodeType() == null) {
            result.addError(path + ".nodeType", "节点 nodeType 必填");
            return;
        }
        if ("CONDITION_GROUP".equals(node.getNodeType())) {
            validateGroup(node.getLogic(), node.getChildren(), path, result, columnRefs);
            return;
        }
        if (!"CONDITION".equals(node.getNodeType())) {
            result.addError(path + ".nodeType", "nodeType 必须为 CONDITION_GROUP 或 CONDITION");
            return;
        }
        // CONDITION
        if (node.getOp() == null || !COND_OPS.contains(node.getOp())) {
            result.addError(path + ".op", "op 必须为受支持的比较操作符");
            return;
        }
        // left 必填
        if (node.getLeft() == null) {
            result.addError(path + ".left", "左操作数必填");
        } else {
            validateOperand(node.getLeft(), path + ".left", result, columnRefs);
        }
        // right：IS_NULL / IS_NOT_NULL 不校验；其它必填
        if ("IS_NULL".equals(node.getOp()) || "IS_NOT_NULL".equals(node.getOp())) {
            return;
        }
        if (node.getRight() == null) {
            result.addError(path + ".right", "右操作数必填");
            return;
        }
        validateOperand(node.getRight(), path + ".right", result, columnRefs);

        // IN / BETWEEN 特殊约束
        if ("IN".equals(node.getOp())) {
            if (!"CONST".equals(node.getRight().getKind())
                    || !(node.getRight().getConstValue() instanceof List)
                    || !("STRING_ARRAY".equals(node.getRight().getConstType())
                         || "NUMBER_ARRAY".equals(node.getRight().getConstType()))) {
                result.addError(path + ".right",
                        "IN 操作符的右操作数必须为 STRING_ARRAY 或 NUMBER_ARRAY 常量");
            }
        }
        if ("BETWEEN".equals(node.getOp())) {
            Object cv = node.getRight().getConstValue();
            if (!"NUMBER_ARRAY".equals(node.getRight().getConstType())
                    || !(cv instanceof List)
                    || ((List<?>) cv).size() != 2) {
                result.addError(path + ".right",
                        "BETWEEN 操作符的右操作数必须为长度为 2 的 NUMBER_ARRAY");
            }
        }
    }

    private void validateOperand(FormulaOperand op, String path,
                                  FormulaValidateResult result,
                                  List<ColumnRefWithPath> columnRefs) {
        if (op.getKind() == null) {
            result.addError(path + ".kind", "操作数 kind 必填");
            return;
        }
        switch (op.getKind()) {
            case "COLUMN":
                if (op.getTableName() == null || op.getColumnName() == null) {
                    result.addError(path, "COLUMN 操作数需 tableName + columnName");
                } else {
                    ColumnRefWithPath ref = new ColumnRefWithPath();
                    ref.tableName = op.getTableName();
                    ref.columnName = op.getColumnName();
                    ref.path = path;
                    columnRefs.add(ref);
                }
                break;
            case "REQUEST_PARAM":
                if (op.getParamKey() == null || op.getParamKey().isEmpty()) {
                    result.addError(path + ".paramKey", "REQUEST_PARAM 需 paramKey");
                }
                break;
            case "CONST":
                if (op.getConstType() == null || !CONST_TYPES.contains(op.getConstType())) {
                    result.addError(path + ".constType", "CONST 需合法 constType");
                }
                break;
            case "ARITH":
                if (op.getExpr() == null || op.getExpr().getOp() == null
                        || !ARITH_OPS.contains(op.getExpr().getOp())) {
                    result.addError(path + ".expr", "ARITH 需 op ∈ {ADD,SUB,MUL,DIV}");
                } else {
                    if (op.getExpr().getLeft() != null) {
                        validateOperand(op.getExpr().getLeft(), path + ".expr.left", result, columnRefs);
                    } else {
                        result.addError(path + ".expr.left", "ARITH 左操作数必填");
                    }
                    if (op.getExpr().getRight() != null) {
                        validateOperand(op.getExpr().getRight(), path + ".expr.right", result, columnRefs);
                    } else {
                        result.addError(path + ".expr.right", "ARITH 右操作数必填");
                    }
                }
                break;
            case "FORMULA_REF":
                if (op.getFormulaId() == null) {
                    result.addError(path + ".formulaId", "FORMULA_REF 需 formulaId");
                }
                break;
            default:
                result.addError(path + ".kind",
                        "kind 必须为 COLUMN/REQUEST_PARAM/CONST/ARITH/FORMULA_REF 之一");
        }
    }

    private static class ColumnRefWithPath {
        String tableName;
        String columnName;
        String path;
    }
}
```

- [ ] **Step 4: 运行确认通过**

```bash
mvn test -Dtest=UXC02FormulaValidatorTest
```

Expected: 11 条全绿。

- [ ] **Step 5: 提交**

```bash
git add backend/src/main/java/com/powergateway/service/FormulaValidator.java \
        backend/src/test/java/com/powergateway/UXC02FormulaValidatorTest.java
git commit -m "feat(field-formula): 新增 FormulaValidator 校验器 + 11 项 TDD 测试（FN-03/CHG-017）"
```

---

## Task 6: FN-03 后端 FieldFormulaService（CRUD + duplicate + validate）

**Files:**
- Create: `backend/src/main/java/com/powergateway/service/FieldFormulaService.java`
- Create: `backend/src/test/java/com/powergateway/UXC03FieldFormulaServiceTest.java`

**Interfaces:**
- Consumes: `FieldFormulaMapper`（P0-3 已存在，继承 BaseMapper<FieldFormula>）、`FormulaValidator`（Task 5）、`StpUtil.getLoginIdAsLong()` + `SysUserMapper.selectById()` 获取当前用户名
- Produces:
  - `IPage<FieldFormulaDto> list(String scene, String keyword, int pageNo, int pageSize)`
  - `FieldFormulaDto getById(Long id)`（软删返回 null）
  - `Long save(FormulaSaveRequest req, String creator)`（含名称唯一 + 校验器）
  - `Long duplicate(Long originId, String creator)`
  - `void delete(Long id)`（软删）
  - `FormulaValidateResult validate(FormulaValidateRequest req)`（透传 Validator）

- [ ] **Step 1: 写 Service 层测试（Red）**

创建 `backend/src/test/java/com/powergateway/UXC03FieldFormulaServiceTest.java`：

```java
package com.powergateway;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.powergateway.dao.DbConnectionMapper;
import com.powergateway.dao.FieldFormulaMapper;
import com.powergateway.exception.BusinessException;
import com.powergateway.model.DbConnection;
import com.powergateway.model.FieldFormula;
import com.powergateway.model.dto.*;
import com.powergateway.service.FieldFormulaService;
import com.powergateway.service.TableMetaService;
import com.powergateway.utils.AesUtil;
import org.junit.jupiter.api.*;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class UXC03FieldFormulaServiceTest {

    @Autowired FieldFormulaService service;
    @Autowired FieldFormulaMapper mapper;
    @Autowired DbConnectionMapper dbConnMapper;
    @Autowired AesUtil aesUtil;

    /**
     * MockBean 屏蔽真实业务库连接：Validator 会调用 TableMetaService.getTables，
     * 返回一张含 amount 列的假表，避免测试依赖 JDBC 连接。
     */
    @MockBean TableMetaService tableMetaService;

    private Long dbId;

    @BeforeEach
    void setup() {
        DbConnection conn = new DbConnection();
        conn.setName("uxc-test-db");
        conn.setDbType("MySQL");
        conn.setUrl("jdbc:h2:mem:uxctest;DB_CLOSE_DELAY=-1");
        conn.setUsername("sa");
        conn.setPassword(aesUtil.encrypt(""));
        conn.setEnv("dev");
        dbConnMapper.insert(conn);
        dbId = conn.getId();

        ColumnMeta amount = new ColumnMeta(); amount.setName("amount");
        TableMeta orders = new TableMeta();
        orders.setTableName("orders");
        orders.setColumns(Collections.singletonList(amount));
        Mockito.when(tableMetaService.getTables(dbId))
               .thenReturn(Collections.singletonList(orders));
    }

    private FormulaSaveRequest legalReq(String name) {
        FormulaSaveRequest r = new FormulaSaveRequest();
        r.setName(name);
        r.setScene("测试场景");
        r.setDbConnectionId(dbId);
        r.setFormulaJson("{\"type\":\"CONDITION_GROUP\",\"logic\":\"AND\",\"children\":["
                + "{\"nodeType\":\"CONDITION\",\"op\":\"GT\","
                + "\"left\":{\"kind\":\"COLUMN\",\"tableName\":\"orders\",\"columnName\":\"amount\"},"
                + "\"right\":{\"kind\":\"CONST\",\"constType\":\"NUMBER\",\"constValue\":100}}"
                + "]}");
        r.setRemark("单元测试用");
        return r;
    }

    @Test @Order(1)
    void 保存合法公式_返回正整数id() {
        Long id = service.save(legalReq("f-save-1"), "admin");
        assertNotNull(id);
        assertTrue(id > 0);
    }

    @Test @Order(2)
    void 保存同名公式_抛BusinessException400() {
        service.save(legalReq("f-dup"), "admin");
        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.save(legalReq("f-dup"), "admin"));
        assertEquals(400, ex.getCode());
        assertTrue(ex.getMessage().contains("已存在"));
    }

    @Test @Order(3)
    void 保存空formulaJson_抛BusinessException400() {
        FormulaSaveRequest r = legalReq("f-null-json");
        r.setFormulaJson(null);
        assertThrows(BusinessException.class, () -> service.save(r, "admin"));
    }

    @Test @Order(4)
    void 更新已存在公式_覆盖成功() {
        Long id = service.save(legalReq("f-upd"), "admin");
        FormulaSaveRequest upd = legalReq("f-upd");
        upd.setId(id);
        upd.setRemark("已更新");
        Long r = service.save(upd, "admin");
        assertEquals(id, r);
        FieldFormulaDto dto = service.getById(id);
        assertEquals("已更新", dto.getRemark());
    }

    @Test @Order(5)
    void 复制公式_新记录name带copy后缀_其余字段一致() {
        Long origin = service.save(legalReq("f-orig"), "admin");
        Long copy = service.duplicate(origin, "admin");
        assertNotEquals(origin, copy);
        FieldFormulaDto o = service.getById(origin);
        FieldFormulaDto c = service.getById(copy);
        assertTrue(c.getName().startsWith("f-orig_copy_"));
        assertEquals(o.getFormulaJson(), c.getFormulaJson());
        assertEquals(o.getScene(), c.getScene());
    }

    @Test @Order(6)
    void 软删除公式_后续getById返回null() {
        Long id = service.save(legalReq("f-del"), "admin");
        service.delete(id);
        assertNull(service.getById(id));
    }

    @Test @Order(7)
    void 分页查询按场景过滤() {
        FormulaSaveRequest a = legalReq("f-scene-a"); a.setScene("A");
        FormulaSaveRequest b = legalReq("f-scene-b"); b.setScene("B");
        service.save(a, "admin");
        service.save(b, "admin");
        IPage<FieldFormulaDto> page = service.list("A", null, 1, 20);
        assertTrue(page.getRecords().stream().allMatch(f -> "A".equals(f.getScene())));
    }

    @Test @Order(8)
    void validate透传Validator结果() {
        FormulaValidateRequest req = new FormulaValidateRequest();
        req.setDbConnectionId(dbId);
        req.setFormulaJson("{broken");
        FormulaValidateResult r = service.validate(req);
        assertFalse(r.isOk());
    }
}
```

- [ ] **Step 2: 运行确认失败**

```bash
mvn test -Dtest=UXC03FieldFormulaServiceTest
```

Expected: 编译失败（`FieldFormulaService` 不存在）。

- [ ] **Step 3: 最小实现**

创建 `backend/src/main/java/com/powergateway/service/FieldFormulaService.java`：

```java
package com.powergateway.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.powergateway.dao.FieldFormulaMapper;
import com.powergateway.exception.BusinessException;
import com.powergateway.model.FieldFormula;
import com.powergateway.model.dto.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 字段公式管理 Service（UX-C FN-03）。
 * 全部操作走配置库 master 数据源，无 @DS 注解。
 */
@Slf4j
@Service
public class FieldFormulaService {

    @Autowired private FieldFormulaMapper mapper;
    @Autowired private FormulaValidator validator;

    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    // ─── 查询 ─────────────────────────────────────────

    public IPage<FieldFormulaDto> list(String scene, String keyword, int pageNo, int pageSize) {
        Page<FieldFormula> page = new Page<>(pageNo, pageSize);
        LambdaQueryWrapper<FieldFormula> qw = new LambdaQueryWrapper<>();
        if (scene != null && !scene.isEmpty()) qw.eq(FieldFormula::getScene, scene);
        if (keyword != null && !keyword.isEmpty()) {
            qw.and(w -> w.like(FieldFormula::getName, keyword)
                          .or().like(FieldFormula::getRemark, keyword));
        }
        qw.orderByDesc(FieldFormula::getUpdateTime, FieldFormula::getCreateTime);
        IPage<FieldFormula> raw = mapper.selectPage(page, qw);
        List<FieldFormulaDto> dtos = raw.getRecords().stream()
                .map(this::toDto).collect(Collectors.toList());
        Page<FieldFormulaDto> outPage = new Page<>(raw.getCurrent(), raw.getSize(), raw.getTotal());
        outPage.setRecords(dtos);
        return outPage;
    }

    public FieldFormulaDto getById(Long id) {
        FieldFormula e = mapper.selectById(id);
        return e == null ? null : toDto(e);
    }

    // ─── 写操作 ───────────────────────────────────────

    public Long save(FormulaSaveRequest req, String creator) {
        if (req.getName() == null || req.getName().trim().isEmpty()) {
            throw new BusinessException(400, "公式名称必填");
        }
        if (req.getFormulaJson() == null || req.getFormulaJson().trim().isEmpty()) {
            throw new BusinessException(400, "公式配置不能为空");
        }

        // 校验公式
        FormulaValidateRequest vreq = new FormulaValidateRequest();
        vreq.setDbConnectionId(req.getDbConnectionId());
        vreq.setFormulaJson(req.getFormulaJson());
        FormulaValidateResult vr = validator.validate(vreq);
        if (!vr.isOk()) {
            String first = vr.getErrors().isEmpty() ? "校验失败" : vr.getErrors().get(0).getMessage();
            throw new BusinessException(400, "公式校验失败：" + first);
        }

        // 名称唯一（排除自身；软删除记录不占用名称）
        LambdaQueryWrapper<FieldFormula> qw = new LambdaQueryWrapper<>();
        qw.eq(FieldFormula::getName, req.getName());
        if (req.getId() != null) qw.ne(FieldFormula::getId, req.getId());
        if (mapper.selectCount(qw) > 0) {
            throw new BusinessException(400, "公式名称已存在：" + req.getName());
        }

        if (req.getId() == null) {
            FieldFormula e = new FieldFormula();
            e.setName(req.getName());
            e.setScene(req.getScene());
            e.setDbConnectionId(req.getDbConnectionId());
            e.setFormulaJson(req.getFormulaJson());
            e.setRemark(req.getRemark());
            e.setCreator(creator);
            mapper.insert(e);
            return e.getId();
        } else {
            FieldFormula e = mapper.selectById(req.getId());
            if (e == null) throw new BusinessException(404, "公式不存在：id=" + req.getId());
            e.setName(req.getName());
            e.setScene(req.getScene());
            e.setDbConnectionId(req.getDbConnectionId());
            e.setFormulaJson(req.getFormulaJson());
            e.setRemark(req.getRemark());
            mapper.updateById(e);
            return e.getId();
        }
    }

    public Long duplicate(Long originId, String creator) {
        FieldFormula origin = mapper.selectById(originId);
        if (origin == null) throw new BusinessException(404, "原公式不存在：id=" + originId);
        FormulaSaveRequest r = new FormulaSaveRequest();
        r.setName(origin.getName() + "_copy_" + LocalDateTime.now().format(STAMP));
        r.setScene(origin.getScene());
        r.setDbConnectionId(origin.getDbConnectionId());
        r.setFormulaJson(origin.getFormulaJson());
        r.setRemark(origin.getRemark());
        return save(r, creator);
    }

    public void delete(Long id) {
        FieldFormula e = mapper.selectById(id);
        if (e == null) throw new BusinessException(404, "公式不存在：id=" + id);
        mapper.deleteById(id); // @TableLogic 触发软删
    }

    public FormulaValidateResult validate(FormulaValidateRequest req) {
        return validator.validate(req);
    }

    // ─── 工具 ─────────────────────────────────────────

    private FieldFormulaDto toDto(FieldFormula e) {
        FieldFormulaDto dto = new FieldFormulaDto();
        BeanUtils.copyProperties(e, dto);
        return dto;
    }
}
```

- [ ] **Step 4: 运行确认通过**

```bash
mvn test -Dtest=UXC03FieldFormulaServiceTest
```

Expected: 8 条全绿。

- [ ] **Step 5: 提交**

```bash
git add backend/src/main/java/com/powergateway/service/FieldFormulaService.java \
        backend/src/test/java/com/powergateway/UXC03FieldFormulaServiceTest.java
git commit -m "feat(field-formula): 新增 FieldFormulaService CRUD/duplicate/validate + 8 项 TDD 测试（FN-03/CHG-017）"
```

---

## Task 7: FN-03 后端 FieldFormulaController（6 个端点 + MockMvc 测试）

**Files:**
- Create: `backend/src/main/java/com/powergateway/controller/FieldFormulaController.java`
- Create: `backend/src/test/java/com/powergateway/UXC04FieldFormulaControllerTest.java`

**Interfaces:**
- Consumes: `FieldFormulaService`（Task 6）、Sa-Token `StpUtil.checkLogin()` + `StpUtil.getLoginIdAsLong()`、`SysUserMapper.selectById()`（取 username 作为 creator）
- Produces: 6 个 REST 端点，参见 spec §5.4；`@SysLogRecord` 三处写操作；`@Tag("字段公式管理")` Swagger 分组；`DELETE` 端点内做 admin 角色检查（当前项目 UserService.delete 是硬编码逻辑，非 `@SaCheckRole`，本单元保持一致，在 Controller 层判断 `SysUser.role`）

- [ ] **Step 1: 写 Controller 测试（Red）**

创建 `backend/src/test/java/com/powergateway/UXC04FieldFormulaControllerTest.java`：

```java
package com.powergateway;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jayway.jsonpath.JsonPath;
import com.powergateway.dao.DbConnectionMapper;
import com.powergateway.dao.FieldFormulaMapper;
import com.powergateway.model.DbConnection;
import com.powergateway.model.dto.ColumnMeta;
import com.powergateway.model.dto.TableMeta;
import com.powergateway.service.TableMetaService;
import com.powergateway.utils.AesUtil;
import org.junit.jupiter.api.*;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class UXC04FieldFormulaControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper om;
    @Autowired DbConnectionMapper dbConnMapper;
    @Autowired FieldFormulaMapper mapper;
    @Autowired AesUtil aesUtil;

    @MockBean TableMetaService tableMetaService;

    private String adminToken;
    private Long dbId;

    @BeforeAll
    void setup() throws Exception {
        DbConnection conn = new DbConnection();
        conn.setName("uxc-ctrl-db");
        conn.setDbType("MySQL");
        conn.setUrl("jdbc:h2:mem:uxcctrl;DB_CLOSE_DELAY=-1");
        conn.setUsername("sa");
        conn.setPassword(aesUtil.encrypt(""));
        conn.setEnv("dev");
        dbConnMapper.insert(conn);
        dbId = conn.getId();

        ColumnMeta amount = new ColumnMeta(); amount.setName("amount");
        TableMeta orders = new TableMeta();
        orders.setTableName("orders");
        orders.setColumns(Collections.singletonList(amount));
        Mockito.when(tableMetaService.getTables(dbId))
               .thenReturn(Collections.singletonList(orders));

        MvcResult loginResult = mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"admin\",\"password\":\"Admin@123\"}"))
                .andReturn();
        adminToken = JsonPath.read(loginResult.getResponse().getContentAsString(), "$.data");
    }

    private String legalJson(String name) {
        return "{\"name\":\"" + name + "\",\"scene\":\"测试\",\"dbConnectionId\":" + dbId
                + ",\"formulaJson\":\"{\\\"type\\\":\\\"CONDITION_GROUP\\\",\\\"logic\\\":\\\"AND\\\",\\\"children\\\":["
                + "{\\\"nodeType\\\":\\\"CONDITION\\\",\\\"op\\\":\\\"GT\\\","
                + "\\\"left\\\":{\\\"kind\\\":\\\"COLUMN\\\",\\\"tableName\\\":\\\"orders\\\",\\\"columnName\\\":\\\"amount\\\"},"
                + "\\\"right\\\":{\\\"kind\\\":\\\"CONST\\\",\\\"constType\\\":\\\"NUMBER\\\",\\\"constValue\\\":100}}"
                + "]}\"}";
    }

    @Test @Order(1)
    void GET_list_分页参数默认值() throws Exception {
        mockMvc.perform(get("/api/field-formula/list")
                .header("satoken", adminToken))
                .andExpect(jsonPath("$.code").value(200));
    }

    @Test @Order(2)
    void POST_save_合法_返回id() throws Exception {
        MvcResult r = mockMvc.perform(post("/api/field-formula/save")
                .header("satoken", adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(legalJson("ctrl-1")))
                .andExpect(jsonPath("$.code").value(200))
                .andReturn();
        Number id = JsonPath.read(r.getResponse().getContentAsString(), "$.data");
        assertTrue(id.longValue() > 0);
    }

    @Test @Order(3)
    void GET_byId_软删后返回null() throws Exception {
        MvcResult saved = mockMvc.perform(post("/api/field-formula/save")
                .header("satoken", adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(legalJson("ctrl-del")))
                .andReturn();
        Number id = JsonPath.read(saved.getResponse().getContentAsString(), "$.data");

        mockMvc.perform(delete("/api/field-formula/" + id.longValue())
                .header("satoken", adminToken))
                .andExpect(jsonPath("$.code").value(200));

        MvcResult r = mockMvc.perform(get("/api/field-formula/" + id.longValue())
                .header("satoken", adminToken))
                .andReturn();
        // 软删后 data 应为 null
        assertNull(JsonPath.read(r.getResponse().getContentAsString(), "$.data"));
    }

    @Test @Order(4)
    void POST_duplicate_返回新id() throws Exception {
        MvcResult saved = mockMvc.perform(post("/api/field-formula/save")
                .header("satoken", adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(legalJson("ctrl-dup-src")))
                .andReturn();
        Number id = JsonPath.read(saved.getResponse().getContentAsString(), "$.data");

        MvcResult dup = mockMvc.perform(post("/api/field-formula/" + id.longValue() + "/duplicate")
                .header("satoken", adminToken))
                .andExpect(jsonPath("$.code").value(200))
                .andReturn();
        Number newId = JsonPath.read(dup.getResponse().getContentAsString(), "$.data");
        assertNotEquals(id.longValue(), newId.longValue());
    }

    @Test @Order(5)
    void POST_validate_校验失败返回错误列表() throws Exception {
        String body = "{\"dbConnectionId\":" + dbId + ",\"formulaJson\":\"{broken\"}";
        MvcResult r = mockMvc.perform(post("/api/field-formula/validate")
                .header("satoken", adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
                .andExpect(jsonPath("$.code").value(200))
                .andReturn();
        Boolean ok = JsonPath.read(r.getResponse().getContentAsString(), "$.data.ok");
        assertFalse(ok);
    }

    @Test @Order(6)
    void POST_save_未登录_返回401或非200() throws Exception {
        mockMvc.perform(post("/api/field-formula/save")
                .contentType(MediaType.APPLICATION_JSON)
                .content(legalJson("no-token")))
                .andExpect(result -> {
                    int status = result.getResponse().getStatus();
                    int code = JsonPath.read(result.getResponse().getContentAsString(), "$.code");
                    assertTrue(status == 401 || code != 200, "未登录必须被拒");
                });
    }
}
```

- [ ] **Step 2: 运行确认失败**

```bash
mvn test -Dtest=UXC04FieldFormulaControllerTest
```

Expected: 编译失败（Controller 不存在）。

- [ ] **Step 3: 最小实现**

创建 `backend/src/main/java/com/powergateway/controller/FieldFormulaController.java`：

```java
package com.powergateway.controller;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.powergateway.aop.SysLogRecord;
import com.powergateway.common.Result;
import com.powergateway.dao.SysUserMapper;
import com.powergateway.exception.BusinessException;
import com.powergateway.model.SysUser;
import com.powergateway.model.dto.FieldFormulaDto;
import com.powergateway.model.dto.FormulaSaveRequest;
import com.powergateway.model.dto.FormulaValidateRequest;
import com.powergateway.model.dto.FormulaValidateResult;
import com.powergateway.service.FieldFormulaService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

/**
 * 字段公式管理 REST 端点（UX-C FN-03）。
 * 6 个端点：list / getById / save / duplicate / delete / validate
 * 全部要求登录；delete 要求 admin 角色。
 */
@RestController
@RequestMapping("/api/field-formula")
@Tag(name = "字段公式管理", description = "常用字段公式 CRUD 与校验（UX-C FN-03）")
public class FieldFormulaController {

    @Autowired private FieldFormulaService service;
    @Autowired private SysUserMapper sysUserMapper;

    @GetMapping("/list")
    @Operation(summary = "分页查询公式列表")
    public Result<IPage<FieldFormulaDto>> list(
            @RequestParam(required = false) String scene,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "1") int pageNo,
            @RequestParam(defaultValue = "20") int pageSize) {
        StpUtil.checkLogin();
        return Result.success(service.list(scene, keyword, pageNo, pageSize));
    }

    @GetMapping("/{id}")
    @Operation(summary = "查询公式详情，软删返回 null")
    public Result<FieldFormulaDto> getById(@PathVariable Long id) {
        StpUtil.checkLogin();
        return Result.success(service.getById(id));
    }

    @SysLogRecord(module = "字段公式管理", action = "保存公式")
    @PostMapping("/save")
    @Operation(summary = "新增或更新公式（id 空=新增，非空=更新）")
    public Result<Long> save(@RequestBody FormulaSaveRequest req) {
        StpUtil.checkLogin();
        return Result.success(service.save(req, currentUsername()));
    }

    @SysLogRecord(module = "字段公式管理", action = "复制公式")
    @PostMapping("/{id}/duplicate")
    @Operation(summary = "复制公式为新记录")
    public Result<Long> duplicate(@PathVariable Long id) {
        StpUtil.checkLogin();
        return Result.success(service.duplicate(id, currentUsername()));
    }

    @SysLogRecord(module = "字段公式管理", action = "删除公式")
    @DeleteMapping("/{id}")
    @Operation(summary = "软删除公式（仅 admin）")
    public Result<Void> delete(@PathVariable Long id) {
        StpUtil.checkLogin();
        requireAdmin();
        service.delete(id);
        return Result.success();
    }

    @PostMapping("/validate")
    @Operation(summary = "独立校验端点（不保存）")
    public Result<FormulaValidateResult> validate(@RequestBody FormulaValidateRequest req) {
        StpUtil.checkLogin();
        return Result.success(service.validate(req));
    }

    // ─── 辅助 ─────────────────────────────────

    private String currentUsername() {
        SysUser u = sysUserMapper.selectById(StpUtil.getLoginIdAsLong());
        return u == null ? "unknown" : u.getUsername();
    }

    private void requireAdmin() {
        SysUser u = sysUserMapper.selectById(StpUtil.getLoginIdAsLong());
        if (u == null || !"admin".equals(u.getRole())) {
            throw new BusinessException(403, "仅管理员可执行此操作");
        }
    }
}
```

- [ ] **Step 4: 运行确认通过**

```bash
mvn test -Dtest=UXC04FieldFormulaControllerTest
mvn test  # 全量回归，确认 326 存量 + 新增测试均绿
```

Expected: 6 条 Controller 测试 + 前面 Task 1~6 的所有测试全绿；全量测试不退化。

- [ ] **Step 5: 提交**

```bash
git add backend/src/main/java/com/powergateway/controller/FieldFormulaController.java \
        backend/src/test/java/com/powergateway/UXC04FieldFormulaControllerTest.java
git commit -m "feat(field-formula): 新增 FieldFormulaController 6 个端点 + MockMvc 测试（FN-03/CHG-017）"
```

---

## Task 8: FN-03 前端 API 封装 fieldFormula.js

**Files:**
- Create: `frontend/src/api/fieldFormula.js`

**Interfaces:**
- Consumes: `@/api/request`（axios 实例，自动解包 Result 与注入 satoken）
- Produces: 6 个导出函数：`listFormulas(params)` / `getFormula(id)` / `saveFormula(data)` / `duplicateFormula(id)` / `deleteFormula(id)` / `validateFormula(data)`，均返回 Promise<data>

- [ ] **Step 1: 写"文件存在且导出 6 个函数"约定（Red）**

由于前端无 Vitest，采用启动 dev server 后手工 `import` 验证的方式。先跑 `npm run build`，观察是否有引用 `@/api/fieldFormula` 的位置（当前应无）：

```bash
cd D:/Project/powergateway/frontend
npm run build
```

Expected: 构建通过（文件尚未创建，因此也未被引用）。此步骤记录 Red 基线。

- [ ] **Step 2: 运行确认失败**

在浏览器 console 尝试：`import('/src/api/fieldFormula.js')` — Expected: 404。

- [ ] **Step 3: 最小实现**

创建 `frontend/src/api/fieldFormula.js`：

```js
import request from '@/api/request'

// 分页列表；params: { scene, keyword, pageNo, pageSize }
export const listFormulas = (params) =>
  request.get('/field-formula/list', { params })

// 详情；返回 FieldFormulaDto | null
export const getFormula = (id) =>
  request.get(`/field-formula/${id}`)

// 新增或更新；data: FormulaSaveRequest
export const saveFormula = (data) =>
  request.post('/field-formula/save', data)

// 复制
export const duplicateFormula = (id) =>
  request.post(`/field-formula/${id}/duplicate`)

// 软删除（仅 admin）
export const deleteFormula = (id) =>
  request.delete(`/field-formula/${id}`)

// 独立校验；data: FormulaValidateRequest
export const validateFormula = (data) =>
  request.post('/field-formula/validate', data)
```

- [ ] **Step 4: 运行确认通过**

```bash
npm run build
```

Expected: 构建通过。浏览器 console `import('/src/api/fieldFormula.js').then(m => console.log(Object.keys(m)))` 应输出 `['listFormulas','getFormula','saveFormula','duplicateFormula','deleteFormula','validateFormula']`。

- [ ] **Step 5: 提交**

```bash
git add frontend/src/api/fieldFormula.js
git commit -m "feat(field-formula): 新增 fieldFormula.js API 封装（FN-03/CHG-017）"
```

---

## Task 9: FN-03 前端 OperandInput.vue（操作数编辑器，5 种 kind）

**Files:**
- Create: `frontend/src/components/formula/OperandInput.vue`

**Interfaces:**
- Consumes: prop `modelValue`（FormulaOperand 对象）、`tableColumnsMap`（`{ tableName: [{name,type,...}] }`）、`recursive`（Boolean，限制 ARITH 深度，默认 true）
- Produces: `emit('update:modelValue', newVal)` — 支持 v-model 语法糖；kind 变更时清空其它字段避免脏数据

- [ ] **Step 1: 写手工用例（Red）**

前端无自动化测试基础设施，本任务的 Red/Green 通过 spec §6.3 UI2 中"能选择 5 种 kind、切换后旧字段清空"作为验收判据。

创建计划：先建空组件，浏览器打开 FieldFormula 页面确认组件不存在或渲染失败（后续 Task 10/11 挂载后可见）。

Expected: 组件不存在时，FormulaBuilder（Task 10）无法引用，构建报错。

- [ ] **Step 2: 运行确认失败**

暂缓，等待 Task 10/11 完成后一起统一手工验收。此处仅创建文件。

- [ ] **Step 3: 最小实现**

创建 `frontend/src/components/formula/OperandInput.vue`：

```vue
<template>
  <div class="operand-input">
    <el-select v-model="local.kind" size="small" style="width:110px" @change="onKindChange">
      <el-option label="列" value="COLUMN" />
      <el-option label="请求参数" value="REQUEST_PARAM" />
      <el-option label="常量" value="CONST" />
      <el-option v-if="recursive" label="算术表达式" value="ARITH" />
      <el-option label="公式引用" value="FORMULA_REF" />
    </el-select>

    <!-- COLUMN -->
    <template v-if="local.kind === 'COLUMN'">
      <el-select v-model="local.tableName" size="small" style="width:140px; margin-left:6px"
                 placeholder="表" filterable @change="onFieldChange">
        <el-option v-for="t in tableNames" :key="t" :label="t" :value="t" />
      </el-select>
      <el-select v-model="local.columnName" size="small" style="width:160px; margin-left:6px"
                 placeholder="列" filterable @change="onFieldChange">
        <el-option v-for="c in columnsOf(local.tableName)" :key="c.name"
                   :label="c.name" :value="c.name" />
      </el-select>
    </template>

    <!-- REQUEST_PARAM -->
    <template v-else-if="local.kind === 'REQUEST_PARAM'">
      <el-input v-model="local.paramKey" size="small" placeholder="请求参数名"
                style="width:200px; margin-left:6px" @input="onFieldChange" />
    </template>

    <!-- CONST -->
    <template v-else-if="local.kind === 'CONST'">
      <el-select v-model="local.constType" size="small" style="width:130px; margin-left:6px"
                 placeholder="类型" @change="onConstTypeChange">
        <el-option label="数字" value="NUMBER" />
        <el-option label="字符串" value="STRING" />
        <el-option label="布尔" value="BOOLEAN" />
        <el-option label="字符串数组" value="STRING_ARRAY" />
        <el-option label="数字数组" value="NUMBER_ARRAY" />
      </el-select>
      <el-input v-if="isScalarConst" v-model="local.constValue" size="small"
                :placeholder="constPlaceholder" style="width:180px; margin-left:6px"
                @input="onFieldChange" />
      <el-input v-else v-model="arrayText" size="small"
                placeholder="逗号分隔，如 A,B,C 或 1,2"
                style="width:220px; margin-left:6px" @input="onArrayInput" />
    </template>

    <!-- ARITH -->
    <template v-else-if="local.kind === 'ARITH' && recursive">
      <div class="arith-box">
        <OperandInput
          :model-value="local.expr && local.expr.left ? local.expr.left : { kind: 'CONST', constType: 'NUMBER', constValue: 0 }"
          :table-columns-map="tableColumnsMap"
          :recursive="false"
          @update:model-value="setArithLeft" />
        <el-select v-model="arithOp" size="small" style="width:70px; margin:0 4px" @change="onFieldChange">
          <el-option label="+" value="ADD" />
          <el-option label="-" value="SUB" />
          <el-option label="×" value="MUL" />
          <el-option label="÷" value="DIV" />
        </el-select>
        <OperandInput
          :model-value="local.expr && local.expr.right ? local.expr.right : { kind: 'CONST', constType: 'NUMBER', constValue: 0 }"
          :table-columns-map="tableColumnsMap"
          :recursive="false"
          @update:model-value="setArithRight" />
      </div>
    </template>

    <!-- FORMULA_REF -->
    <template v-else-if="local.kind === 'FORMULA_REF'">
      <el-input-number v-model="local.formulaId" size="small" :min="1"
                       placeholder="公式 ID" style="width:140px; margin-left:6px"
                       @change="onFieldChange" />
    </template>
  </div>
</template>

<script setup>
import { reactive, computed, watch } from 'vue'

const props = defineProps({
  modelValue: { type: Object, required: true },
  tableColumnsMap: { type: Object, default: () => ({}) },
  recursive: { type: Boolean, default: true }
})
const emit = defineEmits(['update:modelValue'])

// 本地可变副本
const local = reactive({
  kind: 'COLUMN',
  tableName: '',
  columnName: '',
  paramKey: '',
  constType: 'STRING',
  constValue: '',
  expr: null,
  formulaId: null,
  ...props.modelValue
})

// 外部 modelValue 改变时同步（父组件重置场景）
watch(() => props.modelValue, (v) => {
  Object.assign(local, {
    kind: 'COLUMN', tableName: '', columnName: '', paramKey: '',
    constType: 'STRING', constValue: '', expr: null, formulaId: null,
    ...v
  })
}, { deep: false })

const tableNames = computed(() => Object.keys(props.tableColumnsMap || {}))
const columnsOf = (t) => (t && props.tableColumnsMap[t]) || []

const isScalarConst = computed(() =>
  ['NUMBER', 'STRING', 'BOOLEAN'].includes(local.constType)
)
const constPlaceholder = computed(() => {
  if (local.constType === 'NUMBER') return '如 100'
  if (local.constType === 'BOOLEAN') return 'true / false'
  return '字符串值'
})

const arrayText = computed({
  get: () => Array.isArray(local.constValue) ? local.constValue.join(',') : '',
  set: (v) => {
    const parts = String(v).split(',').map(s => s.trim()).filter(Boolean)
    local.constValue = local.constType === 'NUMBER_ARRAY'
      ? parts.map(Number)
      : parts
  }
})

const arithOp = computed({
  get: () => (local.expr && local.expr.op) || 'ADD',
  set: (v) => {
    if (!local.expr) local.expr = { op: v, left: null, right: null }
    else local.expr.op = v
  }
})

function onKindChange() {
  // 切换 kind 时清空互斥字段
  local.tableName = ''; local.columnName = ''; local.paramKey = ''
  local.constValue = ''; local.expr = null; local.formulaId = null
  if (local.kind === 'CONST' && !local.constType) local.constType = 'STRING'
  if (local.kind === 'ARITH') {
    local.expr = { op: 'ADD',
      left:  { kind: 'CONST', constType: 'NUMBER', constValue: 0 },
      right: { kind: 'CONST', constType: 'NUMBER', constValue: 0 } }
  }
  emitUpdate()
}
function onConstTypeChange() {
  local.constValue = ['STRING_ARRAY', 'NUMBER_ARRAY'].includes(local.constType) ? [] : ''
  emitUpdate()
}
function onFieldChange() { emitUpdate() }
function onArrayInput() { emitUpdate() }
function setArithLeft(v)  { if (!local.expr) local.expr = { op: 'ADD' }; local.expr.left = v; emitUpdate() }
function setArithRight(v) { if (!local.expr) local.expr = { op: 'ADD' }; local.expr.right = v; emitUpdate() }

function emitUpdate() {
  emit('update:modelValue', JSON.parse(JSON.stringify(local)))
}
</script>

<style scoped>
.operand-input { display: inline-flex; align-items: center; flex-wrap: wrap; gap: 2px; }
.arith-box { display: inline-flex; align-items: center; padding: 4px 6px; border: 1px dashed #dcdfe6; border-radius: 4px; margin-left: 6px; }
</style>
```

- [ ] **Step 4: 运行确认通过**

```bash
cd D:/Project/powergateway/frontend
npm run build
```

Expected: 构建通过。手工验收留到 Task 11 组件挂载后统一执行。

- [ ] **Step 5: 提交**

```bash
git add frontend/src/components/formula/OperandInput.vue
git commit -m "feat(field-formula): 新增 OperandInput.vue 操作数编辑器（FN-03/CHG-017）"
```

---

## Task 10: FN-03 前端 FormulaBuilder.vue（递归条件组编辑器）

**Files:**
- Create: `frontend/src/components/formula/FormulaBuilder.vue`

**Interfaces:**
- Consumes: prop `modelValue`（FormulaJson 对象）、`tableColumnsMap`；子组件 `OperandInput.vue`
- Produces: `emit('update:modelValue', newFormula)`；支持添加条件行/子组、切换 AND/OR/NOT、删除节点

**约束**：**本组件内部禁止使用 vue-draggable-next**，用普通 `v-for` + 删除/上下移按钮即可（规避 FN-01/02 雷区）。

- [ ] **Step 1: 写手工用例（Red）**

同 Task 9，先建空组件供 Task 11 引用。

Expected: 组件不存在时 FieldFormula.vue 构建报错。

- [ ] **Step 2: 运行确认失败**

暂缓，与 Task 11 一起验收。

- [ ] **Step 3: 最小实现**

创建 `frontend/src/components/formula/FormulaBuilder.vue`：

```vue
<template>
  <div class="formula-builder">
    <div class="group-header" :style="{ paddingLeft: depth * 16 + 'px' }">
      <el-tag :type="tagType" size="small">{{ groupLogic }}</el-tag>
      <el-select v-model="localGroup.logic" size="small" style="width:80px; margin-left:6px" @change="emitUpdate">
        <el-option label="AND" value="AND" />
        <el-option label="OR" value="OR" />
        <el-option label="NOT" value="NOT" />
      </el-select>
      <el-button link size="small" type="primary" @click="addCondition">+ 条件</el-button>
      <el-button link size="small" type="primary" @click="addSubGroup">+ 子组</el-button>
      <el-button v-if="depth > 0" link size="small" type="danger" @click="$emit('remove')">删除本组</el-button>
    </div>

    <div v-for="(child, idx) in localGroup.children" :key="idx" class="child-row" :style="{ paddingLeft: (depth + 1) * 16 + 'px' }">
      <!-- 嵌套子组 -->
      <FormulaBuilder
        v-if="child.nodeType === 'CONDITION_GROUP'"
        :model-value="{ type: 'CONDITION_GROUP', logic: child.logic, children: child.children, interfaceRefs: [] }"
        :table-columns-map="tableColumnsMap"
        :depth="depth + 1"
        @update:model-value="(v) => onChildGroupUpdate(idx, v)"
        @remove="removeChild(idx)"
      />
      <!-- 条件行 -->
      <div v-else class="cond-row">
        <OperandInput :model-value="child.left || {}" :table-columns-map="tableColumnsMap"
                      @update:model-value="(v) => { child.left = v; emitUpdate() }" />
        <el-select v-model="child.op" size="small" style="width:100px; margin:0 6px" @change="emitUpdate">
          <el-option label="=" value="EQ" />
          <el-option label="≠" value="NE" />
          <el-option label=">" value="GT" />
          <el-option label="≥" value="GE" />
          <el-option label="<" value="LT" />
          <el-option label="≤" value="LE" />
          <el-option label="LIKE" value="LIKE" />
          <el-option label="IN" value="IN" />
          <el-option label="BETWEEN" value="BETWEEN" />
          <el-option label="IS NULL" value="IS_NULL" />
          <el-option label="IS NOT NULL" value="IS_NOT_NULL" />
        </el-select>
        <OperandInput
          v-if="child.op !== 'IS_NULL' && child.op !== 'IS_NOT_NULL'"
          :model-value="child.right || {}"
          :table-columns-map="tableColumnsMap"
          @update:model-value="(v) => { child.right = v; emitUpdate() }" />
        <el-button link size="small" type="danger" style="margin-left:6px" @click="removeChild(idx)">删除</el-button>
      </div>
    </div>
  </div>
</template>

<script setup>
import { reactive, computed, watch } from 'vue'
import OperandInput from './OperandInput.vue'

const props = defineProps({
  modelValue: { type: Object, required: true },
  tableColumnsMap: { type: Object, default: () => ({}) },
  depth: { type: Number, default: 0 }
})
const emit = defineEmits(['update:modelValue', 'remove'])

const localGroup = reactive({
  type: 'CONDITION_GROUP',
  logic: 'AND',
  children: [],
  interfaceRefs: [],
  ...deepClone(props.modelValue)
})

watch(() => props.modelValue, (v) => {
  Object.assign(localGroup, { type: 'CONDITION_GROUP', logic: 'AND', children: [], interfaceRefs: [], ...deepClone(v) })
}, { deep: false })

const groupLogic = computed(() => localGroup.logic || 'AND')
const tagType = computed(() => ({ AND: 'primary', OR: 'success', NOT: 'danger' }[localGroup.logic] || 'info'))

function addCondition() {
  localGroup.children.push({
    nodeType: 'CONDITION',
    op: 'EQ',
    left:  { kind: 'COLUMN', tableName: '', columnName: '' },
    right: { kind: 'CONST', constType: 'STRING', constValue: '' }
  })
  emitUpdate()
}
function addSubGroup() {
  localGroup.children.push({
    nodeType: 'CONDITION_GROUP',
    logic: 'AND',
    children: []
  })
  emitUpdate()
}
function removeChild(idx) {
  localGroup.children.splice(idx, 1)
  emitUpdate()
}
function onChildGroupUpdate(idx, v) {
  localGroup.children[idx] = { nodeType: 'CONDITION_GROUP', logic: v.logic, children: v.children }
  emitUpdate()
}

function emitUpdate() {
  emit('update:modelValue', deepClone(localGroup))
}
function deepClone(o) { return o ? JSON.parse(JSON.stringify(o)) : o }
</script>

<style scoped>
.formula-builder { padding: 4px 0; }
.group-header { display: flex; align-items: center; gap: 6px; padding: 4px 0; }
.child-row { padding: 4px 0; }
.cond-row { display: flex; align-items: center; flex-wrap: wrap; padding: 4px 0; border-left: 2px solid #ebeef5; padding-left: 8px; }
</style>
```

- [ ] **Step 4: 运行确认通过**

```bash
npm run build
```

Expected: 构建通过。

- [ ] **Step 5: 提交**

```bash
git add frontend/src/components/formula/FormulaBuilder.vue
git commit -m "feat(field-formula): 新增 FormulaBuilder.vue 递归条件组编辑器（FN-03/CHG-017）"
```

---

## Task 11: FN-03 前端 FieldFormula.vue 列表页 + 编辑对话框

**Files:**
- Create: `frontend/src/views/interface/FieldFormula.vue`
- Modify: `frontend/src/router/index.js`（第 121~125 行 `/interface/formula` 的 component 从 PlaceholderView 改为新组件）

**Interfaces:**
- Consumes: `@/api/fieldFormula`（Task 8）、`@/api/dbConnection`（`listConnections()`）、`@/api/tableStructure`（`getTableStructure(dbId)`）、`FormulaBuilder.vue`（Task 10）、`@/store/user`（获取 role 用于隐藏 admin-only 按钮）
- Produces: 列表页 `/interface/formula`；新增/编辑/复制/删除/校验预览 UI

- [ ] **Step 1: 写手工用例清单（Red）**

以 spec §6.3 中 UI1~UI7 作为验收判据。首次访问 `/interface/formula` 应看到 PlaceholderView（Red 基线）。

浏览器访问 `http://localhost:5173/interface/formula`：

Expected（Red 状态）：显示"开发中"占位页。

- [ ] **Step 2: 运行确认失败**

按 Step 1 操作，确认现状为占位页。

Expected: 未见列表页 UI。

- [ ] **Step 3: 最小实现**

3.1 创建 `frontend/src/views/interface/FieldFormula.vue`：

```vue
<template>
  <div class="field-formula-page">
    <el-card>
      <template #header>
        <div class="page-header">
          <span class="title">字段公式管理</span>
          <div>
            <el-input v-model="query.scene" placeholder="场景" size="small" style="width:120px" clearable />
            <el-input v-model="query.keyword" placeholder="关键字（名称/备注）" size="small" style="width:200px; margin-left:6px" clearable />
            <el-button size="small" type="primary" @click="reload">查询</el-button>
            <el-button size="small" @click="resetQuery">重置</el-button>
            <el-button size="small" type="success" @click="openCreate">+ 新增公式</el-button>
          </div>
        </div>
      </template>

      <el-table :data="rows" border v-loading="loading" size="small">
        <el-table-column prop="id" label="ID" width="70" />
        <el-table-column prop="name" label="名称" min-width="160" show-overflow-tooltip />
        <el-table-column prop="scene" label="场景" width="140" show-overflow-tooltip />
        <el-table-column label="关联数据库" width="160">
          <template #default="{ row }">{{ dbNameById(row.dbConnectionId) }}</template>
        </el-table-column>
        <el-table-column prop="remark" label="备注" min-width="180" show-overflow-tooltip />
        <el-table-column prop="creator" label="创建人" width="100" />
        <el-table-column prop="createTime" label="创建时间" width="160" />
        <el-table-column label="操作" width="240" fixed="right">
          <template #default="{ row }">
            <el-button link size="small" @click="openEdit(row)">编辑</el-button>
            <el-button link size="small" type="primary" @click="onDuplicate(row)">复制</el-button>
            <el-popconfirm v-if="isAdmin" title="确认删除该公式？" @confirm="onDelete(row)">
              <template #reference>
                <el-button link size="small" type="danger">删除</el-button>
              </template>
            </el-popconfirm>
          </template>
        </el-table-column>
      </el-table>

      <el-pagination
        style="margin-top: 12px; justify-content: flex-end; display:flex"
        v-model:current-page="page.pageNo"
        v-model:page-size="page.pageSize"
        :total="page.total"
        :page-sizes="[10, 20, 50]"
        layout="total, sizes, prev, pager, next, jumper"
        @size-change="reload"
        @current-change="reload" />
    </el-card>

    <!-- 编辑对话框 -->
    <el-dialog v-model="dialogVisible" :title="form.id ? '编辑公式' : '新增公式'" width="900px" destroy-on-close>
      <el-form label-width="110px">
        <el-form-item label="公式名称" required>
          <el-input v-model="form.name" placeholder="全局唯一" />
        </el-form-item>
        <el-form-item label="场景">
          <el-input v-model="form.scene" placeholder="业务场景，如 客户信息" />
        </el-form-item>
        <el-form-item label="关联数据库" required>
          <el-select v-model="form.dbConnectionId" placeholder="选择数据库连接" style="width:100%" @change="loadTables">
            <el-option v-for="db in dbList" :key="db.id" :label="db.name" :value="db.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="form.remark" type="textarea" :rows="2" />
        </el-form-item>
        <el-form-item label="公式配置">
          <FormulaBuilder v-model="formulaObj" :table-columns-map="tableColumnsMap" />
        </el-form-item>
        <el-form-item label="校验结果" v-if="validateResult">
          <el-tag v-if="validateResult.ok" type="success">校验通过</el-tag>
          <div v-else>
            <el-tag type="danger">校验未通过</el-tag>
            <ul class="err-list">
              <li v-for="(e, i) in validateResult.errors" :key="i">
                <code>{{ e.path }}</code> — {{ e.message }}
              </li>
            </ul>
          </div>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="doValidate" :loading="validating">校验预览</el-button>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="doSave" :loading="saving">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted, computed } from 'vue'
import { ElMessage } from 'element-plus'
import { listFormulas, getFormula, saveFormula, duplicateFormula, deleteFormula, validateFormula } from '@/api/fieldFormula'
import { listConnections } from '@/api/dbConnection'
import { getTableStructure } from '@/api/tableStructure'
import FormulaBuilder from '@/components/formula/FormulaBuilder.vue'
import { useUserStore } from '@/store/user'

const userStore = useUserStore()
const isAdmin = computed(() => userStore.role === 'admin')

const loading = ref(false)
const rows = ref([])
const query = reactive({ scene: '', keyword: '' })
const page = reactive({ pageNo: 1, pageSize: 20, total: 0 })

const dbList = ref([])
const dbNameById = (id) => (dbList.value.find(d => d.id === id) || {}).name || '-'

const dialogVisible = ref(false)
const saving = ref(false)
const validating = ref(false)
const validateResult = ref(null)

const form = reactive({ id: null, name: '', scene: '', dbConnectionId: null, remark: '' })
const formulaObj = ref({ type: 'CONDITION_GROUP', logic: 'AND', children: [], interfaceRefs: [] })
const tableColumnsMap = ref({})

async function reload() {
  loading.value = true
  try {
    const res = await listFormulas({
      scene: query.scene || undefined,
      keyword: query.keyword || undefined,
      pageNo: page.pageNo,
      pageSize: page.pageSize
    })
    rows.value = res.records || []
    page.total = res.total || 0
  } finally {
    loading.value = false
  }
}
function resetQuery() { query.scene = ''; query.keyword = ''; page.pageNo = 1; reload() }

async function loadTables(dbId) {
  tableColumnsMap.value = {}
  if (!dbId) return
  try {
    const list = await getTableStructure(dbId)
    ;(list || []).forEach(t => { tableColumnsMap.value[t.tableName] = t.columns || [] })
  } catch {
    ElMessage.error('加载表结构失败')
  }
}

function openCreate() {
  Object.assign(form, { id: null, name: '', scene: '', dbConnectionId: null, remark: '' })
  formulaObj.value = { type: 'CONDITION_GROUP', logic: 'AND', children: [], interfaceRefs: [] }
  validateResult.value = null
  tableColumnsMap.value = {}
  dialogVisible.value = true
}

async function openEdit(row) {
  const detail = await getFormula(row.id)
  if (!detail) { ElMessage.warning('公式已被删除'); reload(); return }
  Object.assign(form, {
    id: detail.id, name: detail.name, scene: detail.scene,
    dbConnectionId: detail.dbConnectionId, remark: detail.remark
  })
  try {
    formulaObj.value = detail.formulaJson ? JSON.parse(detail.formulaJson)
      : { type: 'CONDITION_GROUP', logic: 'AND', children: [], interfaceRefs: [] }
  } catch {
    formulaObj.value = { type: 'CONDITION_GROUP', logic: 'AND', children: [], interfaceRefs: [] }
    ElMessage.warning('公式 JSON 解析失败，已重置')
  }
  validateResult.value = null
  await loadTables(detail.dbConnectionId)
  dialogVisible.value = true
}

async function doValidate() {
  validating.value = true
  try {
    validateResult.value = await validateFormula({
      dbConnectionId: form.dbConnectionId,
      formulaJson: JSON.stringify(formulaObj.value)
    })
  } finally {
    validating.value = false
  }
}

async function doSave() {
  if (!form.name || !form.dbConnectionId) { ElMessage.warning('名称与数据库必填'); return }
  saving.value = true
  try {
    await saveFormula({
      id: form.id,
      name: form.name,
      scene: form.scene,
      dbConnectionId: form.dbConnectionId,
      remark: form.remark,
      formulaJson: JSON.stringify(formulaObj.value)
    })
    ElMessage.success('保存成功')
    dialogVisible.value = false
    reload()
  } finally {
    saving.value = false
  }
}

async function onDuplicate(row) {
  const newId = await duplicateFormula(row.id)
  ElMessage.success(`已复制，新公式 id=${newId}，请修改后保存`)
  reload()
}

async function onDelete(row) {
  await deleteFormula(row.id)
  ElMessage.success('已删除')
  reload()
}

onMounted(async () => {
  try {
    dbList.value = (await listConnections()) || []
  } catch {
    ElMessage.error('加载数据库连接失败')
  }
  reload()
})
</script>

<style scoped>
.field-formula-page { padding: 0; }
.page-header { display:flex; justify-content:space-between; align-items:center; gap: 6px; }
.title { font-size:16px; font-weight:600; }
.err-list { margin: 4px 0 0 16px; color: #f56c6c; font-size: 12px; }
.err-list code { background:#fef0f0; padding: 0 4px; border-radius: 2px; }
</style>
```

3.2 修改 `frontend/src/router/index.js` 第 121~125 行，将：

```js
{
  path: 'interface/formula',
  name: 'FieldFormula',
  component: () => import('@/views/placeholder/PlaceholderView.vue'),
  meta: { title: '字段公式管理' }
},
```

改为：

```js
{
  path: 'interface/formula',
  name: 'FieldFormula',
  component: () => import('@/views/interface/FieldFormula.vue'),
  meta: { title: '字段公式管理' }
},
```

- [ ] **Step 4: 运行确认通过（手工回归 UI1~UI7）**

```bash
cd D:/Project/powergateway/frontend
npm run dev
```

浏览器操作按 spec §6.3 后半段（前端手工用例）：

| 编号 | 步骤 | Expected |
|------|-----|----------|
| FN-03-UI1 | 侧边栏点"字段公式管理" | 进入 `/interface/formula`，显示列表页 |
| FN-03-UI2 | 点"新增公式"，填基础信息、加 2 条件 + 1 子组、点"校验预览" | 显示绿色"校验通过" tag |
| FN-03-UI3 | 故意选不存在的列，点"校验预览" | 红色错误列表，含 "column 'xxx' not found" 类消息 |
| FN-03-UI4 | 保存 → 列表出现新行 | 正常 |
| FN-03-UI5 | 点行"复制" → 提示新 id → 列表出现副本 | 正常 |
| FN-03-UI6 | 点"删除" → el-popconfirm → 消失 | 正常 |
| FN-03-UI7 | 用非 admin（如 readonly）登录 | 列表页无"删除"按钮；curl DELETE 后端返回 403 |

Expected: 7 项全绿；控制台无 Vue warning；网络请求全部走 `/api/field-formula/*`（headers 含 satoken）。

- [ ] **Step 5: 提交**

```bash
git add frontend/src/views/interface/FieldFormula.vue \
        frontend/src/router/index.js
git commit -m "feat(field-formula): 新增 FieldFormula.vue 列表页 + 编辑对话框 + 路由挂载（FN-03/CHG-017）"
```

---

## Task 12: FN-03 前端 FormulaPicker.vue（预留复用组件）

**Files:**
- Create: `frontend/src/components/formula/FormulaPicker.vue`

**Interfaces:**
- Consumes: `listFormulas`（Task 8）
- Produces: v-model 绑定公式 id；`emit('select', fullDto)` 供父组件读完整数据

**说明**：本组件用于未来 M2-3/5/6 集成，本单元只交付组件本身并保证可 import，不接入任何页面。

- [ ] **Step 1: 建立引用验证（Red）**

`npm run build` 当前通过。此步骤记录基线。

Expected: 不存在时无人引用，构建通过。

- [ ] **Step 2: 运行确认失败**

浏览器 console `import('/src/components/formula/FormulaPicker.vue')` — Expected: 404。

- [ ] **Step 3: 最小实现**

创建 `frontend/src/components/formula/FormulaPicker.vue`：

```vue
<template>
  <div class="formula-picker">
    <el-select
      :model-value="modelValue"
      filterable clearable
      :placeholder="placeholder"
      style="width: 100%"
      @update:model-value="onChange"
    >
      <el-option
        v-for="f in list"
        :key="f.id"
        :value="f.id"
        :label="displayLabel(f)"
      />
    </el-select>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { listFormulas } from '@/api/fieldFormula'

const props = defineProps({
  modelValue: { type: [Number, null], default: null },
  placeholder: { type: String, default: '选择已存字段公式' },
  scene: { type: String, default: '' }
})
const emit = defineEmits(['update:modelValue', 'select'])

const list = ref([])

async function reload() {
  const res = await listFormulas({ pageNo: 1, pageSize: 200, scene: props.scene || undefined })
  list.value = res.records || []
}

function displayLabel(f) {
  return f.scene ? `${f.name}（${f.scene}）` : f.name
}

function onChange(id) {
  emit('update:modelValue', id)
  emit('select', list.value.find(x => x.id === id) || null)
}

onMounted(reload)
defineExpose({ reload })
</script>

<style scoped>
.formula-picker { display: inline-block; width: 100%; }
</style>
```

- [ ] **Step 4: 运行确认通过**

```bash
npm run build
```

Expected: 构建通过。浏览器 console `import('/src/components/formula/FormulaPicker.vue')` 能拿到模块（vite HMR 环境下）。

- [ ] **Step 5: 提交**

```bash
git add frontend/src/components/formula/FormulaPicker.vue
git commit -m "feat(field-formula): 新增 FormulaPicker.vue 预留复用组件（FN-03/CHG-017）"
```

---

## Task 13: 端到端冒烟（pg-testkit 或手工）+ 全量回归

**Files:** 无代码变更

**Interfaces:** 使用 pg-testkit `/test/*` 与 backend `/api/*`

- [ ] **Step 1: 后端全量测试**

```bash
cd D:/Project/powergateway/backend
mvn test
```

Expected: 326 存量 + 新增（UXC00 * 1 + UXC01 * 3 + UXC02 * 11 + UXC03 * 8 + UXC04 * 6 = 29）= 355 条测试全绿；无退化。

- [ ] **Step 2: 前端构建 + 手工冒烟**

```bash
cd D:/Project/powergateway/frontend
npm run build
npm run dev
```

浏览器全链路走一遍：

1. 登录 admin → `/convert/field-mapping` → 手动添加 orderId → 左侧出现字段块（FN-01 T1 通过）
2. → `/convert/field-process` → 添加 3 条 TRIM/CASE/SUBSTRING 规则 → 输入 `  hello  ` → 输出 `HELLO` 之类（FN-02 T1~T5 通过）
3. → `/interface/formula` → 新增公式 → 填 orders.amount > 100 → 校验预览 → 保存 → 复制 → 删除（FN-03 UI1~UI6 通过）
4. 打开 network 面板确认所有请求走 `/api/*` 且 headers 含 satoken

Expected: 4 步全通，无 Vue warning，无 500/404/403。

- [ ] **Step 3: pg-testkit 场景冒烟（可选，若 pg-testkit 已启动）**

```bash
# pg-testkit 端口 8081
curl -X POST http://localhost:8081/test/db/query \
  -H "Content-Type: application/json" \
  -d '{"database":"config","sql":"SELECT COUNT(*) c FROM field_formula WHERE deleted=0"}'
```

Expected: 返回 `c` 大于等于 Task 13 Step 2 中创建的公式数。

- [ ] **Step 4: 记录冒烟结论**

在下一 Task 的 CHG-017 条目中，追加"验收：全量后端测试 355/355 绿，前端 4 步冒烟全过"作为收尾数据。

Expected: 结论就绪，进入 Task 14 归档。

- [ ] **Step 5: 无代码变更，不 commit**

冒烟仅用于验收，本 Task 无文件改动。

---

## Task 14: 文档归档（CHG-017 + 问题清单 + 开发计划 + 需求补充）

**Files:**
- Modify: `docs/03-开发/变更记录.md`（追加 CHG-017 完整条目）
- Modify: `docs/03-开发/问题清单.md`（FN-01/02/03 移入已解决）
- Modify: `docs/03-开发/开发计划.md`（阶段六 UX-C 行状态改"已完成"）
- Modify: `docs/01-需求/需求拆分与最小实现方案.md`（追加"字段公式管理"节）

**Interfaces:** 纯 Markdown 编辑

- [ ] **Step 1: 写"归档条目已存在且格式正确"验收清单（Red）**

```bash
grep -n "CHG-017" "D:/Project/powergateway/docs/03-开发/变更记录.md"
```

Expected（Red 状态）：无输出（尚未添加）。

- [ ] **Step 2: 运行确认失败**

```bash
grep -c "CHG-017" "D:/Project/powergateway/docs/03-开发/变更记录.md"
```

Expected: 输出 `0`。

- [ ] **Step 3: 最小实现（追加文档条目）**

3.1 在 `docs/03-开发/变更记录.md` 末尾追加：

```markdown

---

### CHG-017 UX-C 字段映射/加工回滚复发修复 + 字段公式管理落地

- **日期**：2026-07-19
- **影响单元**：UX-C（阶段六），涉及 M1-2、M1-3 前端 draggable 用法修复；P0-3 `field_formula` 表补齐并新建全套 Service/Controller/前端页面
- **变更类型**：Bug 修复（FN-01/02）+ 范围新增（FN-03 字段公式管理）
- **变更前**：
  - `FieldMapping.vue` 与 `FieldProcess.vue` 中 `<draggable>` 使用 `<template #item>`（vue-draggable-next v2.x 忽略此 slot），源字段区与规则行渲染为空 DOM（回滚复发自 CHG-003）
  - `/interface/formula` 挂载 PlaceholderView，无后端 Service/Controller，仅 P0-3 建了 `field_formula` 空表
- **变更后**：
  - `FieldMapping.vue` / `FieldProcess.vue` 两处 draggable 改为 default slot + `v-for`，文件顶部追加 vue-draggable-next 规约注释（防三次回滚）
  - 保留 CHG-005 F-4（fixedValue ?? null）、F-7（catch 弹 warning）、问题4（addRule/runPreview 空值保护）等既有修复
  - 新增 `FormulaOperand` / `FormulaJson` / `FieldFormulaDto` / `FormulaSaveRequest` / `FormulaValidateRequest` / `FormulaValidateResult` 六个 DTO
  - 新增 `FormulaValidator`：静态语义校验器，一次性收集所有错误（含 JSON path），复用 `TableMetaService.getTables` 校验 COLUMN 引用
  - 新增 `FieldFormulaService`：CRUD + duplicate + validate，配置库 master 数据源
  - 新增 `FieldFormulaController`：6 个端点（list/getById/save/duplicate/delete/validate），delete 强制 admin
  - 补 `FieldFormula.updateTime` 字段 + `@TableField(fill=INSERT_UPDATE)`
  - 幂等迁移脚本 `migration-field-formula.sql`：加 update_time 列 + 3 索引 + 表注释
  - 前端新增 `FieldFormula.vue`（列表 + 编辑对话框）、`FormulaBuilder.vue`（递归条件组编辑器）、`OperandInput.vue`（5 种 kind 操作数编辑器）、`FormulaPicker.vue`（预留复用）、`fieldFormula.js`（API 封装）
  - `router/index.js` 中 `/interface/formula` 的 component 由 PlaceholderView 改为 FieldFormula.vue
  - `MenuPermission.java` 无需改（`/interface/formula` 已在 ADMIN_MENUS / USER_MENUS）
- **影响文件**：见本单元任务计划 `docs/03-开发/任务计划/2026-07-19-UX-C-field-mapping-formula.md` 的文件地图
- **需求文档更新**：
  - `docs/01-需求/需求拆分与最小实现方案.md` 追加"字段公式管理"节（覆盖 §3.2.8 需求点，落地 field_formula 表与 6 端点契约）
  - `docs/03-开发/开发计划.md` 阶段六 UX-C 行状态改"已完成"
- **原因**：
  - 2026-07-19 用户巡检发现 FN-01/02（回滚复发自 CHG-003）和 FN-03（PlaceholderView 未落地）
  - CHG-003 修复被后续多次编辑同一文件时回滚，本次通过"default slot 唯一正确写法 + 文件顶部规约注释锁定"双保险
- **设计文档**：`docs/02-设计/详细设计/2026-07-19-UX-C-field-mapping-formula-design.md`
- **验收数据**：后端 `mvn test` 全绿（新增 29 条 + 存量 326 条）；前端 `FN-01-T1~T7` / `FN-02-T1~T6` / `FN-03-UI1~UI7` 手工全过；`vue-draggable-next` 使用规约注释已入库
```

3.2 在 `docs/03-开发/问题清单.md` 中，将 2026-07-19 批次 C 组下的 FN-01/02/03 三行从"待解决"章节移动到"已解决"章节（保留原编号与描述，末尾追加"— CHG-017"）。

3.3 在 `docs/03-开发/开发计划.md` 阶段六表格中，UX-C 行的"状态"列改为"已完成"，"完成日期"列填 `2026-07-19`。

3.4 在 `docs/01-需求/需求拆分与最小实现方案.md` 末尾（或 SYS-5 之后）追加"字段公式管理"章节：

```markdown

## 字段公式管理（UX-C FN-03 · CHG-017）

- **需求出处**：产品需求说明书 §3.2.8 常用字段公式
- **范围（含）**：
  - `field_formula` 配置表：id/name(UNIQUE)/scene/db_connection_id/formula_json/remark/deleted/creator/create_time/update_time + 3 索引
  - `formula_json` 支持 CONDITION_GROUP（AND/OR/NOT 嵌套）+ CONDITION（11 种 op）+ 5 种 operand kind（COLUMN/REQUEST_PARAM/CONST/ARITH/FORMULA_REF）+ interfaceRefs
  - 后端 6 个端点：list / getById / save / duplicate / delete（admin） / validate（不保存）
  - `FormulaValidator`：一次性收集所有错误，复用 `TableMetaService` 校验列引用
  - 前端：列表页 + 编辑对话框（含校验预览）；`FormulaBuilder.vue` + `OperandInput.vue` + `FormulaPicker.vue`（预留复用给 M2-3/5/6）
- **不含**：
  - 公式引擎的 SQL 化执行（把公式注入 WHERE 子句的运行时集成，交由后续 UX-D 或独立单元）
  - Excel 导入导出（P1 延后）
  - 公式版本历史（当前只软删）
- **实现方案**：见详细设计 `docs/02-设计/详细设计/2026-07-19-UX-C-field-mapping-formula-design.md`
- **验收标准**：见任务计划 `docs/03-开发/任务计划/2026-07-19-UX-C-field-mapping-formula.md` Task 4~12 各 Step 4
```

- [ ] **Step 4: 运行确认通过**

```bash
grep -c "CHG-017" "D:/Project/powergateway/docs/03-开发/变更记录.md"
grep -c "字段公式管理" "D:/Project/powergateway/docs/01-需求/需求拆分与最小实现方案.md"
```

Expected: 两个 grep 计数均 ≥ 1。

- [ ] **Step 5: 提交**

```bash
git add docs/03-开发/变更记录.md \
        docs/03-开发/问题清单.md \
        docs/03-开发/开发计划.md \
        docs/01-需求/需求拆分与最小实现方案.md
git commit -m "docs(UX-C): 追加 CHG-017 + 更新问题清单/开发计划/需求拆分（FN-01/02/03 已解决）"
```

---

## 验收总检查（14 个 Task 完成后统一跑）

```bash
# 1. 后端全量测试
cd D:/Project/powergateway/backend && mvn test
# Expected: 355/355 绿（新增 29）

# 2. 前端构建
cd D:/Project/powergateway/frontend && npm run build
# Expected: 构建成功，无 Vue warning

# 3. 前端手工冒烟（14.Step 2 中的 4 步链路）
# Expected: FN-01/02/03 全部人工用例过

# 4. 文档校验
grep -c "CHG-017" D:/Project/powergateway/docs/03-开发/变更记录.md   # ≥ 1
grep -l "已解决" D:/Project/powergateway/docs/03-开发/问题清单.md   # 存在
```

## 提交时间线（14 个 commit）

1. `fix(FieldMapping): ...（FN-01）` — Task 1
2. `fix(FieldProcess): ...（FN-02）` — Task 2
3. `feat(field-formula): 补 FieldFormula.updateTime + 迁移脚本` — Task 3
4. `feat(field-formula): 新增 FormulaJson/Operand/DTO 六个类` — Task 4
5. `feat(field-formula): 新增 FormulaValidator + 11 项 TDD 测试` — Task 5
6. `feat(field-formula): 新增 FieldFormulaService + 8 项 TDD 测试` — Task 6
7. `feat(field-formula): 新增 FieldFormulaController 6 端点 + MockMvc 测试` — Task 7
8. `feat(field-formula): 新增 fieldFormula.js API 封装` — Task 8
9. `feat(field-formula): 新增 OperandInput.vue` — Task 9
10. `feat(field-formula): 新增 FormulaBuilder.vue` — Task 10
11. `feat(field-formula): 新增 FieldFormula.vue 列表页 + 路由挂载` — Task 11
12. `feat(field-formula): 新增 FormulaPicker.vue 预留复用` — Task 12
13. （Task 13 冒烟无 commit）
14. `docs(UX-C): 追加 CHG-017 + 更新问题清单/开发计划/需求拆分` — Task 14
