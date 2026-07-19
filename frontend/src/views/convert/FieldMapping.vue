<template>
  <div class="field-mapping-page">
    <el-card class="page-card">
      <template #header>
        <div class="card-header">
          <span class="card-title">字段映射配置</span>
          <el-tooltip content="拖拽源字段到映射表中建立映射关系，或手动选择源字段 / 填入固定值" placement="right">
            <el-icon class="help-icon"><QuestionFilled /></el-icon>
          </el-tooltip>
        </div>
      </template>

      <!-- 模板基本信息 -->
      <el-form :model="form" label-width="90px" class="template-form">
        <el-row :gutter="16">
          <el-col :span="8">
            <el-form-item label="模板名称">
              <el-input v-model="form.name" placeholder="请输入模板名称" clearable />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="源格式">
              <el-select v-model="form.srcFormat" placeholder="选择源格式" style="width:100%">
                <el-option v-for="f in FORMAT_OPTIONS" :key="f.value" :label="f.label" :value="f.value" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="目标格式">
              <el-select v-model="form.targetFormat" placeholder="选择目标格式" style="width:100%">
                <el-option v-for="f in FORMAT_OPTIONS" :key="f.value" :label="f.label" :value="f.value" />
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>
      </el-form>

      <!-- 主体：左侧源字段 + 右侧映射配置 -->
      <el-row :gutter="16" class="mapping-body">

        <!-- ── 左侧：源字段面板 ── -->
        <el-col :span="7">
          <el-card shadow="never" class="field-panel">
            <template #header>
              <div class="panel-header">
                <span>源字段</span>
                <div class="panel-actions">
                  <el-button size="small" @click="showParseDialog = true">解析报文</el-button>
                  <el-button size="small" type="primary" @click="addSrcField">手动添加</el-button>
                </div>
              </div>
            </template>

            <draggable
              v-model="srcFields"
              :group="{ name: 'srcGroup', pull: 'clone', put: false }"
              :sort="false"
              item-key="id"
              class="src-field-list"
            >
              <template #item="{ element }">
                <div class="field-tag">
                  <el-icon class="drag-icon"><Grid /></el-icon>
                  <span class="field-name">{{ element.name }}</span>
                  <el-button
                    link size="small" type="danger"
                    class="remove-btn"
                    @click.stop="removeSrcField(element)"
                  ><el-icon><Close /></el-icon></el-button>
                </div>
              </template>
            </draggable>

            <el-empty v-if="srcFields.length === 0" description="暂无源字段" :image-size="60" />
          </el-card>
        </el-col>

        <!-- ── 右侧：映射规则配置 ── -->
        <el-col :span="17">
          <el-card shadow="never" class="field-panel">
            <template #header>
              <div class="panel-header">
                <span>字段映射规则（可将左侧源字段拖入此区域）</span>
                <div class="panel-actions">
                  <el-button size="small" @click="autoMatch">
                    <el-icon><MagicStick /></el-icon> 自动匹配
                  </el-button>
                  <el-button size="small" type="primary" @click="addMappingRow">
                    <el-icon><Plus /></el-icon> 添加行
                  </el-button>
                </div>
              </div>
            </template>

            <!-- 表头 -->
            <div class="mapping-table-header">
              <span class="col-drag"></span>
              <span class="col-target">目标字段名</span>
              <span class="col-src">来源（源字段）</span>
              <span class="col-fixed">固定值</span>
              <span class="col-op">操作</span>
            </div>

            <!-- 映射规则列表（可排序 + 接收拖入） -->
            <draggable
              v-model="mappingRules"
              :group="{ name: 'srcGroup', pull: false, put: true }"
              item-key="id"
              handle=".row-drag-handle"
              @add="onDragAdd"
              class="mapping-rule-list"
            >
              <template #item="{ element, index }">
                <div class="mapping-row">
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
              </template>
            </draggable>

            <el-empty
              v-if="mappingRules.length === 0"
              description="暂无映射规则，从左侧拖入源字段或点击「添加行」"
              :image-size="60"
            />
          </el-card>
        </el-col>
      </el-row>

      <!-- 操作栏 -->
      <div class="action-bar">
        <el-button type="primary" :loading="saving" @click="handleSave">
          <el-icon><Check /></el-icon> 保存模板
        </el-button>
        <el-button @click="handlePreview" :disabled="!savedTemplateId">
          <el-icon><View /></el-icon> 映射预览
        </el-button>
        <span v-if="savedTemplateId" class="save-tip">
          已保存（ID: {{ savedTemplateId }}，版本: {{ savedVersion }}）
        </span>
      </div>
    </el-card>

    <!-- ── 手动添加字段弹窗 ── -->
    <el-dialog v-model="showAddFieldDialog" title="手动添加源字段" width="400px" @open="newFieldName = ''">
      <el-form label-width="80px" @submit.prevent="confirmAddField">
        <el-form-item label="字段名">
          <el-input
            v-model="newFieldName"
            placeholder="请输入字段名，如 orderId"
            clearable
            autofocus
            @keyup.enter="confirmAddField"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showAddFieldDialog = false">取消</el-button>
        <el-button type="primary" @click="confirmAddField">确定添加</el-button>
      </template>
    </el-dialog>

    <!-- ── 解析报文弹窗 ── -->
    <el-dialog v-model="showParseDialog" title="解析报文提取源字段" width="560px">
      <el-form label-width="80px">
        <el-form-item label="报文格式">
          <el-select v-model="parseFormat" placeholder="选择格式" style="width:100%">
            <el-option v-for="f in FORMAT_OPTIONS" :key="f.value" :label="f.label" :value="f.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="报文内容">
          <el-input
            v-model="parseRawMessage"
            type="textarea"
            :rows="6"
            placeholder="粘贴测试报文"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showParseDialog = false">取消</el-button>
        <el-button type="primary" :loading="parsing" @click="parseMessage2Fields">解析并填充</el-button>
      </template>
    </el-dialog>

    <!-- ── 预览结果弹窗 ── -->
    <el-dialog v-model="showPreviewDialog" title="映射预览结果" width="640px">
      <el-form label-width="80px" class="preview-form">
        <el-form-item label="测试报文">
          <el-input v-model="previewMessage" type="textarea" :rows="4" placeholder="粘贴测试报文" />
        </el-form-item>
        <el-form-item label="报文格式">
          <el-select v-model="previewFormat" style="width:200px">
            <el-option v-for="f in FORMAT_OPTIONS" :key="f.value" :label="f.label" :value="f.value" />
          </el-select>
        </el-form-item>
        <el-form-item label=" ">
          <el-button type="primary" :loading="previewing" @click="doPreview">执行预览</el-button>
        </el-form-item>
        <el-form-item v-if="previewResult !== null" label="映射结果">
          <el-input
            :model-value="JSON.stringify(previewResult, null, 2)"
            type="textarea"
            :rows="8"
            readonly
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showPreviewDialog = false">关闭</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import { VueDraggableNext as draggable } from 'vue-draggable-next'
import {
  QuestionFilled, Grid, Close, Plus, Delete,
  MagicStick, Rank, Check, View
} from '@element-plus/icons-vue'
import { saveTemplate, getTemplateById, previewMapping } from '@/api/template'
import { parseMessage as parseMessageApi } from '@/api/convert'

// ───────── 常量 ─────────
const FORMAT_OPTIONS = [
  { label: 'JSON',     value: 'JSON'      },
  { label: 'XML',      value: 'XML'       },
  { label: 'CSV',      value: 'CSV'       },
  { label: 'FormData', value: 'FORM_DATA' }
]

// ───────── 模板基本信息 ─────────
const form = ref({ name: '', srcFormat: 'JSON', targetFormat: 'JSON' })

// ───────── 源字段列表 ─────────
const srcFields = ref([])
let srcFieldIdCounter = 0

const showAddFieldDialog = ref(false)
const newFieldName = ref('')

function addSrcField() {
  showAddFieldDialog.value = true
}

function confirmAddField() {
  const name = newFieldName.value.trim()
  if (!name) {
    ElMessage.warning('请输入字段名')
    return
  }
  if (srcFields.value.some(f => f.name === name)) {
    ElMessage.warning('字段名已存在')
    return
  }
  srcFields.value.push({ id: ++srcFieldIdCounter, name })
  showAddFieldDialog.value = false
}

function removeSrcField(item) {
  srcFields.value = srcFields.value.filter(f => f.id !== item.id)
}

// ───────── 映射规则列表 ─────────
const mappingRules = ref([])
let ruleIdCounter = 0

function addMappingRow() {
  mappingRules.value.push({
    id: ++ruleIdCounter,
    targetField: '',
    srcField: null,
    useFixed: false,
    fixedValue: null
  })
}

function removeMappingRow(index) {
  mappingRules.value.splice(index, 1)
}

function onUseFixedChange(rule) {
  if (rule.useFixed) {
    rule.srcField = null
  } else {
    rule.fixedValue = null
  }
}

/**
 * 当源字段从左侧拖入映射列表时触发：
 * vue-draggable-next 的 clone 模式会把源字段对象直接插入 mappingRules，
 * 需要将其转换为标准映射规则格式。
 */
function onDragAdd(evt) {
  const idx = evt.newIndex
  const item = mappingRules.value[idx]
  // 如果是从源字段拖入的（有 name 属性，没有 targetField 属性）
  if (item && item.name !== undefined && item.targetField === undefined) {
    mappingRules.value.splice(idx, 1, {
      id: ++ruleIdCounter,
      targetField: item.name,
      srcField: item.name,
      useFixed: false,
      fixedValue: null
    })
  }
}

// ───────── 自动匹配（同名字段） ─────────
function autoMatch() {
  const srcNames = new Set(srcFields.value.map(f => f.name))
  // 已有规则中，目标字段名与某源字段同名且未选来源时，自动填充
  mappingRules.value.forEach(rule => {
    if (!rule.useFixed && !rule.srcField && rule.targetField && srcNames.has(rule.targetField)) {
      rule.srcField = rule.targetField
    }
  })
  // 已有规则的目标字段集合
  const existingTargets = new Set(mappingRules.value.map(r => r.targetField))
  // 对尚无规则的同名源字段，自动新增映射行
  srcFields.value.forEach(sf => {
    if (!existingTargets.has(sf.name)) {
      mappingRules.value.push({
        id: ++ruleIdCounter,
        targetField: sf.name,
        srcField: sf.name,
        useFixed: false,
        fixedValue: null
      })
    }
  })
  ElMessage.success('自动匹配完成')
}

// ───────── 解析报文提取源字段 ─────────
const showParseDialog = ref(false)
const parseFormat = ref('JSON')
const parseRawMessage = ref('')
const parsing = ref(false)

/**
 * 递归展平后端返回的嵌套 Map/List，提取所有叶节点路径。
 * - 嵌套对象：parent.child
 * - 嵌套列表：取第一个元素展开，路径格式 parent[0].child
 *   若列表为空，则只记录列表自身路径
 */
function extractFieldPaths(obj, prefix = '') {
  const paths = []
  if (obj === null || obj === undefined) {
    if (prefix) paths.push(prefix)
    return paths
  }
  if (Array.isArray(obj)) {
    if (obj.length === 0) {
      if (prefix) paths.push(prefix)
    } else {
      const firstPath = prefix ? `${prefix}[0]` : '[0]'
      const sub = extractFieldPaths(obj[0], firstPath)
      paths.push(...sub)
    }
    return paths
  }
  if (typeof obj === 'object') {
    for (const [key, value] of Object.entries(obj)) {
      const fullKey = prefix ? `${prefix}.${key}` : key
      if (value !== null && typeof value === 'object') {
        paths.push(...extractFieldPaths(value, fullKey))
      } else {
        paths.push(fullKey)
      }
    }
    return paths
  }
  if (prefix) paths.push(prefix)
  return paths
}

async function parseMessage2Fields() {
  const msg = parseRawMessage.value.trim()
  if (!msg) {
    ElMessage.warning('请先粘贴报文内容')
    return
  }
  parsing.value = true
  try {
    const fieldMap = await parseMessageApi({ message: msg, format: parseFormat.value })
    const fields = extractFieldPaths(fieldMap)
    const existingNames = new Set(srcFields.value.map(f => f.name))
    let added = 0
    fields.forEach(name => {
      if (name && !existingNames.has(name)) {
        srcFields.value.push({ id: ++srcFieldIdCounter, name })
        existingNames.add(name)
        added++
      }
    })
    showParseDialog.value = false
    parseRawMessage.value = ''
    ElMessage.success(`解析完成，新增 ${added} 个源字段`)
  } catch {
    // 错误由 request.js 拦截器统一展示
  } finally {
    parsing.value = false
  }
}

// ───────── 保存 ─────────
const saving = ref(false)
const savedTemplateId = ref(null)
const savedVersion = ref(null)

async function handleSave() {
  if (!form.value.name) {
    ElMessage.warning('请填写模板名称')
    return
  }
  const rules = mappingRules.value
    .filter(r => r.targetField && r.targetField.trim())
    .map(r => ({
      srcField: r.useFixed ? null : (r.srcField || null),
      targetField: r.targetField.trim(),
      fixedValue: r.useFixed ? (r.fixedValue || '') : null
    }))

  saving.value = true
  try {
    const newId = await saveTemplate({
      id: savedTemplateId.value,
      name: form.value.name,
      srcFormat: form.value.srcFormat,
      targetFormat: form.value.targetFormat,
      mappingRules: rules
    })
    savedTemplateId.value = newId
    savedVersion.value = (savedVersion.value || 0) + 1
    ElMessage.success('模板保存成功')
    // 回显服务端版本号
    await loadTemplate(newId)
  } finally {
    saving.value = false
  }
}

// ───────── 加载已有模板 ─────────
const route = useRoute()

onMounted(async () => {
  const tid = route.query.templateId
  if (tid) await loadTemplate(Number(tid))
})

async function loadTemplate(id) {
  try {
    const tpl = await getTemplateById(id)
    form.value.name = tpl.name
    form.value.srcFormat = tpl.srcFormat
    form.value.targetFormat = tpl.targetFormat
    savedTemplateId.value = tpl.id
    savedVersion.value = tpl.version

    let rawRules = []
    if (tpl.mappingRule) {
      try {
        rawRules = JSON.parse(tpl.mappingRule)
      } catch (_) {
        ElMessage.warning('模板映射规则解析失败，已清空规则，请重新配置')
      }
    }
    mappingRules.value = rawRules.map(r => ({
      id: ++ruleIdCounter,
      targetField: r.targetField || '',
      srcField: (r.fixedValue !== null && r.fixedValue !== undefined) ? null : (r.srcField || null),
      useFixed: r.fixedValue !== null && r.fixedValue !== undefined,
      fixedValue: r.fixedValue ?? null
    }))
    // 从映射规则中恢复源字段列表（去重）
    const srcSet = new Set()
    rawRules.forEach(r => { if (r.srcField) srcSet.add(r.srcField) })
    srcFields.value = Array.from(srcSet).map(name => ({ id: ++srcFieldIdCounter, name }))
  } catch (_) {
    ElMessage.error('加载模板失败')
  }
}

// ───────── 预览 ─────────
const showPreviewDialog = ref(false)
const previewMessage = ref('')
const previewFormat = ref('JSON')
const previewResult = ref(null)
const previewing = ref(false)

function handlePreview() {
  previewFormat.value = form.value.srcFormat
  previewResult.value = null
  showPreviewDialog.value = true
}

async function doPreview() {
  if (!previewMessage.value.trim()) {
    ElMessage.warning('请填写测试报文')
    return
  }
  previewing.value = true
  try {
    previewResult.value = await previewMapping(savedTemplateId.value, {
      message: previewMessage.value,
      format: previewFormat.value
    })
  } finally {
    previewing.value = false
  }
}
</script>

<style scoped>
.field-mapping-page { padding: 0; }
.page-card { margin: 0; }

.card-header { display: flex; align-items: center; gap: 8px; }
.card-title { font-size: 16px; font-weight: 600; }
.help-icon { color: var(--pg-text-secondary); cursor: help; }

.template-form { margin-bottom: 16px; }
.mapping-body { margin-top: 4px; }

.field-panel { height: 100%; min-height: 360px; }
.panel-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.panel-actions { display: flex; gap: 6px; }

/* ── 源字段列表 ── */
.src-field-list { min-height: 200px; padding: 4px 0; }
.field-tag {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 7px 10px;
  margin-bottom: 6px;
  background: var(--pg-track-bg);
  border: 1px solid var(--pg-line-strong);
  border-radius: 4px;
  cursor: grab;
  user-select: none;
  transition: background 0.15s;
}
.field-tag:hover { background: var(--pg-primary-soft); border-color: var(--pg-primary); }
.field-tag:active { cursor: grabbing; }
.drag-icon { color: var(--pg-text-placeholder); flex-shrink: 0; }
.field-name { flex: 1; font-size: 13px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.remove-btn { flex-shrink: 0; }

/* ── 映射规则表 ── */
.mapping-table-header,
.mapping-row {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 5px 4px;
  border-bottom: 1px solid var(--pg-line);
}
.mapping-table-header {
  font-size: 12px;
  color: var(--pg-text-secondary);
  font-weight: 600;
  border-bottom: 2px solid var(--pg-line-strong);
  margin-bottom: 2px;
}
.mapping-row:hover { background: var(--pg-hover-surface); }
.mapping-rule-list { min-height: 160px; }

.col-drag   { width: 22px; flex-shrink: 0; text-align: center; }
.col-target { flex: 3; min-width: 0; }
.col-src    { flex: 3; min-width: 0; }
.col-fixed  { flex: 4; min-width: 0; display: flex; align-items: center; gap: 6px; flex-wrap: wrap; }
.col-op     { width: 40px; flex-shrink: 0; text-align: center; }

.row-drag-handle { cursor: grab; color: var(--pg-text-placeholder); }
.row-drag-handle:active { cursor: grabbing; }
.fixed-input { flex: 1; min-width: 80px; }

/* ── 操作栏 ── */
.action-bar {
  margin-top: 20px;
  display: flex;
  align-items: center;
  gap: 12px;
}
.save-tip { color: var(--pg-success); font-size: 13px; }

/* ── 预览弹窗 ── */
.preview-form { padding-top: 8px; }
</style>
